package com.example.data.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.model.OfflineVoice
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class OfflineTtsManager(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isReady = false

    // Package name of the engine we are currently bound to (null = system default).
    private var boundEnginePackage: String? = null
    // True when Pocket TTS was requested but is not installed, so we fell back.
    private var pocketTtsMissingNote = false

    private val _voices = MutableStateFlow<List<OfflineVoice>>(emptyList())
    val voices: StateFlow<List<OfflineVoice>> = _voices.asStateFlow()

    private val _engineStatus = MutableStateFlow("Initializing offline voice engine...")
    val engineStatus: StateFlow<String> = _engineStatus.asStateFlow()

    private val pendingSyntheses = ConcurrentHashMap<String, CancellableContinuation<Boolean>>()

    init {
        initializeTts()
    }

    fun initializeTts(enginePackage: String? = null) {
        tts?.shutdown()
        isReady = false
        boundEnginePackage = enginePackage
        pocketTtsMissingNote = false
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { pendingSyntheses.remove(it)?.resume(true) }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { pendingSyntheses.remove(it)?.resume(false) }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        utteranceId?.let { pendingSyntheses.remove(it)?.resume(false) }
                    }
                })
                refreshVoices()
            } else {
                _engineStatus.value = "Offline TTS unavailable on this device."
            }
        }
        tts = if (enginePackage != null) {
            TextToSpeech(appContext, listener, enginePackage)
        } else {
            TextToSpeech(appContext, listener)
        }
    }

    /** True when the Pocket TTS app is installed and can serve as a TTS engine. */
    fun isPocketTtsInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(POCKET_TTS_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * Bind the offline engine to Pocket TTS when requested and installed,
     * otherwise to the system default engine. Safe to call on every
     * preference change — a no-op when the bound engine already matches.
     */
    fun setEngine(usePocketTts: Boolean) {
        val target: String? = if (usePocketTts && isPocketTtsInstalled()) {
            POCKET_TTS_PACKAGE
        } else {
            null
        }
        pocketTtsMissingNote = usePocketTts && target == null
        if (target == boundEnginePackage && tts != null) return
        initializeTts(target)
    }

    fun refreshVoices() {
        val engine = tts ?: return
        if (!isReady) return
        val rawVoices = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
        val offlineRanked = rawVoices
            .filter { !it.isNetworkConnectionRequired }
            .sortedWith(
                compareByDescending<Voice> { it.locale.language == "en" }
                    .thenByDescending { it.quality }
                    .thenBy { it.locale.displayName }
                    .thenBy { it.name }
            )

        val mapped = offlineRanked.map { voice ->
            OfflineVoice(
                id = voice.name,
                displayName = friendlyOfflineVoiceName(voice),
                localeTag = voice.locale.toLanguageTag(),
                qualityTier = when {
                    voice.quality >= Voice.QUALITY_VERY_HIGH -> "Local HD"
                    voice.quality >= Voice.QUALITY_HIGH -> "High"
                    else -> "Standard"
                },
                isNetworkFree = true
            )
        }

        _voices.value = if (mapped.isNotEmpty()) {
            mapped
        } else {
            listOf(
                OfflineVoice(
                    id = "",
                    displayName = "System Default Offline Narrator",
                    localeTag = Locale.getDefault().toLanguageTag(),
                    qualityTier = "System",
                    isNetworkFree = true
                )
            )
        }
        _engineStatus.value = when {
            boundEnginePackage == POCKET_TTS_PACKAGE ->
                "Pocket TTS · ${_voices.value.size} offline voice(s) ready (no internet required)"
            pocketTtsMissingNote ->
                "Pocket TTS not installed — system engine · ${_voices.value.size} offline voice(s) ready"
            else ->
                "${_voices.value.size} offline voice(s) ready (no internet required)"
        }
    }

    fun previewVoice(
        voiceId: String,
        rate: Float,
        pitch: Float,
        sampleText: String = "This is a local offline voice preview. No internet or Polly allotment is used."
    ) {
        val engine = tts ?: return
        if (!isReady) return
        applyVoiceSelection(engine, voiceId, rate, pitch)
        engine.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "preview_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
    }

    suspend fun synthesizeChunkToWavFile(
        text: String,
        voiceId: String,
        rate: Float,
        pitch: Float,
        outputFile: File
    ): Boolean = withContext(Dispatchers.Main) {
        val engine = tts ?: return@withContext false
        if (!isReady) return@withContext false
        applyVoiceSelection(engine, voiceId, rate, pitch)

        suspendCancellableCoroutine { cont ->
            val utteranceId = "vox_${UUID.randomUUID()}"
            pendingSyntheses[utteranceId] = cont
            cont.invokeOnCancellation {
                pendingSyntheses.remove(utteranceId)
            }
            val params = Bundle()
            val res = engine.synthesizeToFile(text, params, outputFile, utteranceId)
            if (res != TextToSpeech.SUCCESS) {
                pendingSyntheses.remove(utteranceId)?.resume(false)
            }
        }
    }

    fun buildInstallVoicesIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

    private fun applyVoiceSelection(
        engine: TextToSpeech,
        voiceId: String,
        rate: Float,
        pitch: Float
    ) {
        engine.setSpeechRate(rate.coerceIn(0.6f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.6f, 1.5f))
        if (voiceId.isNotBlank()) {
            val match = runCatching { engine.voices?.firstOrNull { it.name == voiceId } }.getOrNull()
            if (match != null) {
                engine.voice = match
                return
            }
        }
        engine.language = Locale.US
    }

    private fun friendlyOfflineVoiceName(voice: Voice): String {
        // Pocket TTS voices are named plainly ("alba", "jean", clones by name) —
        // show the name as-is instead of the "Narrator ALBA" fallback.
        if (boundEnginePackage == POCKET_TTS_PACKAGE) {
            val pretty = voice.name.replaceFirstChar { it.titlecase(Locale.US) }
            val regionLabel = voice.locale.displayCountry.ifBlank { voice.locale.displayLanguage }
            return "$pretty ($regionLabel)"
        }
        val nameLower = voice.name.lowercase(Locale.US)
        val curatedPersona = CURATED_LOCAL_PERSONAS.entries
            .firstOrNull { nameLower.contains(it.key) }
            ?.value

        val regionLabel = voice.locale.displayCountry.ifBlank { voice.locale.displayLanguage }
        return if (curatedPersona != null) {
            "$curatedPersona ($regionLabel)"
        } else {
            val shortCode = voice.name
                .removeSuffix("-local")
                .substringAfterLast('-')
                .uppercase(Locale.US)
            "Narrator $shortCode (${voice.locale.displayLanguage} · $regionLabel)"
        }
    }

    fun release() {
        pendingSyntheses.values.forEach { it.cancel() }
        pendingSyntheses.clear()
        runCatching { tts?.shutdown() }
        tts = null
    }

    companion object {
        /** Package name of the Pocket TTS app, which exposes a system TTS engine. */
        const val POCKET_TTS_PACKAGE = "com.timberpocket.tts"

        private val CURATED_LOCAL_PERSONAS = mapOf(
            "tpf" to "Aria · Warm Clear",
            "sfg" to "Clara · Classic Book",
            "iol" to "Marcus · Deep Narrative",
            "iom" to "Julian · Crisp Studio",
            "iob" to "Elena · Storytelling",
            "iog" to "Devon · Balanced",
            "tpc" to "Sienna · Expressive",
            "tpd" to "Nolan · Steady",
            "gba" to "Charlotte · British",
            "gbd" to "Oliver · British",
            "rjs" to "Arthur · British Deep",
            "aua" to "Matilda · Australian",
            "aub" to "Liam · Australian"
        )
    }
}
