package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HighlightStyle
import com.example.data.model.PagePositionMode
import com.example.data.model.ReaderFontKey
import com.example.data.model.ReaderSettings
import com.example.data.model.ReaderTextAlign
import com.example.data.model.ReaderThemeMode
import com.example.ui.theme.ReaderCreamBg
import com.example.ui.theme.ReaderCreamHighlight
import com.example.ui.theme.ReaderCreamText
import com.example.ui.theme.ReaderMidnightBg
import com.example.ui.theme.ReaderMidnightHighlight
import com.example.ui.theme.ReaderMidnightText
import com.example.ui.theme.ReaderSepiaBg
import com.example.ui.theme.ReaderSepiaHighlight
import com.example.ui.theme.ReaderSepiaText
import com.example.ui.theme.ReaderWhiteBg
import com.example.ui.theme.ReaderWhiteHighlight
import com.example.ui.theme.ReaderWhiteText
import kotlin.math.roundToInt

data class ReaderPalette(
    val background: Color,
    val text: Color,
    val mutedText: Color,
    val highlightBg: Color,
    val accent: Color
)

fun ReaderThemeMode.toPalette(): ReaderPalette = when (this) {
    ReaderThemeMode.PAPER_CREAM -> ReaderPalette(
        background = ReaderCreamBg,
        text = ReaderCreamText,
        mutedText = ReaderCreamText.copy(alpha = 0.48f),
        highlightBg = ReaderCreamHighlight,
        accent = Color(0xFFB85D19)
    )
    ReaderThemeMode.WARM_SEPIA -> ReaderPalette(
        background = ReaderSepiaBg,
        text = ReaderSepiaText,
        mutedText = ReaderSepiaText.copy(alpha = 0.48f),
        highlightBg = ReaderSepiaHighlight,
        accent = Color(0xFF964710)
    )
    ReaderThemeMode.CRISP_WHITE -> ReaderPalette(
        background = ReaderWhiteBg,
        text = ReaderWhiteText,
        mutedText = ReaderWhiteText.copy(alpha = 0.45f),
        highlightBg = ReaderWhiteHighlight,
        accent = Color(0xFF1D5FA8)
    )
    ReaderThemeMode.MIDNIGHT_SLATE -> ReaderPalette(
        background = ReaderMidnightBg,
        text = ReaderMidnightText,
        mutedText = ReaderMidnightText.copy(alpha = 0.45f),
        highlightBg = ReaderMidnightHighlight,
        accent = Color(0xFFF2A65A)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderTypographyControls(
    settings: ReaderSettings,
    onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit,
    showLivePreview: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showLivePreview) {
            ReaderStyleLivePreview(settings = settings)
        }

        // Paper Theme Swatches
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Reading Paper Theme",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReaderThemeMode.entries.forEach { mode ->
                    val palette = mode.toPalette()
                    val selected = settings.readerTheme == mode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("theme_${mode.name.lowercase()}")
                            .clickable { onUpdate { it.copy(readerTheme = mode) } },
                        shape = RoundedCornerShape(14.dp),
                        color = palette.background,
                        border = BorderStroke(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = mode.label.substringBefore(' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.text,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Font Family
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Font Family",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReaderFontKey.entries.forEach { fontKey ->
                    FilterChip(
                        selected = settings.fontFamily == fontKey,
                        onClick = { onUpdate { it.copy(fontFamily = fontKey) } },
                        label = {
                            Text(
                                text = fontKey.label,
                                fontFamily = fontKey.toFontFamily()
                            )
                        },
                        modifier = Modifier.testTag("font_chip_${fontKey.name.lowercase()}")
                    )
                }
            }
        }

        // Text Size Slider
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Text Size", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${settings.fontSizeSp.roundToInt()} sp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.fontSizeSp,
                onValueChange = { v -> onUpdate { it.copy(fontSizeSp = v) } },
                valueRange = 13f..32f,
                steps = 18,
                modifier = Modifier.testTag("reader_font_size_slider")
            )
        }

        // Font Weight (Boldness) & Italic Style
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Font Weight & Emphasis", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    400 to "Regular",
                    500 to "Medium",
                    600 to "SemiBold",
                    700 to "Bold"
                ).forEach { (weightVal, label) ->
                    FilterChip(
                        selected = settings.fontWeightLevel == weightVal,
                        onClick = { onUpdate { it.copy(fontWeightLevel = weightVal) } },
                        label = { Text(label) },
                        modifier = Modifier.testTag("weight_chip_$weightVal")
                    )
                }
            }

            SettingToggleRow(
                icon = Icons.Rounded.FormatItalic,
                title = "Italicize Body Text",
                subtitle = "Apply oblique/italic styling to all read-along text",
                checked = settings.italicBody,
                testTag = "italic_body_switch",
                onCheckedChange = { checked -> onUpdate { it.copy(italicBody = checked) } }
            )

            SettingToggleRow(
                icon = Icons.Rounded.FormatBold,
                title = "Bold Active Spoken Sentence",
                subtitle = "Make the currently spoken line bold as the voice reads",
                checked = settings.boldActiveSegment,
                testTag = "bold_active_switch",
                onCheckedChange = { checked -> onUpdate { it.copy(boldActiveSegment = checked) } }
            )

            SettingToggleRow(
                icon = Icons.Rounded.FormatItalic,
                title = "Italicize Active Spoken Sentence",
                subtitle = "Emphasize the active line in italics during playback",
                checked = settings.italicActiveSegment,
                testTag = "italic_active_switch",
                onCheckedChange = { checked -> onUpdate { it.copy(italicActiveSegment = checked) } }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Page Position & Read-Along Tracking
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Page Position & Auto-Scroll", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Controls where the active spoken line sits on the page as the voice reads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PagePositionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.pagePositionMode == mode,
                        onClick = { onUpdate { it.copy(pagePositionMode = mode) } },
                        label = { Text(mode.label) },
                        modifier = Modifier.testTag("page_pos_${mode.name.lowercase()}")
                    )
                }
            }
        }

        // Alignment & Highlight Style
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Text Alignment & Highlight", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReaderTextAlign.entries.forEach { align ->
                    val icon = when (align) {
                        ReaderTextAlign.START -> Icons.Rounded.FormatAlignLeft
                        ReaderTextAlign.CENTER -> Icons.Rounded.FormatAlignCenter
                        ReaderTextAlign.JUSTIFY -> Icons.Rounded.FormatAlignJustify
                    }
                    FilterChip(
                        selected = settings.textAlign == align,
                        onClick = { onUpdate { it.copy(textAlign = align) } },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(align.label) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("align_${align.name.lowercase()}")
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HighlightStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.highlightStyle == style,
                        onClick = { onUpdate { it.copy(highlightStyle = style) } },
                        label = { Text(style.label) },
                        modifier = Modifier.testTag("highlight_${style.name.lowercase()}")
                    )
                }
            }
        }

        // Line Spacing & Page Margins
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Line Spacing", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = String.format("%.2fx", settings.lineHeightMultiplier),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.lineHeightMultiplier,
                onValueChange = { v -> onUpdate { it.copy(lineHeightMultiplier = v) } },
                valueRange = 1.25f..2.1f,
                modifier = Modifier.testTag("line_spacing_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Page Side Margins", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${settings.horizontalMarginDp} dp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = settings.horizontalMarginDp.toFloat(),
                onValueChange = { v -> onUpdate { it.copy(horizontalMarginDp = v.roundToInt()) } },
                valueRange = 10f..40f,
                modifier = Modifier.testTag("page_margin_slider")
            )
        }

        SettingToggleRow(
            icon = null,
            title = "Show Page Number & Progress Pill",
            subtitle = "Display current page number and percentage overlay while reading",
            checked = settings.showPageNumbers,
            testTag = "show_page_numbers_switch",
            onCheckedChange = { checked -> onUpdate { it.copy(showPageNumbers = checked) } }
        )

        OutlinedButton(
            onClick = { onUpdate { ReaderSettings() } },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reset_typography_button")
        ) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Reset Read-Along Defaults")
        }
    }
}

