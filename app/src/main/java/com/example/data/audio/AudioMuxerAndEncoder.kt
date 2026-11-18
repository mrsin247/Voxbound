package com.example.data.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import com.example.data.model.AudiobookFormat
import com.example.data.model.AudiobookSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PcmData(
    val samples: ByteArray,
    val sampleRate: Int,
    val channels: Int
)

object AudioMuxerAndEncoder {

    fun extractPcmFromWav(wavFile: File): PcmData {
        val bytes = wavFile.readBytes()
        return extractPcmFromWavBytes(bytes)
    }

    fun extractPcmFromWavBytes(bytes: ByteArray): PcmData {
        if (bytes.size < 44 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF") {
            return PcmData(bytes, 16000, 1)
        }
        var offset = 12
        var sampleRate = 16000
        var channels = 1
        val pcmOut = ByteArrayOutputStream()

        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int.coerceAtLeast(0)
            val dataStart = offset + 8
            val dataEnd = (dataStart + chunkSize).coerceAtMost(bytes.size)

            when (chunkId) {
                "fmt " -> {
                    if (dataStart + 8 <= bytes.size) {
                        val buf = ByteBuffer.wrap(bytes, dataStart, (dataEnd - dataStart).coerceAtLeast(8))
                            .order(ByteOrder.LITTLE_ENDIAN)
                        buf.short // audioFormat
                        channels = buf.short.toInt().coerceAtLeast(1)
                        sampleRate = buf.int.coerceAtLeast(8000)
                    }
                }
                "data" -> {
                    if (dataEnd > dataStart) {
                        pcmOut.write(bytes, dataStart, dataEnd - dataStart)
                    }
                }
            }
            offset = dataStart + chunkSize + (chunkSize % 2)
        }
        return PcmData(
            samples = pcmOut.toByteArray(),
            sampleRate = sampleRate,
            channels = channels
        )
    }

    fun pcmDurationMs(byteCount: Int, sampleRate: Int, channels: Int, bitsPerSample: Int = 16): Long {
        val bytesPerSecond = sampleRate.toLong() * channels.toLong() * (bitsPerSample / 8L)
        if (bytesPerSecond <= 0L) return 0L
        return (byteCount.toLong() * 1000L) / bytesPerSecond
    }

    suspend fun writeWavFromPcm(
        pcmChunks: List<ByteArray>,
        sampleRate: Int,
        channels: Int,
        outputFile: File
    ): Long = withContext(Dispatchers.IO) {
        val totalPcmBytes = pcmChunks.sumOf { it.size }
        FileOutputStream(outputFile).use { fos ->
            fos.write(buildWavHeader(totalPcmBytes, sampleRate, channels, 16))
            for (chunk in pcmChunks) {
                fos.write(chunk)
            }
        }
        pcmDurationMs(totalPcmBytes, sampleRate, channels)
    }

    suspend fun writeMp3FromChunks(
        mp3Chunks: List<ByteArray>,
        outputFile: File
    ): List<Long> = withContext(Dispatchers.IO) {
        val durations = mutableListOf<Long>()
        val tempFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}_chunk_probe.mp3")
        FileOutputStream(outputFile).use { fos ->
            mp3Chunks.forEachIndexed { index, rawBytes ->
                // Measure exact duration of this chunk
                tempFile.writeBytes(rawBytes)
                val chunkDuration = measureAudioFileDurationMs(tempFile)
                durations.add(chunkDuration)

                val stripped = if (index == 0) rawBytes else stripId3v2Header(rawBytes)
                fos.write(stripped)
            }
        }
        tempFile.delete()
        durations
    }

    suspend fun writeOggFromChunks(
        oggChunks: List<ByteArray>,
        outputFile: File
    ): List<Long> = withContext(Dispatchers.IO) {
        val durations = mutableListOf<Long>()
        val tempFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}_ogg_probe.ogg")
        FileOutputStream(outputFile).use { fos ->
            oggChunks.forEach { bytes ->
                tempFile.writeBytes(bytes)
                durations.add(measureAudioFileDurationMs(tempFile))
                fos.write(bytes)
            }
        }
        tempFile.delete()
        durations
    }

    suspend fun encodePcmToM4aOrM4b(
        pcmChunks: List<ByteArray>,
        sampleRate: Int,
        channels: Int,
        outputFile: File
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val totalPcmBytes = pcmChunks.sumOf { it.size }
            val totalDurationMs = pcmDurationMs(totalPcmBytes, sampleRate, channels)
            val mime = MediaFormat.MIMETYPE_AUDIO_AAC
            val format = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, if (sampleRate >= 22050) 96_000 else 64_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            val codec = MediaCodec.createEncoderByType(mime)
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerStarted = false
            var trackIndex = -1

            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var chunkIdx = 0
                var chunkOffset = 0
                var inputDone = false
                var outputDone = false
                var totalFramesSent = 0L
                val bytesPerFrame = channels * 2

                while (!outputDone) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            inBuf?.clear()

                            var written = 0
                            val capacity = inBuf?.remaining()?.coerceAtMost(8192) ?: 0
                            while (written < capacity && chunkIdx < pcmChunks.size) {
                                val current = pcmChunks[chunkIdx]
                                val avail = current.size - chunkOffset
                                if (avail <= 0) {
                                    chunkIdx++
                                    chunkOffset = 0
                                    continue
                                }
                                val toCopy = minOf(avail, capacity - written)
                                inBuf?.put(current, chunkOffset, toCopy)
                                chunkOffset += toCopy
                                written += toCopy
                                if (chunkOffset >= current.size) {
                                    chunkIdx++
                                    chunkOffset = 0
                                }
                            }

                            val ptsUs = (totalFramesSent * 1_000_000L) / sampleRate
                            if (written == 0) {
                                codec.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    ptsUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                totalFramesSent += (written / bytesPerFrame)
                                codec.queueInputBuffer(inIdx, 0, written, ptsUs, 0)
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIdx >= 0 -> {
                            val encoded = codec.getOutputBuffer(outIdx)
                            if (encoded != null && bufferInfo.size > 0 &&
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                muxerStarted
                            ) {
                                encoded.position(bufferInfo.offset)
                                encoded.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
                if (muxerStarted) {
                    runCatching { muxer.stop() }
                }
                runCatching { muxer.release() }
            }

            if (!outputFile.exists() || outputFile.length() < 128L) {
                error("AAC encoding produced an empty output file.")
            }
            measureAudioFileDurationMs(outputFile).takeIf { it > 0L } ?: totalDurationMs
        }
    }

    fun measureAudioFileDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun assignTimingsToSegments(
        chunkedSegments: List<List<AudiobookSegment>>,
        chunkDurationsMs: List<Long>
    ): List<AudiobookSegment> {
        val timed = mutableListOf<AudiobookSegment>()
        var cursorMs = 0L
        chunkedSegments.forEachIndexed { chunkIdx, segs ->
            val chunkDur = chunkDurationsMs.getOrElse(chunkIdx) { 0L }.coerceAtLeast(500L * segs.size)
            val totalChars = segs.sumOf { it.text.length.coerceAtLeast(1) }.coerceAtLeast(1)
            var localCursor = cursorMs
            segs.forEachIndexed { sIdx, seg ->
                val weight = seg.text.length.coerceAtLeast(1).toDouble() / totalChars.toDouble()
                val segEnd = if (sIdx == segs.lastIndex) {
                    cursorMs + chunkDur
                } else {
                    (localCursor + (chunkDur * weight).toLong()).coerceAtMost(cursorMs + chunkDur)
                }
                timed.add(
                    seg.copy(
                        startMs = localCursor,
                        endMs = segEnd.coerceAtLeast(localCursor + 100L)
                    )
                )
                localCursor = segEnd
            }
            cursorMs += chunkDur
        }
        return timed
    }

    fun resolveEffectiveFormat(requested: AudiobookFormat, isPolly: Boolean): AudiobookFormat {
        if (isPolly) return requested
        return if (requested.supportsOfflineEncoding) requested else AudiobookFormat.M4B
    }

    private fun stripId3v2Header(mp3Bytes: ByteArray): ByteArray {
        if (mp3Bytes.size < 10) return mp3Bytes
        if (mp3Bytes[0] == 'I'.code.toByte() &&
            mp3Bytes[1] == 'D'.code.toByte() &&
            mp3Bytes[2] == '3'.code.toByte()
        ) {
            val size = ((mp3Bytes[6].toInt() and 0x7F) shl 21) or
                ((mp3Bytes[7].toInt() and 0x7F) shl 14) or
                ((mp3Bytes[8].toInt() and 0x7F) shl 7) or
                (mp3Bytes[9].toInt() and 0x7F)
            val headerEnd = (10 + size).coerceAtMost(mp3Bytes.size)
            return mp3Bytes.copyOfRange(headerEnd, mp3Bytes.size)
        }
        return mp3Bytes
    }

    private fun buildWavHeader(
        pcmByteSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmByteSize + 36
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(totalDataLen)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort()) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmByteSize)
        }.array()
    }
}
