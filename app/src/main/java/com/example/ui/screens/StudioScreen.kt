package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AudiobookFormat
import com.example.data.model.SynthesisState
import com.example.data.model.VoiceProvider
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    bookId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenPlayer: (String) -> Unit,
    onOpenSettingsCog: () -> Unit
) {
    val books by viewModel.libraryBooks.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == bookId }
    val voicePrefs by viewModel.voicePreferences.collectAsStateWithLifecycle()
    val creds by viewModel.awsCredentials.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val synthesisState by viewModel.synthesisState.collectAsStateWithLifecycle()

    var selectedProvider by remember(voicePrefs.preferredProvider, isOnline, creds.isConfigured) {
        mutableStateOf(
            if (voicePrefs.preferredProvider == VoiceProvider.AMAZON_POLLY && isOnline && creds.isConfigured) {
                VoiceProvider.AMAZON_POLLY
            } else if (voicePrefs.preferredProvider == VoiceProvider.OFFLINE_DEVICE) {
                VoiceProvider.OFFLINE_DEVICE
            } else if (!isOnline || !creds.isConfigured) {
                VoiceProvider.OFFLINE_DEVICE
            } else {
                voicePrefs.preferredProvider
            }
        )
    }
    var selectedFormat by remember(voicePrefs.preferredOutputFormat) {
        mutableStateOf(voicePrefs.preferredOutputFormat)
    }
    var editableTitle by remember(book?.id) {
        mutableStateOf(book?.title.orEmpty())
    }

    LaunchedEffect(book?.title) {
        if (editableTitle.isBlank() && !book?.title.isNullOrBlank()) {
            editableTitle = book!!.title
        }
    }

    LaunchedEffect(synthesisState) {
        val state = synthesisState
        if (state is SynthesisState.Completed && state.audiobookId == bookId) {
            viewModel.resetSynthesisState()
            onOpenPlayer(bookId)
        }
    }

    BackHandler {
        if (synthesisState is SynthesisState.InProgress) {
            viewModel.cancelSynthesis()
        }
        onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to library")
                    }
                },
                title = { Text("Audiobook Studio") },
                actions = {
                    IconButton(
                        onClick = onOpenSettingsCog,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("studio_settings_cog_button")
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Open voice and IAM settings")
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
                Text("Document not found.")
            }
            return@Scaffold
        }

        val isSynthesizing = synthesisState is SynthesisState.InProgress

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title & Document Stats
                item {
                    OutlinedTextField(
                        value = editableTitle,
                        onValueChange = {
                            editableTitle = it
                            viewModel.updateTitle(book.id, it)
                        },
                        label = { Text("Audiobook Title") },
                        singleLine = true,
                        enabled = !isSynthesizing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_title_input")
                    )
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${book.sourceType} · ${book.sourceFileName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${book.pageCount} page(s) · ${formatCount(book.characterCount)} characters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (book.hasIncludedAudio) {
                                OutlinedButton(
                                    onClick = { onOpenPlayer(book.id) },
                                    modifier = Modifier.testTag("studio_open_existing_player_button")
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play Saved")
                                }
                            }
                        }
                    }
                }

                // Voice Engine Selection
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("1. Choose Voice Engine", style = MaterialTheme.typography.titleMedium)

                        VoiceEngineChoiceCard(
                            selected = selectedProvider == VoiceProvider.AMAZON_POLLY,
                            icon = if (isOnline) Icons.Rounded.Cloud else Icons.Rounded.CloudOff,
                            title = "Amazon Polly (${voicePrefs.pollyVoiceName} · ${voicePrefs.pollyEngine.label})",
                            subtitle = when {
                                !creds.isConfigured -> "IAM credentials not set yet · Tap Cog to configure"
                                !isOnline -> "Currently offline · Will use Offline Fallback if enabled"
                                else -> "Uses ~${formatCount(book.characterCount)} chars once; saved replays use 0 allotment"
                            },
                            badge = if (creds.isConfigured && isOnline) "IAM Ready" else "Setup / Offline",
                            onClick = {
                                if (!isSynthesizing) {
                                    selectedProvider = VoiceProvider.AMAZON_POLLY
                                }
                            },
                            actionLabel = if (!creds.isConfigured) "Add IAM Keys" else "Change Voice",
                            onActionClick = onOpenSettingsCog,
                            testTag = "studio_engine_polly"
                        )

                        VoiceEngineChoiceCard(
                            selected = selectedProvider == VoiceProvider.OFFLINE_DEVICE,
                            icon = Icons.Rounded.RecordVoiceOver,
                            title = "Offline Device Voice (${voicePrefs.offlineVoiceLabel})",
                            subtitle = "100% offline · 0 internet · 0 Amazon Polly allotment",
                            badge = "Offline",
                            onClick = {
                                if (!isSynthesizing) {
                                    selectedProvider = VoiceProvider.OFFLINE_DEVICE
                                }
                            },
                            actionLabel = "Select Voice",
                            onActionClick = onOpenSettingsCog,
                            testTag = "studio_engine_offline"
                        )
                    }
                }

                // Output Audiobook File Format
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("2. Saved Audiobook File Format", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudiobookFormat.entries.forEach { format ->
                                val offlineDisabled = selectedProvider == VoiceProvider.OFFLINE_DEVICE && !format.supportsOfflineEncoding
                                FilterChip(
                                    selected = selectedFormat == format,
                                    enabled = !isSynthesizing && !offlineDisabled,
                                    onClick = {
                                        selectedFormat = format
                                        viewModel.updateVoicePreferences { it.copy(preferredOutputFormat = format) }
                                    },
                                    label = { Text(format.displayName) },
                                    modifier = Modifier.testTag("format_chip_${format.extension}")
                                )
                            }
                        }
                        val activeFormat = if (selectedProvider == VoiceProvider.OFFLINE_DEVICE && !selectedFormat.supportsOfflineEncoding) {
                            AudiobookFormat.M4B
                        } else {
                            selectedFormat
                        }
                        Text(
                            text = "${activeFormat.displayName} (.${activeFormat.extension}): ${activeFormat.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Synthesis Progress or Error Banner
                item {
                    when (val s = synthesisState) {
                        is SynthesisState.InProgress -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = s.statusMessage,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    val progress = if (s.totalChunks > 0) {
                                        s.currentChunk.toFloat() / s.totalChunks.toFloat()
                                    } else 0f
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Chunk ${s.currentChunk} of ${s.totalChunks}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        TextButton(onClick = { viewModel.cancelSynthesis() }) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }
                        }
                        is SynthesisState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = s.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        if (!creds.isConfigured) {
                                            TextButton(onClick = onOpenSettingsCog) {
                                                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Open IAM Settings")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                // Generate Button
                item {
                    Button(
                        onClick = {
                            viewModel.generateAudiobook(
                                bookId = book.id,
                                requestedProvider = selectedProvider,
                                requestedFormat = selectedFormat
                            )
                        },
                        enabled = !isSynthesizing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("generate_audiobook_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val btnLabel = if (book.hasIncludedAudio) {
                            "Re-Generate & Save Audiobook"
                        } else {
                            "Generate & Save Audiobook"
                        }
                        Text(btnLabel, style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Extracted Text Preview
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Extracted Text Preview", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = book.fullText.take(800) + if (book.fullText.length > 800) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceEngineChoiceCard(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    actionLabel: String,
    onActionClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                TextButton(onClick = onActionClick) {
                    Text(actionLabel)
                }
            }
        }
    }
}