@Composable
fun ReaderStyleLivePreview(settings: ReaderSettings) {
    val palette = settings.readerTheme.toPalette()
    val fontFamily = settings.fontFamily.toFontFamily()
    val baseWeight = settings.toBaseFontWeight()
    val baseStyle = if (settings.italicBody) FontStyle.Italic else FontStyle.Normal
    val activeWeight = if (settings.boldActiveSegment) FontWeight.Bold else baseWeight
    val activeStyle = if (settings.italicActiveSegment || settings.italicBody) FontStyle.Italic else FontStyle.Normal
    val align = when (settings.textAlign) {
        ReaderTextAlign.START -> TextAlign.Start
        ReaderTextAlign.CENTER -> TextAlign.Center
        ReaderTextAlign.JUSTIFY -> TextAlign.Justify
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = palette.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = settings.horizontalMarginDp.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (settings.showPageNumbers) {
                Text(
                    text = "LIVE PREVIEW · PAGE 1 · ${settings.pagePositionMode.label.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent
                )
            }
            val annotated = buildAnnotatedString {
                val inactiveColor = if (settings.highlightStyle == HighlightStyle.INK_FOCUS) {
                    palette.mutedText
                } else {
                    palette.text.copy(alpha = 0.85f)
                }
                withStyle(
                    SpanStyle(
                        color = inactiveColor,
                        fontWeight = baseWeight,
                        fontStyle = baseStyle
                    )
                ) {
                    append("The evening lamp cast a warm amber glow across the desk. ")
                }
                withStyle(
                    SpanStyle(
                        color = palette.text,
                        background = if (settings.highlightStyle == HighlightStyle.AMBER_PILL) palette.highlightBg else Color.Transparent,
                        textDecoration = if (settings.highlightStyle == HighlightStyle.WARM_UNDERLINE) TextDecoration.Underline else TextDecoration.None,
                        fontWeight = activeWeight,
                        fontStyle = activeStyle
                    )
                ) {
                    append("This active sentence tracks right along with the narrator's voice.")
                }
            }
            Text(
                text = annotated,
                fontFamily = fontFamily,
                fontSize = settings.fontSizeSp.sp,
                lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                letterSpacing = settings.letterSpacingEm.sp,
                textAlign = align,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SettingToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
