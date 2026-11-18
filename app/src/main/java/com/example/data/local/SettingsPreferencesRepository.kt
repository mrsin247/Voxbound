package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AudiobookFormat
import com.example.data.model.HighlightStyle
import com.example.data.model.PagePositionMode
import com.example.data.model.PollyEngine
import com.example.data.model.ReaderFontKey
import com.example.data.model.ReaderSettings
import com.example.data.model.ReaderTextAlign
import com.example.data.model.ReaderThemeMode
import com.example.data.model.VoiceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voxDataStore by preferencesDataStore(name = "voxbound_preferences")

data class VoicePreferences(
    val preferredProvider: VoiceProvider = VoiceProvider.AMAZON_POLLY,
    val autoOfflineFallback: Boolean = true,
    val pollyVoiceId: String = "Joanna",
    val pollyVoiceName: String = "Joanna",
    val pollyEngine: PollyEngine = PollyEngine.NEURAL,
    val offlineVoiceId: String = "",
    val offlineVoiceLabel: String = "System Default Offline Voice",
    val offlineSpeechRate: Float = 1.0f,
    val offlinePitch: Float = 1.0f,
    val preferredOutputFormat: AudiobookFormat = AudiobookFormat.M4B
)

class SettingsPreferencesRepository(private val context: Context) {

    val readerSettings: Flow<ReaderSettings> = context.voxDataStore.data.map { prefs ->
        ReaderSettings(
            fontSizeSp = prefs[KEY_FONT_SIZE] ?: 18f,
            fontFamily = enumValueOrDefault(prefs[KEY_FONT_FAMILY], ReaderFontKey.LITERATA),
            fontWeightLevel = prefs[KEY_FONT_WEIGHT] ?: 400,
            italicBody = prefs[KEY_ITALIC_BODY] ?: false,
            boldActiveSegment = prefs[KEY_BOLD_ACTIVE] ?: true,
            italicActiveSegment = prefs[KEY_ITALIC_ACTIVE] ?: false,
            lineHeightMultiplier = prefs[KEY_LINE_HEIGHT] ?: 1.6f,
            letterSpacingEm = prefs[KEY_LETTER_SPACING] ?: 0.0f,
            textAlign = enumValueOrDefault(prefs[KEY_TEXT_ALIGN], ReaderTextAlign.START),
            pagePositionMode = enumValueOrDefault(prefs[KEY_PAGE_POSITION], PagePositionMode.CENTERED),
            horizontalMarginDp = prefs[KEY_H_MARGIN] ?: 20,
            showPageNumbers = prefs[KEY_SHOW_PAGE_NUMBERS] ?: true,
            readerTheme = enumValueOrDefault(prefs[KEY_READER_THEME], ReaderThemeMode.PAPER_CREAM),
            highlightStyle = enumValueOrDefault(prefs[KEY_HIGHLIGHT_STYLE], HighlightStyle.AMBER_PILL)
        )
    }

    val voicePreferences: Flow<VoicePreferences> = context.voxDataStore.data.map { prefs ->
        VoicePreferences(
            preferredProvider = enumValueOrDefault(prefs[KEY_PROVIDER], VoiceProvider.AMAZON_POLLY),
            autoOfflineFallback = prefs[KEY_AUTO_FALLBACK] ?: true,
            pollyVoiceId = prefs[KEY_POLLY_VOICE_ID] ?: "Joanna",
            pollyVoiceName = prefs[KEY_POLLY_VOICE_NAME] ?: "Joanna",
            pollyEngine = enumValueOrDefault(prefs[KEY_POLLY_ENGINE], PollyEngine.NEURAL),
            offlineVoiceId = prefs[KEY_OFFLINE_VOICE_ID] ?: "",
            offlineVoiceLabel = prefs[KEY_OFFLINE_VOICE_LABEL] ?: "System Default Offline Voice",
            offlineSpeechRate = prefs[KEY_OFFLINE_RATE] ?: 1.0f,
            offlinePitch = prefs[KEY_OFFLINE_PITCH] ?: 1.0f,
            preferredOutputFormat = enumValueOrDefault(prefs[KEY_OUTPUT_FORMAT], AudiobookFormat.M4B)
        )
    }

    suspend fun updateReaderSettings(transform: (ReaderSettings) -> ReaderSettings) {
        context.voxDataStore.edit { prefs ->
            val current = ReaderSettings(
                fontSizeSp = prefs[KEY_FONT_SIZE] ?: 18f,
                fontFamily = enumValueOrDefault(prefs[KEY_FONT_FAMILY], ReaderFontKey.LITERATA),
                fontWeightLevel = prefs[KEY_FONT_WEIGHT] ?: 400,
                italicBody = prefs[KEY_ITALIC_BODY] ?: false,
                boldActiveSegment = prefs[KEY_BOLD_ACTIVE] ?: true,
                italicActiveSegment = prefs[KEY_ITALIC_ACTIVE] ?: false,
                lineHeightMultiplier = prefs[KEY_LINE_HEIGHT] ?: 1.6f,
                letterSpacingEm = prefs[KEY_LETTER_SPACING] ?: 0.0f,
                textAlign = enumValueOrDefault(prefs[KEY_TEXT_ALIGN], ReaderTextAlign.START),
                pagePositionMode = enumValueOrDefault(prefs[KEY_PAGE_POSITION], PagePositionMode.CENTERED),
                horizontalMarginDp = prefs[KEY_H_MARGIN] ?: 20,
                showPageNumbers = prefs[KEY_SHOW_PAGE_NUMBERS] ?: true,
                readerTheme = enumValueOrDefault(prefs[KEY_READER_THEME], ReaderThemeMode.PAPER_CREAM),
                highlightStyle = enumValueOrDefault(prefs[KEY_HIGHLIGHT_STYLE], HighlightStyle.AMBER_PILL)
            )
            val updated = transform(current)
            prefs[KEY_FONT_SIZE] = updated.fontSizeSp
            prefs[KEY_FONT_FAMILY] = updated.fontFamily.name
            prefs[KEY_FONT_WEIGHT] = updated.fontWeightLevel
            prefs[KEY_ITALIC_BODY] = updated.italicBody
            prefs[KEY_BOLD_ACTIVE] = updated.boldActiveSegment
            prefs[KEY_ITALIC_ACTIVE] = updated.italicActiveSegment
            prefs[KEY_LINE_HEIGHT] = updated.lineHeightMultiplier
            prefs[KEY_LETTER_SPACING] = updated.letterSpacingEm
            prefs[KEY_TEXT_ALIGN] = updated.textAlign.name
            prefs[KEY_PAGE_POSITION] = updated.pagePositionMode.name
            prefs[KEY_H_MARGIN] = updated.horizontalMarginDp
            prefs[KEY_SHOW_PAGE_NUMBERS] = updated.showPageNumbers
            prefs[KEY_READER_THEME] = updated.readerTheme.name
            prefs[KEY_HIGHLIGHT_STYLE] = updated.highlightStyle.name
        }
    }

