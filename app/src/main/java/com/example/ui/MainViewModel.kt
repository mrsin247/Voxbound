package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.AudioMuxerAndEncoder
import com.example.data.audio.AudiobookPlayerController
import com.example.data.audio.OfflineTtsManager
import com.example.data.audio.PlayerUiState
import com.example.data.document.DocumentTextExtractor
import com.example.data.local.AppDatabase
import com.example.data.local.AudiobookEntity
import com.example.data.local.AudiobookRepository
import com.example.data.local.SecureCredentialStore
import com.example.data.local.SettingsPreferencesRepository
import com.example.data.local.VoicePreferences
import com.example.data.model.AudiobookFormat
import com.example.data.model.AudiobookSegment
import com.example.data.model.AwsCredentials
import com.example.data.model.OfflineVoice
import com.example.data.model.PollyEngine
import com.example.data.model.PollyVoice
import com.example.data.model.ReaderSettings
import com.example.data.model.SynthesisState
import com.example.data.model.VoiceProvider
import com.example.data.network.AmazonPollyClient
import com.example.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface PollyVerifyStatus {
    data object Idle : PollyVerifyStatus
    data object Verifying : PollyVerifyStatus
    data class Verified(val voiceCount: Int) : PollyVerifyStatus
    data class Failed(val errorMessage: String) : PollyVerifyStatus
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val repository = AudiobookRepository(db.audiobookDao())
    private val prefsRepo = SettingsPreferencesRepository(appContext)
    private val credentialStore = SecureCredentialStore(appContext)
    private val extractor = DocumentTextExtractor(appContext)
    private val pollyClient = AmazonPollyClient()
    private val offlineTts = OfflineTtsManager(appContext)
    val isPocketTtsInstalled: Boolean = offlineTts.isPocketTtsInstalled()
    private val networkMonitor = NetworkMonitor(appContext)

    private val playerController = AudiobookPlayerController(
        context = appContext,
        scope = viewModelScope,
        onPersistPosition = { id, posMs ->
            viewModelScope.launch(Dispatchers.IO) {
                repository.updatePlaybackPosition(id, posMs)
            }
        }
    )

    val libraryBooks: StateFlow<List<AudiobookEntity>> = repository.allAudiobooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readerSettings: StateFlow<ReaderSettings> = prefsRepo.readerSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    val voicePreferences: StateFlow<VoicePreferences> = prefsRepo.voicePreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoicePreferences())

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    val offlineVoices: StateFlow<List<OfflineVoice>> = offlineTts.voices
    val offlineEngineStatus: StateFlow<String> = offlineTts.engineStatus
    val playerState: StateFlow<PlayerUiState> = playerController.state

    private val _awsCredentials = MutableStateFlow(AwsCredentials())
    val awsCredentials: StateFlow<AwsCredentials> = _awsCredentials.asStateFlow()

    private val _pollyVoices = MutableStateFlow(AmazonPollyClient.CURATED_DEFAULT_VOICES)
    val pollyVoices: StateFlow<List<PollyVoice>> = _pollyVoices.asStateFlow()

    private val _pollyVerifyStatus = MutableStateFlow<PollyVerifyStatus>(PollyVerifyStatus.Idle)
    val pollyVerifyStatus: StateFlow<PollyVerifyStatus> = _pollyVerifyStatus.asStateFlow()

    private val _synthesisState = MutableStateFlow<SynthesisState>(SynthesisState.Idle)
    val synthesisState: StateFlow<SynthesisState> = _synthesisState.asStateFlow()

    private val _activeStudioBookId = MutableStateFlow<String?>(null)
    val activeStudioBookId: StateFlow<String?> = _activeStudioBookId.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private var synthesisJob: Job? = null

    init {
        viewModelScope.launch {
            val savedCreds = credentialStore.loadCredentials()
            _awsCredentials.value = savedCreds
            if (savedCreds.isConfigured && networkMonitor.isOnline.value) {
                verifyAndSyncPollyVoices(savedCreds, silent = true)
            }
        }
        // Keep the offline engine bound to the chosen provider: Pocket TTS
        // when it is the preferred provider (and installed), system default
        // otherwise. setEngine is a no-op when nothing changed, so rate and
        // pitch slider drags do not re-init the engine.
        viewModelScope.launch {
            voicePreferences.collect { prefs ->
                offlineTts.setEngine(prefs.preferredProvider == VoiceProvider.POCKET_TTS)
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun resetSynthesisState() {
        _synthesisState.value = SynthesisState.Idle
    }

    fun setStudioBookId(id: String?) {
        _activeStudioBookId.value = id
    }

    fun decodeSegments(entity: AudiobookEntity): List<AudiobookSegment> =
        repository.decodeSegments(entity.segmentsJson)

    fun importDocumentUri(uri: Uri, onReadyForStudio: (String) -> Unit) {
        viewModelScope.launch {
            _isImporting.value = true
            val result = extractor.extractFromUri(uri)
            result.onSuccess { doc ->
                val id = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val entity = AudiobookEntity(
                    id = id,
                    title = doc.title,
                    sourceFileName = doc.sourceFileName,
                    sourceType = doc.sourceType,
                    fullText = doc.fullText,
                    segmentsJson = repository.encodeSegments(doc.segments),
                    audioFilePath = null,
                    audioFormat = null,
                    audioFileSizeBytes = 0L,
                    durationMs = 0L,
                    lastPositionMs = 0L,
                    voiceUsedLabel = "Not synthesized yet",
                    voiceProvider = "NONE",
                    characterCount = doc.fullText.length,
                    pageCount = doc.pageCount,
                    createdAt = now,
                    updatedAt = now
                )
                repository.upsert(entity)
                _activeStudioBookId.value = id
                _isImporting.value = false
                onReadyForStudio(id)
            }.onFailure { err ->
                _isImporting.value = false
                _snackbarMessage.value = err.message ?: "Failed to read document."
            }
        }
    }

    fun importPastedText(title: String, rawText: String, onReadyForStudio: (String) -> Unit) {
        if (rawText.isBlank()) {
            _snackbarMessage.value = "Please enter or paste some text first."
            return
        }
        viewModelScope.launch {
            val doc = extractor.extractFromPlainText(title, rawText, "TEXT")
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val entity = AudiobookEntity(
                id = id,
                title = doc.title,
                sourceFileName = doc.sourceFileName,
                sourceType = doc.sourceType,
                fullText = doc.fullText,
                segmentsJson = repository.encodeSegments(doc.segments),
                audioFilePath = null,
                audioFormat = null,
                audioFileSizeBytes = 0L,
                durationMs = 0L,
                lastPositionMs = 0L,
                voiceUsedLabel = "Not synthesized yet",
                voiceProvider = "NONE",
                characterCount = doc.fullText.length,
                pageCount = doc.pageCount,
                createdAt = now,
                updatedAt = now
            )
            repository.upsert(entity)
            _activeStudioBookId.value = id
            onReadyForStudio(id)
        }
    }

    fun loadClassicStorySample(onReadyForStudio: (String) -> Unit) {
        val sampleText = """
            Chapter One: The Quiet Lantern

            One dollar and eighty-seven cents. That was all. And sixty cents of it was in pennies. Pennies saved one and two at a time by bulldozing the grocer and the vegetable man and the butcher until one's cheeks burned with the silent imputation of parsimony that such close dealing implied.

            Three times Della counted it. One dollar and eighty-seven cents. And the next day would be Christmas. There was clearly nothing to do but flop down on the shabby little couch and howl. So Della did it. Which instigates the moral reflection that life is made up of sobs, sniffles, and smiles, with sniffles predominating.

            While the mistress of the home is gradually subsiding from the first stage to the second, take a look at the home. A furnished flat at eight dollars per week. It did not exactly beggar description, but it certainly had that word on the lookout for the mendicancy squad.

            In the vestibule below was a letter-box into which no letter would go, and an electric button from which no mortal finger could coax a ring. Also appertaining thereunto was a card bearing the name Mr. James Dillingham Young.
        """.trimIndent()
        importPastedText("The Gift of the Magi (Public Domain Sample)", sampleText, onReadyForStudio)
    }

    fun updateTitle(bookId: String, newTitle: String) {
        viewModelScope.launch {
            val existing = repository.getAudiobook(bookId) ?: return@launch
            repository.upsert(existing.copy(title = newTitle.trim().ifBlank { existing.title }))
        }
    }

    fun generateAudiobook(
        bookId: String,
        requestedProvider: VoiceProvider,
        requestedFormat: AudiobookFormat
    ) {
        if (synthesisJob?.isActive == true) return
        synthesisJob = viewModelScope.launch {
            val book = repository.getAudiobook(bookId)
            if (book == null) {
                _synthesisState.value = SynthesisState.Error("Document not found.")
                return@launch
            }

            val segments = repository.decodeSegments(book.segmentsJson)
            if (segments.isEmpty()) {
                _synthesisState.value = SynthesisState.Error("Document has no readable segments.")
                return@launch
            }

            val creds = _awsCredentials.value
            val prefs = voicePreferences.value
            val online = networkMonitor.isOnline.value

            val useOfflineFallback = requestedProvider == VoiceProvider.AMAZON_POLLY &&
                (!online || !creds.isConfigured) &&
                prefs.autoOfflineFallback

            val effectiveProvider = when {
                requestedProvider == VoiceProvider.POCKET_TTS -> VoiceProvider.POCKET_TTS
                requestedProvider == VoiceProvider.OFFLINE_DEVICE -> VoiceProvider.OFFLINE_DEVICE
                !online || !creds.isConfigured -> {
                    if (prefs.autoOfflineFallback) {
                        VoiceProvider.OFFLINE_DEVICE
                    } else {
                        val reason = if (!online) {
                            "No internet connection. Enable Auto Offline Fallback or switch to Offline Voice in the Cog Settings menu."
                        } else {
                            "AWS IAM credentials are missing. Tap the Cog icon at the top to add your IAM Access Key and Secret Key."
                        }
                        _synthesisState.value = SynthesisState.Error(reason)
                        return@launch
                    }
                }
                else -> VoiceProvider.AMAZON_POLLY
            }

            val chunks = DocumentTextExtractor.groupSegmentsIntoChunks(segments, maxCharsPerChunk = 2400)
            val totalChunks = chunks.size
            val effectiveFormat = AudioMuxerAndEncoder.resolveEffectiveFormat(
                requested = requestedFormat,
                isPolly = effectiveProvider == VoiceProvider.AMAZON_POLLY
            )

            val booksDir = File(appContext.filesDir, "audiobooks").apply { mkdirs() }
            val tempDir = File(appContext.cacheDir, "synth_${book.id}").apply {
                deleteRecursively()
                mkdirs()
            }

            try {
                if (effectiveProvider == VoiceProvider.AMAZON_POLLY) {
                    synthesizeWithPolly(
                        book = book,
                        chunks = chunks,
                        creds = creds,
                        prefs = prefs,
                        targetFormat = effectiveFormat,
                        booksDir = booksDir
                    )
                } else {
                    synthesizeOffline(
                        book = book,
                        chunks = chunks,
                        prefs = prefs,
                        targetFormat = effectiveFormat,
                        booksDir = booksDir,
                        tempDir = tempDir,
                        isFallback = useOfflineFallback,
                        provider = effectiveProvider
                    )
                }
            } catch (e: Exception) {
                _synthesisState.value = SynthesisState.Error(
                    e.message ?: "Audiobook synthesis failed."
                )
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    private suspend fun synthesizeWithPolly(
        book: AudiobookEntity,
        chunks: List<List<AudiobookSegment>>,
        creds: AwsCredentials,
        prefs: VoicePreferences,
        targetFormat: AudiobookFormat,
        booksDir: File
    ) {
        val pollyFormat = when (targetFormat) {
            AudiobookFormat.MP3 -> "mp3"
            AudiobookFormat.OGG -> "ogg_vorbis"
            AudiobookFormat.WAV, AudiobookFormat.M4B, AudiobookFormat.M4A -> "pcm"
        }
        val sampleRate = if (pollyFormat == "pcm") "16000" else null
        val collectedBytes = mutableListOf<ByteArray>()

        chunks.forEachIndexed { index, segList ->
            _synthesisState.value = SynthesisState.InProgress(
                title = book.title,
                currentChunk = index + 1,
                totalChunks = chunks.size,
                statusMessage = "Polly (${prefs.pollyVoiceName}) synthesizing part ${index + 1} of ${chunks.size}..."
            )
            val chunkText = segList.joinToString(" ") { it.text }
            val result = pollyClient.synthesizeSpeech(
                credentials = creds,
                text = chunkText,
                voiceId = prefs.pollyVoiceId,
                engine = prefs.pollyEngine,
                pollyOutputFormat = pollyFormat,
                sampleRate = sampleRate
            )
            val bytes = result.getOrElse { throw it }
            collectedBytes.add(bytes)
        }

        _synthesisState.value = SynthesisState.InProgress(
            title = book.title,
            currentChunk = chunks.size,
            totalChunks = chunks.size,
            statusMessage = "Packaging ${targetFormat.displayName} file..."
        )

        var finalFormat = targetFormat
        var outFile = File(booksDir, "${book.id}.${finalFormat.extension}")
        book.audioFilePath?.let { old -> if (old != outFile.absolutePath) runCatching { File(old).delete() } }

        val chunkDurations: List<Long>
        when (targetFormat) {
            AudiobookFormat.MP3 -> {
                chunkDurations = AudioMuxerAndEncoder.writeMp3FromChunks(collectedBytes, outFile)
            }
            AudiobookFormat.OGG -> {
                chunkDurations = AudioMuxerAndEncoder.writeOggFromChunks(collectedBytes, outFile)
            }
            AudiobookFormat.WAV -> {
                chunkDurations = collectedBytes.map { AudioMuxerAndEncoder.pcmDurationMs(it.size, 16000, 1) }
                AudioMuxerAndEncoder.writeWavFromPcm(collectedBytes, 16000, 1, outFile)
            }
            AudiobookFormat.M4B, AudiobookFormat.M4A -> {
                chunkDurations = collectedBytes.map { AudioMuxerAndEncoder.pcmDurationMs(it.size, 16000, 1) }
                val muxResult = AudioMuxerAndEncoder.encodePcmToM4aOrM4b(collectedBytes, 16000, 1, outFile)
                if (muxResult.isFailure) {
                    finalFormat = AudiobookFormat.WAV
                    outFile = File(booksDir, "${book.id}.wav")
                    AudioMuxerAndEncoder.writeWavFromPcm(collectedBytes, 16000, 1, outFile)
                }
            }
        }

        val timedSegments = AudioMuxerAndEncoder.assignTimingsToSegments(chunks, chunkDurations)
        val totalDurationMs = timedSegments.lastOrNull()?.endMs
            ?: AudioMuxerAndEncoder.measureAudioFileDurationMs(outFile)

        val updated = book.copy(
            segmentsJson = repository.encodeSegments(timedSegments),
            audioFilePath = outFile.absolutePath,
            audioFormat = finalFormat.extension,
            audioFileSizeBytes = outFile.length(),
            durationMs = totalDurationMs,
            lastPositionMs = 0L,
            voiceUsedLabel = "Polly · ${prefs.pollyVoiceName} (${prefs.pollyEngine.label})",
            voiceProvider = VoiceProvider.AMAZON_POLLY.name,
            updatedAt = System.currentTimeMillis()
        )
        repository.upsert(updated)
        _synthesisState.value = SynthesisState.Completed(book.id, finalFormat)
    }

    private suspend fun synthesizeOffline(
        book: AudiobookEntity,
        chunks: List<List<AudiobookSegment>>,
        prefs: VoicePreferences,
        targetFormat: AudiobookFormat,
        booksDir: File,
        tempDir: File,
        isFallback: Boolean,
        provider: VoiceProvider
    ) {
        val pcmChunks = mutableListOf<ByteArray>()
        val chunkDurations = mutableListOf<Long>()
        var detectedSampleRate = 22050
        var detectedChannels = 1

        chunks.forEachIndexed { index, segList ->
            _synthesisState.value = SynthesisState.InProgress(
                title = book.title,
                currentChunk = index + 1,
                totalChunks = chunks.size,
                statusMessage = if (isFallback) {
                    "Offline fallback: rendering part ${index + 1} of ${chunks.size}..."
                } else {
                    "Offline voice rendering part ${index + 1} of ${chunks.size}..."
                },
                usingOfflineFallback = isFallback
            )

            val chunkText = segList.joinToString(" ") { it.text }
            val wavFile = File(tempDir, "chunk_$index.wav")
            val ok = offlineTts.synthesizeChunkToWavFile(
                text = chunkText,
                voiceId = prefs.offlineVoiceId,
                rate = prefs.offlineSpeechRate,
                pitch = prefs.offlinePitch,
                outputFile = wavFile
            )
            if (!ok || !wavFile.exists() || wavFile.length() <= 44L) {
                error(
                    if (provider == VoiceProvider.POCKET_TTS) {
                        "Offline TTS failed to synthesize part ${index + 1}. Ensure Pocket TTS is installed and has a voice ready."
                    } else {
                        "Offline TTS failed to synthesize part ${index + 1}. Ensure an offline system voice pack is installed."
                    }
                )
            }

            val pcm = withContext(Dispatchers.IO) { AudioMuxerAndEncoder.extractPcmFromWav(wavFile) }
            detectedSampleRate = pcm.sampleRate
            detectedChannels = pcm.channels
            pcmChunks.add(pcm.samples)
            chunkDurations.add(AudioMuxerAndEncoder.pcmDurationMs(pcm.samples.size, pcm.sampleRate, pcm.channels))
        }

        _synthesisState.value = SynthesisState.InProgress(
            title = book.title,
            currentChunk = chunks.size,
            totalChunks = chunks.size,
            statusMessage = "Saving offline ${targetFormat.displayName}...",
            usingOfflineFallback = isFallback
        )

        var finalFormat = if (targetFormat.supportsOfflineEncoding) targetFormat else AudiobookFormat.M4B
        var outFile = File(booksDir, "${book.id}.${finalFormat.extension}")
        book.audioFilePath?.let { old -> if (old != outFile.absolutePath) runCatching { File(old).delete() } }

        if (finalFormat == AudiobookFormat.WAV) {
            AudioMuxerAndEncoder.writeWavFromPcm(pcmChunks, detectedSampleRate, detectedChannels, outFile)
        } else {
            val res = AudioMuxerAndEncoder.encodePcmToM4aOrM4b(
                pcmChunks,
                detectedSampleRate,
                detectedChannels,
                outFile
            )
            if (res.isFailure) {
                finalFormat = AudiobookFormat.WAV
                outFile = File(booksDir, "${book.id}.wav")
                AudioMuxerAndEncoder.writeWavFromPcm(pcmChunks, detectedSampleRate, detectedChannels, outFile)
            }
        }

        val timedSegments = AudioMuxerAndEncoder.assignTimingsToSegments(chunks, chunkDurations)
        val totalDurationMs = timedSegments.lastOrNull()?.endMs
            ?: AudioMuxerAndEncoder.measureAudioFileDurationMs(outFile)

        val updated = book.copy(
            segmentsJson = repository.encodeSegments(timedSegments),
            audioFilePath = outFile.absolutePath,
            audioFormat = finalFormat.extension,
            audioFileSizeBytes = outFile.length(),
            durationMs = totalDurationMs,
            lastPositionMs = 0L,
            voiceUsedLabel = "${if (provider == VoiceProvider.POCKET_TTS) "Pocket TTS" else "Offline"} · ${prefs.offlineVoiceLabel}",
            voiceProvider = provider.name,
            updatedAt = System.currentTimeMillis()
        )
        repository.upsert(updated)
        _synthesisState.value = SynthesisState.Completed(book.id, finalFormat)
    }

    fun cancelSynthesis() {
        synthesisJob?.cancel()
        _synthesisState.value = SynthesisState.Idle
    }

    fun exportAudiobookToUri(entity: AudiobookEntity, destUri: Uri) {
        val srcPath = entity.audioFilePath
        if (srcPath.isNullOrBlank()) {
            _snackbarMessage.value = "Generate the audiobook first before exporting the audio file."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val srcFile = File(srcPath)
                appContext.contentResolver.openOutputStream(destUri)?.use { out ->
                    srcFile.inputStream().use { input -> input.copyTo(out) }
                } ?: error("Could not open destination file.")
            }
            withContext(Dispatchers.Main) {
                _snackbarMessage.value = if (result.isSuccess) {
                    "Saved ${entity.title}.${entity.audioFormat ?: "m4b"} to device storage."
                } else {
                    result.exceptionOrNull()?.message ?: "Export failed."
                }
            }
        }
    }

    fun deleteAudiobook(entity: AudiobookEntity) {
        viewModelScope.launch {
            if (_playerStateMatches(entity.id)) {
                playerController.pause()
            }
            repository.deleteAudiobook(entity)
            _snackbarMessage.value = "Deleted \"${entity.title}\"."
        }
    }

    private fun _playerStateMatches(id: String): Boolean =
        playerController.state.value.loadedAudiobookId == id

    // Player controls (0-allotment local playback)
    fun openBookInPlayer(entity: AudiobookEntity) {
        val segments = repository.decodeSegments(entity.segmentsJson)
        playerController.loadLocalAudiobook(
            audiobookId = entity.id,
            audioFilePath = entity.audioFilePath,
            fallbackDurationMs = entity.durationMs,
            resumePositionMs = entity.lastPositionMs,
            bookSegments = segments
        )
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun skipBy(deltaMs: Long) = playerController.skipBy(deltaMs)
    fun jumpToSegment(segment: AudiobookSegment) = playerController.jumpToSegment(segment)
    fun setPlaybackSpeed(speed: Float) = playerController.setPlaybackSpeed(speed)
    fun pausePlayer() = playerController.pause()

    // Voice & IAM Settings
    fun saveAndVerifyPollyCredentials(credentials: AwsCredentials) {
        viewModelScope.launch {
            credentialStore.saveCredentials(credentials)
            _awsCredentials.value = credentials
            verifyAndSyncPollyVoices(credentials, silent = false)
        }
    }

    fun clearPollyCredentials() {
        viewModelScope.launch {
            credentialStore.clearCredentials()
            _awsCredentials.value = AwsCredentials(region = _awsCredentials.value.region)
            _pollyVerifyStatus.value = PollyVerifyStatus.Idle
            _pollyVoices.value = AmazonPollyClient.CURATED_DEFAULT_VOICES
            _snackbarMessage.value = "AWS IAM credentials cleared."
        }
    }

    private suspend fun verifyAndSyncPollyVoices(credentials: AwsCredentials, silent: Boolean) {
        if (!credentials.isConfigured) {
            if (!silent) {
                _pollyVerifyStatus.value = PollyVerifyStatus.Failed("Enter both IAM Access Key ID and Secret Access Key.")
            }
            return
        }
        _pollyVerifyStatus.value = PollyVerifyStatus.Verifying
        val res = pollyClient.fetchVoices(credentials)
        res.onSuccess { list ->
            if (list.isNotEmpty()) {
                _pollyVoices.value = list
            }
            _pollyVerifyStatus.value = PollyVerifyStatus.Verified(list.size)
            if (!silent) {
                _snackbarMessage.value = "Connected to AWS Polly (${credentials.region}) · ${list.size} voices loaded."
            }
        }.onFailure { err ->
            val msg = err.message ?: "Unable to verify AWS IAM credentials."
            _pollyVerifyStatus.value = PollyVerifyStatus.Failed(msg)
            if (!silent) {
                _snackbarMessage.value = msg
            }
        }
    }

    fun previewPollyVoice(voice: PollyVoice, engine: PollyEngine) {
        val creds = _awsCredentials.value
        if (!creds.isConfigured) {
            _snackbarMessage.value = "Enter your AWS IAM credentials above to preview Polly voices."
            return
        }
        if (!networkMonitor.isOnline.value) {
            _snackbarMessage.value = "You are offline. Try an Offline Device Voice preview below."
            return
        }
        viewModelScope.launch {
            val activeEngine = if (voice.supportedEngines.contains(engine.apiValue)) {
                engine
            } else {
                PollyEngine.entries.firstOrNull { voice.supportedEngines.contains(it.apiValue) }
                    ?: PollyEngine.STANDARD
            }
            val sampleText = "Hello, I am ${voice.name}. Once saved as an audiobook, you can replay me anytime without using your voice allotment."
            val res = pollyClient.synthesizeSpeech(
                credentials = creds,
                text = sampleText,
                voiceId = voice.id,
                engine = activeEngine,
                pollyOutputFormat = "mp3"
            )
            res.onSuccess { bytes ->
                val sampleFile = File(appContext.cacheDir, "polly_preview_${voice.id}.mp3")
                withContext(Dispatchers.IO) { sampleFile.writeBytes(bytes) }
                playerController.playFileSample(sampleFile)
            }.onFailure { err ->
                _snackbarMessage.value = err.message ?: "Polly preview failed."
            }
        }
    }

    fun previewOfflineVoice(voice: OfflineVoice) {
        val prefs = voicePreferences.value
        offlineTts.previewVoice(
            voiceId = voice.id,
            rate = prefs.offlineSpeechRate,
            pitch = prefs.offlinePitch
        )
    }

    fun refreshOfflineVoices() {
        offlineTts.refreshVoices()
    }

    fun buildInstallOfflineVoicesIntent() = offlineTts.buildInstallVoicesIntent()

    /** Opens the Pocket TTS app so the user can clone a voice there, then come back. */
    fun openPocketTtsForCloning() {
        val intent = appContext.packageManager
            .getLaunchIntentForPackage(OfflineTtsManager.POCKET_TTS_PACKAGE)
        if (intent == null) {
            _snackbarMessage.value = "Pocket TTS isn't installed on this phone."
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure { _snackbarMessage.value = "Couldn't open Pocket TTS." }
    }

    fun updateVoicePreferences(transform: (VoicePreferences) -> VoicePreferences) {
        viewModelScope.launch {
            prefsRepo.updateVoicePreferences(transform)
        }
    }

    fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch {
            prefsRepo.updateReaderSettings(transform)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
        offlineTts.release()
        networkMonitor.release()
    }
}
