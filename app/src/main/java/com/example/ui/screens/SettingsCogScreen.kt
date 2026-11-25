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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AwsCredentials
import com.example.data.model.PollyEngine
import com.example.data.model.VoiceProvider
import com.example.data.network.AmazonPollyClient
import com.example.ui.MainViewModel
import com.example.ui.PollyVerifyStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsCogScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val creds by viewModel.awsCredentials.collectAsStateWithLifecycle()
    val voicePrefs by viewModel.voicePreferences.collectAsStateWithLifecycle()
    val pollyVoices by viewModel.pollyVoices.collectAsStateWithLifecycle()
    val verifyStatus by viewModel.pollyVerifyStatus.collectAsStateWithLifecycle()
    val offlineVoices by viewModel.offlineVoices.collectAsStateWithLifecycle()
    val offlineStatus by viewModel.offlineEngineStatus.collectAsStateWithLifecycle()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Voices & IAM, 1 = Read-Along Style

    var accessKeyInput by remember(creds.accessKeyId) { mutableStateOf(creds.accessKeyId) }
    var secretKeyInput by remember(creds.secretAccessKey) { mutableStateOf(creds.secretAccessKey) }
    var sessionTokenInput by remember(creds.sessionToken) { mutableStateOf(creds.sessionToken) }
    var selectedRegion by remember(creds.region) { mutableStateOf(creds.region.ifBlank { "us-east-1" }) }
    var showSecretKey by remember { mutableStateOf(false) }
    var showSessionToken by remember { mutableStateOf(false) }
    var regionDropdownExpanded by remember { mutableStateOf(false) }
    var voiceSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(snackbarMsg) {
        if (!snackbarMsg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMsg!!)
            viewModel.clearSnackbar()
        }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding()) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings & Voices") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("settings_tab_voices")
                ) {
                    Text("Voices & IAM")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Rounded.FormatSize, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("settings_tab_typography")
                ) {
                    Text("Read-Along Style")
                }
            }

            if (selectedTab == 0) {
                val filteredPollyVoices = remember(pollyVoices, voiceSearchQuery) {
                    val q = voiceSearchQuery.trim()
                    val list = if (q.isBlank()) {
                        pollyVoices
                    } else {
                        pollyVoices.filter {
                            it.name.contains(q, ignoreCase = true) ||
                                it.languageName.contains(q, ignoreCase = true) ||
                                it.gender.contains(q, ignoreCase = true)
                        }
                    }
                    list.take(36)
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Engine Mode & Auto Fallback
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Default Narration Engine", style = MaterialTheme.typography.titleMedium)
                                // FlowRow (not Row): three chips no longer fit on one
                                // phone-width line, and a plain Row clips the third
                                // one off the right edge with no way to reach it.
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VoiceProvider.entries.forEach { provider ->
                                        FilterChip(
                                            selected = voicePrefs.preferredProvider == provider,
                                            onClick = {
                                                viewModel.updateVoicePreferences { it.copy(preferredProvider = provider) }
                                            },
                                            label = { Text(provider.displayName) },
                                            modifier = Modifier.testTag("default_provider_${provider.name.lowercase()}")
                                        )
                                    }
                                }
                                SettingToggleRow(
                                    icon = Icons.Rounded.OfflineBolt,
                                    title = "Automatic Offline Fallback",
                                    subtitle = if (isOnline) {
                                        "Online now · Switches to local offline voices automatically if signal drops"
                                    } else {
                                        "Offline detected · Local offline voices active"
                                    },
                                    checked = voicePrefs.autoOfflineFallback,
                                    testTag = "auto_offline_fallback_switch",
                                    onCheckedChange = { enabled ->
                                        viewModel.updateVoicePreferences { it.copy(autoOfflineFallback = enabled) }
                                    }
                                )
                            }
                        }
                    }

                    // AWS IAM Credentials Section
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text("Amazon Polly IAM Credentials", style = MaterialTheme.typography.titleMedium)
                                    }
                                    if (creds.isConfigured) {
                                        IconButton(onClick = {
                                            viewModel.clearPollyCredentials()
                                            accessKeyInput = ""
                                            secretKeyInput = ""
                                            sessionTokenInput = ""
                                        }) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear IAM credentials")
                                        }
                                    }
                                }

                                Text(
                                    text = "Encrypted on-device via Android Keystore AES-256. Requires an IAM user with AmazonPollyReadOnlyAccess or AmazonPollyFullAccess.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                ExposedDropdownMenuBox(
                                    expanded = regionDropdownExpanded,
                                    onExpandedChange = { regionDropdownExpanded = it }
                                ) {
                                    val regionDisplay = AmazonPollyClient.SUPPORTED_REGIONS
                                        .firstOrNull { it.first == selectedRegion }
                                        ?.let { "${it.first} — ${it.second}" }
                                        ?: selectedRegion
                                    OutlinedTextField(
                                        value = regionDisplay,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("AWS Region") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionDropdownExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = regionDropdownExpanded,
                                        onDismissRequest = { regionDropdownExpanded = false }
                                    ) {
                                        AmazonPollyClient.SUPPORTED_REGIONS.forEach { (code, name) ->
                                            DropdownMenuItem(
                                                text = { Text("$code — $name") },
                                                onClick = {
                                                    selectedRegion = code
                                                    regionDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = accessKeyInput,
                                    onValueChange = { accessKeyInput = it },
                                    label = { Text("AWS IAM Access Key ID") },
                                    placeholder = { Text("AKIA...") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("iam_access_key_input")
                                )

                                OutlinedTextField(
                                    value = secretKeyInput,
                                    onValueChange = { secretKeyInput = it },
                                    label = { Text("AWS IAM Secret Access Key") },
                                    singleLine = true,
                                    visualTransformation = if (showSecretKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showSecretKey = !showSecretKey }) {
                                            Icon(
                                                imageVector = if (showSecretKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = if (showSecretKey) "Hide secret key" else "Show secret key"
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("iam_secret_key_input")
                                )

                                TextButton(onClick = { showSessionToken = !showSessionToken }) {
                                    Text(if (showSessionToken) "Hide Optional STS Session Token" else "+ Add Optional STS Session Token")
                                }

                                if (showSessionToken || sessionTokenInput.isNotBlank()) {
                                    OutlinedTextField(
                                        value = sessionTokenInput,
                                        onValueChange = { sessionTokenInput = it },
                                        label = { Text("STS Session Token (Optional)") },
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Button(
                                    onClick = {
                                        viewModel.saveAndVerifyPollyCredentials(
                                            AwsCredentials(
                                                accessKeyId = accessKeyInput,
                                                secretAccessKey = secretKeyInput,
                                                sessionToken = sessionTokenInput,
                                                region = selectedRegion
                                            )
                                        )
                                    },
                                    enabled = verifyStatus !is PollyVerifyStatus.Verifying,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("verify_polly_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    if (verifyStatus is PollyVerifyStatus.Verifying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Verifying IAM Credentials...")
                                    } else {
                                        Text("Save & Verify Polly Voices")
                                    }
                                }

                                when (val st = verifyStatus) {
                                    is PollyVerifyStatus.Verified -> {
                                        Text(
                                            text = "Verified: ${st.voiceCount} Amazon Polly voices synced in $selectedRegion.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    is PollyVerifyStatus.Failed -> {
                                        Text(
                                            text = st.errorMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }

                    // Amazon Polly Voice & Engine Picker
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Amazon Polly Voice & Engine", style = MaterialTheme.typography.titleMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PollyEngine.entries.forEach { eng ->
                                    FilterChip(
                                        selected = voicePrefs.pollyEngine == eng,
                                        onClick = {
                                            viewModel.updateVoicePreferences { it.copy(pollyEngine = eng) }
                                        },
                                        label = { Text(eng.label) },
                                        modifier = Modifier.testTag("polly_engine_${eng.apiValue}")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = voiceSearchQuery,
                                onValueChange = { voiceSearchQuery = it },
                                label = { Text("Search Polly voices (e.g. Joanna, British, Matthew)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    items(filteredPollyVoices, key = { "polly_${it.id}_${it.languageCode}" }) { voice ->
                        val isSelected = voicePrefs.pollyVoiceId == voice.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateVoicePreferences {
                                        it.copy(pollyVoiceId = voice.id, pollyVoiceName = voice.name)
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${voice.name} · ${voice.gender}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${voice.languageName} (${voice.supportedEngines.joinToString(", ")})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected Polly Voice",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.previewPollyVoice(voice, voicePrefs.pollyEngine) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Rounded.VolumeUp,
                                            contentDescription = "Preview ${voice.name}"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Offline Voices Section
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Offline Fallback Voices",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = offlineStatus,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.refreshOfflineVoices() }) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh offline voices")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Offline Speed", style = MaterialTheme.typography.labelLarge)
                                    Text("${String.format("%.2fx", voicePrefs.offlineSpeechRate)}", color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = voicePrefs.offlineSpeechRate,
                                    onValueChange = { r ->
                                        viewModel.updateVoicePreferences { it.copy(offlineSpeechRate = r) }
                                    },
                                    valueRange = 0.7f..1.8f
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Offline Pitch", style = MaterialTheme.typography.labelLarge)
                                    Text("${String.format("%.2f", voicePrefs.offlinePitch)}", color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = voicePrefs.offlinePitch,
                                    onValueChange = { p ->
                                        viewModel.updateVoicePreferences { it.copy(offlinePitch = p) }
                                    },
                                    valueRange = 0.7f..1.4f
                                )

                                OutlinedButton(
                                    onClick = {
                                        runCatching { context.startActivity(viewModel.buildInstallOfflineVoicesIntent()) }
                                            .onFailure {
                                                viewModel.showMessage("Open Android Settings -> Accessibility -> Text-to-speech to install extra voice packs.")
                                            }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Install High-Quality System Voices")
                                }

                                // Pocket TTS: clone a voice in the Pocket TTS app, then
                                // come back and tap Refresh above to see it here.
                                if (voicePrefs.preferredProvider == VoiceProvider.POCKET_TTS) {
                                    if (viewModel.isPocketTtsInstalled) {
                                        OutlinedButton(
                                            onClick = { viewModel.openPocketTtsForCloning() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Clone a voice in Pocket TTS")
                                        }
                                        Text(
                                            text = "Opens Pocket TTS — clone a voice, then come back and tap Refresh above to see it in the list.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    } else {
                                        Text(
                                            text = "Pocket TTS isn't installed on this phone yet. Install it, then come back here — your Kokoro and cloned voices will show up as offline voices.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(offlineVoices, key = { "offline_${it.id}_${it.displayName}" }) { oVoice ->
                        val isSelected = voicePrefs.offlineVoiceId == oVoice.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateVoicePreferences {
                                        it.copy(
                                            offlineVoiceId = oVoice.id,
                                            offlineVoiceLabel = oVoice.displayName
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = oVoice.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${oVoice.qualityTier} · ${oVoice.localeTag} · Works Offline",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected Offline Voice",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.previewOfflineVoice(oVoice) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Rounded.VolumeUp, contentDescription = "Preview ${oVoice.displayName}")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ReaderTypographyControls(
                            settings = readerSettings,
                            onUpdate = { transform -> viewModel.updateReaderSettings(transform) },
                            showLivePreview = true
                        )
                    }
                }
            }
        }
    }
}
