package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AudiobookFormat
import com.example.data.model.AudiobookSegment
import com.example.data.model.HighlightStyle
import com.example.data.model.PagePositionMode
import com.example.data.model.ReaderTextAlign
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bookId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenSettingsCog: () -> Unit,
    onOpenStudio: (String) -> Unit
) {
    val books by viewModel.libraryBooks.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == bookId }
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showQuickTypographySheet by remember { mutableStateOf(false) }
    var showPagesJumpSheet by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val segments = remember(book?.id, book?.segmentsJson) {
        book?.let { viewModel.decodeSegments(it) }.orEmpty()
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && book != null) {
            viewModel.exportAudiobookToUri(book, uri)
        }
    }

    LaunchedEffect(book?.id, book?.audioFilePath) {
        if (book != null) {
            viewModel.openBookInPlayer(book)
        }
    }

    LaunchedEffect(snackbarMsg) {
        if (!snackbarMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            viewModel.clearSnackbar()
        }
    }

    BackHandler {
        onBack()
    }

    val palette = readerSettings.readerTheme.toPalette()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    titleContentColor = palette.text,
                    navigationIconContentColor = palette.text,
                    actionIconContentColor = palette.text
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to library")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = book?.title ?: "Audiobook Player",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = palette.text
                        )
                        if (book != null) {
                            val fmt = AudiobookFormat.fromExtension(book.audioFormat).extension.uppercase()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.OfflinePin,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (book.hasIncludedAudio) {
                                        "Included $fmt · 0 Polly Allotment Used"
                                    } else {
                                        "No saved audio yet"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.accent,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showQuickTypographySheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("quick_typography_button")
                    ) {
                        Icon(
                            Icons.Rounded.FormatSize,
                            contentDescription = "Read-along font, size, bold, italic, and page position"
                        )
                    }
                    if (book?.hasIncludedAudio == true) {
                        IconButton(
                            onClick = {
                                val ext = book.audioFormat ?: "m4b"
                                val safeName = book.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(40)
                                createDocumentLauncher.launch("$safeName.$ext")
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("export_audiobook_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "Save audiobook file to device")
                        }
                    }
                    IconButton(
                        onClick = onOpenSettingsCog,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("player_settings_cog_button")
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Open Cog Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (book == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Audiobook not found.", color = palette.text)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(palette.background)
        ) {
            // Read-Along Synchronized Reader Viewport
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                val viewportHeightPx = with(LocalDensity.current) { maxHeight.toPx().roundToInt() }
                val listState = rememberLazyListState()
                val activeIndex = playerState.activeSegmentIndex.coerceIn(0, (segments.size - 1).coerceAtLeast(0))

                LaunchedEffect(activeIndex, readerSettings.pagePositionMode, viewportHeightPx) {
                    if (segments.isNotEmpty() && readerSettings.pagePositionMode != PagePositionMode.FREE_SCROLL) {
                        val scrollOffsetPx = when (readerSettings.pagePositionMode) {
                            PagePositionMode.TOP_THIRD -> -(viewportHeightPx * 0.14f).roundToInt()
                            PagePositionMode.CENTERED -> -(viewportHeightPx * 0.38f).roundToInt()
                            PagePositionMode.LOWER_THIRD -> -(viewportHeightPx * 0.62f).roundToInt()
                            PagePositionMode.FREE_SCROLL -> 0
                        }
                        runCatching {
                            listState.animateScrollToItem(index = activeIndex, scrollOffset = scrollOffsetPx)
                        }
                    }
                }

                val fontFamily = readerSettings.fontFamily.toFontFamily()
                val baseWeight = readerSettings.toBaseFontWeight()
                val baseStyle = if (readerSettings.italicBody) FontStyle.Italic else FontStyle.Normal
                val textAlign = when (readerSettings.textAlign) {
                    ReaderTextAlign.START -> TextAlign.Start
                    ReaderTextAlign.CENTER -> TextAlign.Center
                    ReaderTextAlign.JUSTIFY -> TextAlign.Justify
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .testTag("read_along_list"),
                    contentPadding = PaddingValues(
                        start = readerSettings.horizontalMarginDp.dp,
                        end = readerSettings.horizontalMarginDp.dp,
                        top = 18.dp,
                        bottom = 72.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(segments, key = { idx, _ -> idx }) { idx, seg ->
                        val isActive = idx == activeIndex
                        val segWeight = when {
                            isActive && readerSettings.boldActiveSegment -> FontWeight.Bold
                            seg.isHeading -> FontWeight.Bold
                            else -> baseWeight
                        }
                        val segStyle = if (isActive && readerSettings.italicActiveSegment) {
                            FontStyle.Italic
                        } else {
                            baseStyle
                        }
                        val textColor = when {
                            isActive -> palette.text
                            readerSettings.highlightStyle == HighlightStyle.INK_FOCUS -> palette.mutedText
                            else -> palette.text.copy(alpha = 0.88f)
                        }
                        val bgColor = if (isActive && readerSettings.highlightStyle == HighlightStyle.AMBER_PILL) {
                            palette.highlightBg
                        } else {
                            Color.Transparent
                        }
                        val decoration = if (isActive && readerSettings.highlightStyle == HighlightStyle.WARM_UNDERLINE) {
                            TextDecoration.Underline
                        } else {
                            TextDecoration.None
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(bgColor)
                                .clickable {
                                    if (book.hasIncludedAudio) {
                                        viewModel.jumpToSegment(seg)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            if (seg.isHeading) {
                                Text(
                                    text = seg.text,
                                    fontFamily = fontFamily,
                                    fontSize = (readerSettings.fontSizeSp + 4f).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = segStyle,
                                    color = palette.accent,
                                    textAlign = textAlign,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = seg.text,
                                    fontFamily = fontFamily,
                                    fontSize = readerSettings.fontSizeSp.sp,
                                    lineHeight = (readerSettings.fontSizeSp * readerSettings.lineHeightMultiplier).sp,
                                    letterSpacing = readerSettings.letterSpacingEm.sp,
                                    fontWeight = segWeight,
                                    fontStyle = segStyle,
                                    color = textColor,
                                    textDecoration = decoration,
                                    textAlign = textAlign,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                if (readerSettings.showPageNumbers && segments.isNotEmpty()) {
                    val currentSeg = segments.getOrNull(activeIndex)
                    val curPage = currentSeg?.pageNumber ?: 1
                    val pct = if (playerState.durationMs > 0L) {
                        ((playerState.currentPositionMs.toFloat() / playerState.durationMs.toFloat()) * 100f)
                            .roundToInt().coerceIn(0, 100)
                    } else 0
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp),
                        shape = CircleShape,
                        color = palette.background.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = "Page $curPage of ${book.pageCount} · $pct% · ${readerSettings.pagePositionMode.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Bottom Included-Audio Transport Dock
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!book.hasIncludedAudio) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Generate this document into an audiobook to enable 0-allotment offline playback.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { onOpenStudio(book.id) }) {
                                Text("Open Studio")
                            }
                        }
                    } else {
                        val dur = playerState.durationMs.coerceAtLeast(1L)
                        val pos = playerState.currentPositionMs.coerceIn(0L, dur)
                        Slider(
                            value = pos.toFloat(),
                            onValueChange = { viewModel.seekTo(it.toLong()) },
                            valueRange = 0f..dur.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_seek_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDurationMs(pos),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = book.voiceUsedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Text(
                                text = formatDurationMs(dur),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speed Selector
                            Box {
                                TextButton(
                                    onClick = { showSpeedMenu = true },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(
                                        text = "${playerState.playbackSpeed}x",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { spd ->
                                        DropdownMenuItem(
                                            text = { Text("${spd}x") },
                                            onClick = {
                                                viewModel.setPlaybackSpeed(spd)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.skipBy(-15_000L) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Replay10, contentDescription = "Rewind 15 seconds")
                            }

                            FilledIconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier
                                    .size(58.dp)
                                    .testTag("player_play_pause_button"),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.skipBy(15_000L) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Forward10, contentDescription = "Forward 15 seconds")
                            }

                            IconButton(
                                onClick = { showPagesJumpSheet = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.ListAlt, contentDescription = "Jump to page or section")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showQuickTypographySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickTypographySheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Read-Along Display Settings", style = MaterialTheme.typography.titleLarge)
                ReaderTypographyControls(
                    settings = readerSettings,
                    onUpdate = { transform -> viewModel.updateReaderSettings(transform) },
                    showLivePreview = false
                )
            }
        }
    }

    if (showPagesJumpSheet) {
        val pageJumpList = remember(segments) {
            segments.groupBy { it.pageNumber }
                .entries
                .sortedBy { it.key }
                .map { (page, list) -> page to list.first() }
        }
        ModalBottomSheet(onDismissRequest = { showPagesJumpSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Jump to Page", style = MaterialTheme.typography.titleLarge)
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(pageJumpList) { _, (pageNum, firstSeg) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.jumpToSegment(firstSeg)
                                    showPagesJumpSheet = false
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Page $pageNum", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = firstSeg.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = formatDurationMs(firstSeg.startMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
