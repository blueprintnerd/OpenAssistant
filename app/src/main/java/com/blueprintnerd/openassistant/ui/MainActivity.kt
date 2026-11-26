package com.blueprintnerd.openassistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blueprintnerd.openassistant.OpenAssistantApp
import com.blueprintnerd.openassistant.data.SearchEngine
import com.blueprintnerd.openassistant.service.WakeWordForegroundService
import com.blueprintnerd.openassistant.speech.ModelKind
import com.blueprintnerd.openassistant.ui.components.GoogleGradientCircleIcon
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.AssistantGreen
import com.blueprintnerd.openassistant.ui.theme.OpenAssistantTheme
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OpenAssistantApp
        setContent {
            OpenAssistantTheme {
                var showSettings by remember { mutableStateOf(false) }
                if (showSettings) {
                    SimpleSettingsScreen(
                        app = app,
                        onBack = { showSettings = false },
                    )
                } else {
                    SimpleHomeScreen(
                        app = app,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleHomeScreen(
    app: OpenAssistantApp,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelStates by app.modelManager.states.collectAsState()

    val kwsState = modelStates[ModelKind.KWS]
    val asrState = modelStates[ModelKind.ASR]
    val voicePackReady = kwsState?.installed == true && asrState?.installed == true
    val voicePackBusy = (kwsState?.downloading == true || kwsState?.extracting == true ||
        asrState?.downloading == true || asrState?.extracting == true)
    val combinedProgress = ((kwsState?.progress ?: 0f) + (asrState?.progress ?: 0f)) / 2f
    val downloadError = kwsState?.error ?: asrState?.error

    var wakeEnabled by remember { mutableStateOf(app.preferences.wakeWordEnabled) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.RECORD_AUDIO] == true
        hasMicPermission = granted
        if (granted && voicePackReady) {
            wakeEnabled = true
            app.preferences.wakeWordEnabled = true
            WakeWordForegroundService.start(context)
        }
    }

    fun requestPermissions() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permLauncher.launch(perms)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                GoogleGradientCircleIcon(size = 72.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "OpenAssistant",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Classic voice assistant that runs on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Step 1: One-tap Voice Pack download
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "1. Voice Pack",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = when {
                                        voicePackReady -> "Ready on your phone"
                                        voicePackBusy -> "Downloading voice pack (${(combinedProgress * 100).toInt()}%)..."
                                        else -> "Needed to hear \"Hey Google\" and understand your voice"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (voicePackReady) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Ready",
                                    tint = AssistantGreen,
                                    modifier = Modifier.size(28.dp),
                                )
                            } else if (!voicePackBusy) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            if (!app.modelManager.isInstalled(ModelKind.KWS)) {
                                                app.modelManager.downloadModel(ModelKind.KWS)
                                            }
                                            if (!app.modelManager.isInstalled(ModelKind.ASR)) {
                                                app.modelManager.downloadModel(ModelKind.ASR)
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                ) {
                                    Text("Download")
                                }
                            }
                        }

                        if (voicePackBusy) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { combinedProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        downloadError?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Step 2: Set as Default Assistant (replaces Gemini)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "2. Replace Gemini",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Pick OpenAssistant as your Default digital assistant app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text("Set as Default Assistant")
                        }
                    }
                }
            }

            // Step 3: Hey Google toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "3. \"Hey Google\" Voice Match",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Wake your assistant hands-free anytime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = wakeEnabled,
                            enabled = voicePackReady,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasMicPermission) {
                                    requestPermissions()
                                } else {
                                    wakeEnabled = enabled
                                    app.preferences.wakeWordEnabled = enabled
                                    if (enabled) {
                                        WakeWordForegroundService.start(context)
                                    } else {
                                        WakeWordForegroundService.stop(context)
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // Try Assistant CTA
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (!hasMicPermission) {
                            requestPermissions()
                        }
                        context.startActivity(
                            Intent(context, AssistActivity::class.java).apply {
                                putExtra(AssistActivity.EXTRA_AUTO_LISTEN, true)
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Try Assistant", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SimpleSettingsScreen(
    app: OpenAssistantApp,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelStates by app.modelManager.states.collectAsState()

    var wakeWords by remember { mutableStateOf(app.preferences.wakeWords) }
    var newPhrase by remember { mutableStateOf("") }
    var speakResponses by remember { mutableStateOf(app.preferences.speakResponses) }
    var chimeEnabled by remember { mutableStateOf(app.preferences.chimeEnabled) }
    var useFahrenheit by remember { mutableStateOf(app.preferences.useFahrenheit) }
    var defaultCity by remember { mutableStateOf(app.preferences.defaultCity) }
    var selectedEngine by remember { mutableStateOf(app.preferences.searchEngine) }
    var shoppingList by remember { mutableStateOf(app.preferences.getShoppingList()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var haUrl by remember { mutableStateOf(app.preferences.homeAssistantUrl) }
    var haToken by remember { mutableStateOf(app.preferences.homeAssistantToken) }

    fun updateWakeWords(updated: List<String>) {
        wakeWords = updated
        app.preferences.wakeWords = updated
        if (app.preferences.wakeWordEnabled) {
            WakeWordForegroundService.reload(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Wake Phrases
            item {
                Text("Wake Phrases", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    wakeWords.forEach { phrase ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(phrase) },
                            trailingIcon = if (wakeWords.size > 1) {
                                {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { updateWakeWords(wakeWords.filterNot { it == phrase }) },
                                    )
                                }
                            } else null,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newPhrase,
                        onValueChange = { newPhrase = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add a wake phrase") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val clean = newPhrase.trim().uppercase(Locale.US)
                            if (clean.isNotEmpty() && clean !in wakeWords) {
                                updateWakeWords(wakeWords + clean)
                                newPhrase = ""
                            }
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = AssistantBlue)
                    }
                }
            }

            item { HorizontalDivider() }

            // Everyday preferences
            item {
                Text("Voice & Weather", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Speak answers aloud")
                    Switch(checked = speakResponses, onCheckedChange = {
                        speakResponses = it
                        app.preferences.speakResponses = it
                    })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Play chime when woken")
                    Switch(checked = chimeEnabled, onCheckedChange = {
                        chimeEnabled = it
                        app.preferences.chimeEnabled = it
                    })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Temperature")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = useFahrenheit,
                            onClick = {
                                useFahrenheit = true
                                app.preferences.useFahrenheit = true
                            },
                            label = { Text("°F") },
                        )
                        FilterChip(
                            selected = !useFahrenheit,
                            onClick = {
                                useFahrenheit = false
                                app.preferences.useFahrenheit = false
                            },
                            label = { Text("°C") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = defaultCity,
                    onValueChange = {
                        defaultCity = it
                        app.preferences.defaultCity = it
                    },
                    label = { Text("Your city (for weather)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            }

            item { HorizontalDivider() }

            // Web Search
            item {
                Text("Web Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchEngine.entries.forEach { engine ->
                        FilterChip(
                            selected = selectedEngine == engine,
                            onClick = {
                                selectedEngine = engine
                                app.preferences.searchEngine = engine
                            },
                            label = { Text(engine.label) },
                        )
                    }
                }
            }

            item { HorizontalDivider() }

            // Routines & Shopping List
            item {
                Text("Routines", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                app.preferences.getRoutines().forEach { routine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(routine.name, fontWeight = FontWeight.Medium)
                            Text(
                                "Say \"${routine.triggerPhrases.first()}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val res = app.commandRouter.handle(routine.triggerPhrases.first())
                                app.ttsEngine.speak(res.spokenText)
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = AssistantBlue)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Shopping List (${shoppingList.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (shoppingList.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            app.preferences.setShoppingList(emptyList())
                            shoppingList = emptyList()
                        }) {
                            Text("Clear")
                        }
                    }
                }
                if (shoppingList.isEmpty()) {
                    Text(
                        "Say \"Add milk to my shopping list\" anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    shoppingList.forEach { item ->
                        Text("- $item", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item { HorizontalDivider() }

            // Optional Advanced section (collapsed by default)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("More Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Icon(
                        if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }

                if (showAdvanced) {
                    Spacer(Modifier.height(10.dp))
                    val ttsState = modelStates[ModelKind.TTS]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Optional Offline Voice", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (ttsState?.installed == true) "Installed (20 MB)" else "Download a natural offline voice instead of your phone's default voice.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (ttsState?.downloading == true || ttsState?.extracting == true) {
                                LinearProgressIndicator(progress = { ttsState.progress }, modifier = Modifier.fillMaxWidth())
                            } else if (ttsState?.installed != true) {
                                Button(onClick = { scope.launch { app.modelManager.downloadModel(ModelKind.TTS) } }) {
                                    Text("Download Voice (20 MB)")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Smart Home (Home Assistant)", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = haUrl,
                        onValueChange = {
                            haUrl = it
                            app.preferences.homeAssistantUrl = it
                        },
                        label = { Text("Server URL (e.g. http://192.168.1.50:8123)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = haToken,
                        onValueChange = {
                            haToken = it
                            app.preferences.homeAssistantToken = it
                        },
                        label = { Text("Access Token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            WakeWordForegroundService.stop(context)
                            app.preferences.wakeWordEnabled = false
                            ModelKind.entries.forEach { app.modelManager.deleteModel(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Delete Downloaded Voice Files")
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}
