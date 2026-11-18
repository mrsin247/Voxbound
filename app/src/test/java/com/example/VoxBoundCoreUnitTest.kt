package com.example

import com.example.data.audio.AudioMuxerAndEncoder
import com.example.data.document.DocumentTextExtractor
import com.example.data.model.AudiobookSegment
import com.example.data.network.AwsSigV4Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoxBoundCoreUnitTest {

    @Test
    fun sigV4SignerProducesValidHeadersForPolly() {
        val req = AwsSigV4Signer.buildSignedRequest(
            method = "POST",
            region = "us-east-1",
            path = "/v1/speech",
            jsonBody = """{"Text":"Hello","VoiceId":"Joanna","OutputFormat":"mp3"}""",
            accessKeyId = "AKIAEXAMPLEKEY",
            secretAccessKey = "secretExampleKey123",
            sessionToken = "tempTokenABC"
        )
        val auth = req.header("Authorization").orEmpty()
        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential=AKIAEXAMPLEKEY/"))
        assertTrue(auth.contains("/us-east-1/polly/aws4_request"))
        assertTrue(auth.contains("SignedHeaders="))
        assertTrue(auth.contains("Signature="))
        assertEquals("polly.us-east-1.amazonaws.com", req.header("Host"))
        assertEquals("tempTokenABC", req.header("X-Amz-Security-Token"))
        assertTrue(!req.header("X-Amz-Date").isNullOrBlank())
    }

    @Test
    fun chunkerRespectsPollyCharLimitAndAssignsSegmentTimings() {
        val segments = (0 until 30).map { i ->
            AudiobookSegment(
                index = i,
                text = "Sentence number $i has enough characters to test chunking cleanly across boundaries. " + "x".repeat(150),
                startMs = 0L,
                endMs = 0L,
                pageNumber = (i / 10) + 1
            )
        }
        val chunks = DocumentTextExtractor.groupSegmentsIntoChunks(segments, maxCharsPerChunk = 2400)
        assertTrue(chunks.size >= 2)
        chunks.forEach { chunk ->
            val len = chunk.sumOf { it.text.length + 1 }
            assertTrue(len <= 2400)
        }

        val durations = chunks.map { 5000L }
        val timed = AudioMuxerAndEncoder.assignTimingsToSegments(chunks, durations)
        assertEquals(segments.size, timed.size)
        assertEquals(0L, timed.first().startMs)
        assertEquals(chunks.size * 5000L, timed.last().endMs)
        for (i in 1 until timed.size) {
            assertTrue(timed[i].startMs >= timed[i - 1].startMs)
        }
    }

    @Test
    fun wavParserExtractsSampleRateAndPcmBytes() {
        val pcmSamples = ByteArray(3200) { (it % 127).toByte() }
        val wavBytes = ByteBuffer.allocate(44 + pcmSamples.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcmSamples.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(16000)
            putInt(32000)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmSamples.size)
            put(pcmSamples)
        }.array()

        val extracted = AudioMuxerAndEncoder.extractPcmFromWavBytes(wavBytes)
        assertEquals(16000, extracted.sampleRate)
        assertEquals(1, extracted.channels)
        assertEquals(pcmSamples.size, extracted.samples.size)
        assertEquals(100L, AudioMuxerAndEncoder.pcmDurationMs(extracted.samples.size, 16000, 1))
    }
}
