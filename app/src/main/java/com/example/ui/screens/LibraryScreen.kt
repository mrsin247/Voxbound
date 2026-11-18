package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.AudiobookEntity
import com.example.data.model.AudiobookFormat
import com.example.ui.MainViewModel
import com.example.ui.PollyVerifyStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onOpenStudio: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onOpenSettingsCog: () -> Unit
) {
    val books by viewModel.libraryBooks.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val creds by viewModel.awsCredentials.collectAsStateWithLifecycle()
    val verifyStatus by viewModel.pollyVerifyStatus.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pendingExportBook by remember { mutableStateOf<AudiobookEntity?>(null) }
    var pendingDeleteBook by remember { mutableStateOf<AudiobookEntity?>(null) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importDocumentUri(uri) { bookId ->
                onOpenStudio(bookId)
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val book = pendingExportBook
        pendingExportBook = null
        if (uri != null && book != null) {
            viewModel.exportAudiobookToUri(book, uri)
        }
    }

    LaunchedEffect(snackbarMsg) {
        if (!snackbarMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoStories,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "VoxBound",
                            style = MaterialTheme.typography.titleLarge
                        )
                        val statusLabel = when {
                            !isOnline -> "Offline · Local Voices Ready"
                            verifyStatus is PollyVerifyStatus.Verified || creds.isConfigured -> "Polly & Offline Ready"
                            else -> "Offline Ready · Add IAM in Cog"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isOnline) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = if (isOnline) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettingsCog,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("settings_cog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Voice, IAM credentials, and read-along settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ImportStudioHeroCard(
                        isImporting = isImporting,
                        onUploadClick = {
                            openDocumentLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "text/plain",
                                    "text/markdown",
                                    "text/html",
                                    "application/epub+zip",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/rtf",
                                    "*/*"
                                )
                            )
                        },
                        onPasteClick = { showPasteDialog = true }
                    )
                }

                if (books.isEmpty()) {
                    item {
                        EmptyShelfCard(
                            onLoadSample = {
                                viewModel.loadClassicStorySample { id -> onOpenStudio(id) }
                            }
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your Audiobooks (${books.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Saved audio uses 0 allotment",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    items(books, key = { it.id }) { book ->
                        AudiobookLibraryCard(
                            book = book,
                            onPlay = {
                                if (book.hasIncludedAudio) {
                                    onOpenPlayer(book.id)
                                } else {
                                    onOpenStudio(book.id)
                                }
                            },
                            onOpenStudio = { onOpenStudio(book.id) },
                            onExport = {
                                pendingExportBook = book
                                val ext = book.audioFormat ?: "m4b"
                                val safeName = book.title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(40)
                                createDocumentLauncher.launch("$safeName.$ext")
                            },
                            onDelete = { pendingDeleteBook = book }
                        )
                    }
                }
            }
        }
    }

    if (showPasteDialog) {
        PasteTextDialog(
            onDismiss = { showPasteDialog = false },
            onConfirm = { title, text ->
                showPasteDialog = false
                viewModel.importPastedText(title, text) { id -> onOpenStudio(id) }
            }
        )
    }

    pendingDeleteBook?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteBook = null },
            title = { Text("Delete Audiobook?") },
            text = {
                Text("Remove \"${target.title}\" and its saved local audio file from your library?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAudiobook(target)
                        pendingDeleteBook = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBook = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ImportStudioHeroCard(
    isImporting: Boolean,
    onUploadClick: () -> Unit,
    onPasteClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Turn Any File Into an Audiobook",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "PDF, TXT, EPUB, DOCX, MD, HTML · Polly or Offline Voices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isImporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Extracting text and chapters from file...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onUploadClick,
                        enabled = !isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("import_document_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import File")
                    }

                    FilledTonalButton(
                        onClick = onPasteClick,
                        enabled = !isImporting,
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("paste_text_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.EditNote, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Paste")
                    }
                }
            }
        }
    }
}

@Composable
private fun AudiobookLibraryCard(
    book: AudiobookEntity,
    onPlay: () -> Unit,
    onOpenStudio: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = if (book.durationMs > 0L) {
        (book.lastPositionMs.toFloat() / book.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audiobook_card_${book.id}")
            .clickable { onPlay() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_audiobook_cover),
                        contentDescription = "Audiobook cover for ${book.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(5.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = book.sourceType,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (book.hasIncludedAudio) {
                            "${formatDurationMs(book.durationMs)} · ${book.voiceUsedLabel}"
                        } else {
                            "${formatCount(book.characterCount)} chars · ${book.pageCount} page(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (book.hasIncludedAudio) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        val badgeText = if (book.hasIncludedAudio) {
                            val fmt = AudiobookFormat.fromExtension(book.audioFormat).extension.uppercase()
                            "Included $fmt (${formatFileSize(book.audioFileSizeBytes)}) · 0 Polly Used"
                        } else {
                            "Draft Text · Ready to Generate Audio"
                        }
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (book.hasIncludedAudio) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (book.hasIncludedAudio) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (book.hasIncludedAudio) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("play_book_button_${book.id}"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (book.lastPositionMs > 2000L) "Resume" else "Listen Offline")
                    }
                    OutlinedButton(
                        onClick = onOpenStudio,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = "Re-generate audio", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onExport,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Save audio file to device",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Button(
                        onClick = onOpenStudio,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create Audiobook")
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${book.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyShelfCard(onLoadSample: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_library_empty),
                contentDescription = "Empty audiobook shelf illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Text(
                text = "Your Audiobook Shelf is Ready",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Import any PDF, EPUB, DOCX, or text file above. Generate it once with your Amazon Polly IAM voices or offline device voices, then replay forever without touching your allotment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onLoadSample,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("load_sample_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.AutoStories, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Try Public-Domain Short Story Sample")
            }
        }
    }
}

@Composable
private fun PasteTextDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste Text to Convert") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Audiobook Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Paste article, story, or notes") },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, content) },
                enabled = content.isNotBlank()
            ) {
                Text("Continue to Studio")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
