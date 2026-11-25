package com.example.data.model

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LexendFontFamily
import com.example.ui.theme.LiterataFontFamily
import com.example.ui.theme.MerriweatherFontFamily
import com.example.ui.theme.PlusJakartaSansFontFamily
import kotlinx.serialization.Serializable

enum class VoiceProvider(val displayName: String) {
    AMAZON_POLLY("Amazon Polly"),
    OFFLINE_DEVICE("Offline Device Voice"),
    POCKET_TTS("Pocket TTS")
}

enum class PollyEngine(val apiValue: String, val label: String) {
    NEURAL("neural", "Neural"),
    GENERATIVE("generative", "Generative"),
    LONG_FORM("long-form", "Long-Form"),
    STANDARD("standard", "Standard")
}

data class AwsCredentials(
    val accessKeyId: String = "",
    val secretAccessKey: String = "",
    val sessionToken: String = "",
    val region: String = "us-east-1"
) {
    val isConfigured: Boolean
        get() = accessKeyId.isNotBlank() && secretAccessKey.isNotBlank() && region.isNotBlank()
}

data class PollyVoice(
    val id: String,
    val name: String,
    val gender: String,
    val languageCode: String,
    val languageName: String,
    val supportedEngines: List<String>
)

data class OfflineVoice(
    val id: String,
    val displayName: String,
    val localeTag: String,
    val qualityTier: String,
    val isNetworkFree: Boolean,
    // "Female", "Male", or "Unknown" — the engine rarely reports this, so
    // known Pocket TTS voices are mapped by name and the rest stay Unknown.
    val gender: String = "Unknown"
)

enum class ReaderFontKey(val label: String) {
    LITERATA("Literata"),
    MERRIWEATHER("Merriweather"),
    PLUS_JAKARTA_SANS("Jakarta Sans"),
    LEXEND("Lexend"),
    JETBRAINS_MONO("JetBrains Mono");

    fun toFontFamily(): FontFamily = when (this) {
        LITERATA -> LiterataFontFamily
        MERRIWEATHER -> MerriweatherFontFamily
        PLUS_JAKARTA_SANS -> PlusJakartaSansFontFamily
        LEXEND -> LexendFontFamily
        JETBRAINS_MONO -> JetBrainsMonoFontFamily
    }
}

enum class ReaderTextAlign(val label: String) {
    START("Left"),
    CENTER("Center"),
    JUSTIFY("Justify")
}

enum class PagePositionMode(val label: String, val description: String) {
    TOP_THIRD("Top Focus", "Active line anchors near upper page"),
    CENTERED("Center Focus", "Active line stays vertically centered"),
    LOWER_THIRD("Lower Third", "Active line tracks near bottom third"),
    FREE_SCROLL("Free Page", "Manual scroll without auto-centering")
}

enum class ReaderThemeMode(val label: String) {
    PAPER_CREAM("Paper Cream"),
    WARM_SEPIA("Warm Sepia"),
    CRISP_WHITE("Crisp White"),
    MIDNIGHT_SLATE("Midnight Slate")
}

enum class HighlightStyle(val label: String) {
    AMBER_PILL("Amber Pill"),
    WARM_UNDERLINE("Underline"),
    INK_FOCUS("Ink Dimming")
}

data class ReaderSettings(
    val fontSizeSp: Float = 18f,
    val fontFamily: ReaderFontKey = ReaderFontKey.LITERATA,
    val fontWeightLevel: Int = 400,
    val italicBody: Boolean = false,
    val boldActiveSegment: Boolean = true,
    val italicActiveSegment: Boolean = false,
    val lineHeightMultiplier: Float = 1.6f,
    val letterSpacingEm: Float = 0.0f,
    val textAlign: ReaderTextAlign = ReaderTextAlign.START,
    val pagePositionMode: PagePositionMode = PagePositionMode.CENTERED,
    val horizontalMarginDp: Int = 20,
    val showPageNumbers: Boolean = true,
    val readerTheme: ReaderThemeMode = ReaderThemeMode.PAPER_CREAM,
    val highlightStyle: HighlightStyle = HighlightStyle.AMBER_PILL
) {
    fun toBaseFontWeight(): FontWeight = when (fontWeightLevel) {
        500 -> FontWeight.Medium
        600 -> FontWeight.SemiBold
        700 -> FontWeight.Bold
        else -> FontWeight.Normal
    }
}

@Serializable
data class AudiobookSegment(
    val index: Int,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val pageNumber: Int = 1,
    val chapterTitle: String = "",
    val isHeading: Boolean = false
)

sealed interface SynthesisState {
    data object Idle : SynthesisState
    data class InProgress(
        val title: String,
        val currentChunk: Int,
        val totalChunks: Int,
        val statusMessage: String,
        val usingOfflineFallback: Boolean = false
    ) : SynthesisState
    data class Completed(val audiobookId: String, val format: AudiobookFormat) : SynthesisState
    data class Error(val message: String) : SynthesisState
}
