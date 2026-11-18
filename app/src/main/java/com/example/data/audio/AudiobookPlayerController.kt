package com.example.data.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.AudiobookSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlayerUiState(
    val loadedAudiobookId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val activeSegmentIndex: Int = 0,
    val hasIncludedAudio: Boolean = false,
    val audioError: String? = null
)

class AudiobookPlayerController(
    context: Context,
    private val scope: CoroutineScope,
    private val onPersistPosition: (String, Long) -> Unit
) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private var segments: List<AudiobookSegment> = emptyList()
    private var positionTrackerJob: Job? = null
    private var lastPersistedAt = 0L

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionLoop()
                } else {
                    persistCurrentIfNeeded(force = true)
                    positionTrackerJob?.cancel()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = exoPlayer.duration.takeIf { it > 0L } ?: _state.value.durationMs
                _state.update { it.copy(durationMs = dur) }
                if (playbackState == Player.STATE_ENDED) {
                    _state.update { it.copy(isPlaying = false, currentPositionMs = dur) }
                    persistCurrentIfNeeded(force = true)
                }
            }
        })
    }

    fun loadLocalAudiobook(
        audiobookId: String,
        audioFilePath: String?,
        fallbackDurationMs: Long,
        resumePositionMs: Long,
        bookSegments: List<AudiobookSegment>
    ) {
        segments = bookSegments
        val file = audioFilePath?.let { File(it) }
        if (file == null || !file.exists()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            _state.value = PlayerUiState(
                loadedAudiobookId = audiobookId,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = fallbackDurationMs,
                activeSegmentIndex = 0,
                hasIncludedAudio = false,
                audioError = null
            )
            return
        }

        val alreadyLoaded = _state.value.loadedAudiobookId == audiobookId &&
            exoPlayer.mediaItemCount > 0

        if (!alreadyLoaded) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            exoPlayer.prepare()
            val targetPos = resumePositionMs.coerceIn(0L, fallbackDurationMs.coerceAtLeast(0L))
            if (targetPos > 0L) {
                exoPlayer.seekTo(targetPos)
            }
            _state.value = PlayerUiState(
                loadedAudiobookId = audiobookId,
                isPlaying = false,
                currentPositionMs = targetPos,
                durationMs = fallbackDurationMs,
                playbackSpeed = _state.value.playbackSpeed,
                activeSegmentIndex = findSegmentIndexForPosition(targetPos),
                hasIncludedAudio = true,
                audioError = null
            )
        } else {
            _state.update {
                it.copy(
                    hasIncludedAudio = true,
                    activeSegmentIndex = findSegmentIndexForPosition(exoPlayer.currentPosition)
                )
            }
        }
    }

    fun playFileSample(file: File) {
        exoPlayer.stop()
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exoPlayer.prepare()
        exoPlayer.play()
        _state.update { it.copy(loadedAudiobookId = null, isPlaying = true, hasIncludedAudio = false) }
    }

    fun togglePlayPause() {
        if (!_state.value.hasIncludedAudio) return
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
            }
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (!_state.value.hasIncludedAudio) return
        val maxDur = _state.value.durationMs.coerceAtLeast(0L)
        val clamped = positionMs.coerceIn(0L, maxDur)
        exoPlayer.seekTo(clamped)
        _state.update {
            it.copy(
                currentPositionMs = clamped,
                activeSegmentIndex = findSegmentIndexForPosition(clamped)
            )
        }
        persistCurrentIfNeeded(force = true)
    }

    fun skipBy(deltaMs: Long) {
        seekTo(_state.value.currentPositionMs + deltaMs)
    }

    fun jumpToSegment(segment: AudiobookSegment) {
        if (!_state.value.hasIncludedAudio) return
        seekTo(segment.startMs)
        if (!exoPlayer.isPlaying) {
            exoPlayer.play()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.6f, 2.5f)
        exoPlayer.playbackParameters = PlaybackParameters(clamped)
        _state.update { it.copy(playbackSpeed = clamped) }
    }

    fun pause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
    }

    private fun startPositionLoop() {
        positionTrackerJob?.cancel()
        positionTrackerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration.takeIf { it > 0L } ?: _state.value.durationMs
                val activeIdx = findSegmentIndexForPosition(pos)
                _state.update {
                    it.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                        activeSegmentIndex = activeIdx
                    )
                }
                persistCurrentIfNeeded(force = false)
                delay(120L)
            }
        }
    }

    private fun persistCurrentIfNeeded(force: Boolean) {
        val bookId = _state.value.loadedAudiobookId ?: return
        val now = System.currentTimeMillis()
        if (force || now - lastPersistedAt > 3000L) {
            lastPersistedAt = now
            onPersistPosition(bookId, exoPlayer.currentPosition.coerceAtLeast(0L))
        }
    }

    private fun findSegmentIndexForPosition(posMs: Long): Int {
        if (segments.isEmpty()) return 0
        val idx = segments.indexOfFirst { posMs in it.startMs..it.endMs }
        if (idx >= 0) return idx
        val before = segments.indexOfLast { it.startMs <= posMs }
        return if (before >= 0) before else 0
    }

    fun release() {
        positionTrackerJob?.cancel()
        persistCurrentIfNeeded(force = true)
        exoPlayer.release()
    }
}