    suspend fun updateVoicePreferences(transform: (VoicePreferences) -> VoicePreferences) {
        context.voxDataStore.edit { prefs ->
            val current = VoicePreferences(
                preferredProvider = enumValueOrDefault(prefs[KEY_PROVIDER], VoiceProvider.AMAZON_POLLY),
                autoOfflineFallback = prefs[KEY_AUTO_FALLBACK] ?: true,
                pollyVoiceId = prefs[KEY_POLLY_VOICE_ID] ?: "Joanna",
                pollyVoiceName = prefs[KEY_POLLY_VOICE_NAME] ?: "Joanna",
                pollyEngine = enumValueOrDefault(prefs[KEY_POLLY_ENGINE], PollyEngine.NEURAL),
                offlineVoiceId = prefs[KEY_OFFLINE_VOICE_ID] ?: "",
                offlineVoiceLabel = prefs[KEY_OFFLINE_VOICE_LABEL] ?: "System Default Offline Voice",
                offlineSpeechRate = prefs[KEY_OFFLINE_RATE] ?: 1.0f,
                offlinePitch = prefs[KEY_OFFLINE_PITCH] ?: 1.0f,
                preferredOutputFormat = enumValueOrDefault(prefs[KEY_OUTPUT_FORMAT], AudiobookFormat.M4B)
            )
            val updated = transform(current)
            prefs[KEY_PROVIDER] = updated.preferredProvider.name
            prefs[KEY_AUTO_FALLBACK] = updated.autoOfflineFallback
            prefs[KEY_POLLY_VOICE_ID] = updated.pollyVoiceId
            prefs[KEY_POLLY_VOICE_NAME] = updated.pollyVoiceName
            prefs[KEY_POLLY_ENGINE] = updated.pollyEngine.name
            prefs[KEY_OFFLINE_VOICE_ID] = updated.offlineVoiceId
            prefs[KEY_OFFLINE_VOICE_LABEL] = updated.offlineVoiceLabel
            prefs[KEY_OFFLINE_RATE] = updated.offlineSpeechRate
            prefs[KEY_OFFLINE_PITCH] = updated.offlinePitch
            prefs[KEY_OUTPUT_FORMAT] = updated.preferredOutputFormat.name
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T {
        if (raw.isNullOrBlank()) return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    companion object {
        private val KEY_FONT_SIZE = floatPreferencesKey("reader_font_size")
        private val KEY_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        private val KEY_FONT_WEIGHT = intPreferencesKey("reader_font_weight")
        private val KEY_ITALIC_BODY = booleanPreferencesKey("reader_italic_body")
        private val KEY_BOLD_ACTIVE = booleanPreferencesKey("reader_bold_active")
        private val KEY_ITALIC_ACTIVE = booleanPreferencesKey("reader_italic_active")
        private val KEY_LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        private val KEY_LETTER_SPACING = floatPreferencesKey("reader_letter_spacing")
        private val KEY_TEXT_ALIGN = stringPreferencesKey("reader_text_align")
        private val KEY_PAGE_POSITION = stringPreferencesKey("reader_page_position")
        private val KEY_H_MARGIN = intPreferencesKey("reader_h_margin")
        private val KEY_SHOW_PAGE_NUMBERS = booleanPreferencesKey("reader_show_page_numbers")
        private val KEY_READER_THEME = stringPreferencesKey("reader_theme")
        private val KEY_HIGHLIGHT_STYLE = stringPreferencesKey("reader_highlight_style")

        private val KEY_PROVIDER = stringPreferencesKey("voice_provider")
        private val KEY_AUTO_FALLBACK = booleanPreferencesKey("voice_auto_fallback")
        private val KEY_POLLY_VOICE_ID = stringPreferencesKey("voice_polly_id")
        private val KEY_POLLY_VOICE_NAME = stringPreferencesKey("voice_polly_name")
        private val KEY_POLLY_ENGINE = stringPreferencesKey("voice_polly_engine")
        private val KEY_OFFLINE_VOICE_ID = stringPreferencesKey("voice_offline_id")
        private val KEY_OFFLINE_VOICE_LABEL = stringPreferencesKey("voice_offline_label")
        private val KEY_OFFLINE_RATE = floatPreferencesKey("voice_offline_rate")
        private val KEY_OFFLINE_PITCH = floatPreferencesKey("voice_offline_pitch")
        private val KEY_OUTPUT_FORMAT = stringPreferencesKey("voice_output_format")
    }
}
