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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blueprintnerd.openassistant.OpenAssistantApp
import com.blueprintnerd.openassistant.data.Routine
import com.blueprintnerd.openassistant.data.SearchEngine
import com.blueprintnerd.openassistant.service.WakeWordForegroundService
import com.blueprintnerd.openassistant.speech.ModelKind
import com.blueprintnerd.openassistant.ui.components.AssistantFourColorBar
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.AssistantGreen
import com.blueprintnerd.openassistant.ui.theme.OpenAssistantTheme
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OpenAssistantApp
        setContent {
            OpenAssistantTheme {
                MainScreen(app = app)
            }
        }
    }
}

private enum class MainTab(val label: String) {
    HOME("Home"),
    MODELS("Models"),
    WAKE("Wake Word"),
    ROUTINES("Routines"),
    SETTINGS("Settings"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(app: OpenAssistantApp) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = MainTab.entries

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("OpenAssistant", fontWeight = FontWeight.Bold)
                            Text(
                                "100% On-Device Speech | Zero Google Servers | Zero LLM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
                AssistantFourColorBar(amplitude = 0.4f, isListening = false)
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { idx, tab ->
                    val icon = when (tab) {
                        MainTab.HOME -> Icons.Default.Home
                        MainTab.MODELS -> Icons.Default.Storage
                        MainTab.WAKE -> Icons.Default.GraphicEq
                        MainTab.ROUTINES -> Icons.Default.AutoAwesome
                        MainTab.SETTINGS -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Icon(icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tabs[selectedTab]) {
                MainTab.HOME -> HomeTab(
                    app = app,
                    onRequestPermissions = {
                        val perms = buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.toTypedArray()
                        permLauncher.launch(perms)
                    },
                    onOpenDefaultAssistantSettings = {
                        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                    },
                    onLaunchOverlay = {
                        context.startActivity(
                            Intent(context, AssistActivity::class.java).apply {
                                putExtra(AssistActivity.EXTRA_AUTO_LISTEN, true)
                            }
                        )
                    },
                    onGoToModels = { selectedTab = MainTab.MODELS.ordinal },
                )
                MainTab.MODELS -> ModelsTab(app = app)
                MainTab.WAKE -> WakeWordTab(app = app)
                MainTab.ROUTINES -> RoutinesTab(app = app)
                MainTab.SETTINGS -> SettingsTab(app = app)
            }
        }
    }
}

@Composable
private fun HomeTab(
    app: OpenAssistantApp,
    onRequestPermissions: () -> Unit,
    onOpenDefaultAssistantSettings: () -> Unit,
    onLaunchOverlay: () -> Unit,
    onGoToModels: () -> Unit,
) {
    val context = LocalContext.current
    val modelStates by app.modelManager.states.collectAsState()
    val kwsReady = modelStates[ModelKind.KWS]?.installed == true
    val asrReady = modelStates[ModelKind.ASR]?.installed == true
    var wakeEnabled by remember { mutableStateOf(app.preferences.wakeWordEnabled) }
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "Replace Gemini as Default Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Select OpenAssistant in Android's Digital Assistant settings so holding the power button or swiping from the corner opens OpenAssistant instead of Gemini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpenDefaultAssistantSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Open Default Digital Assistant Settings")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Always-On Wake Word",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (kwsReady) {
                                    "Listening locally for: ${app.preferences.wakeWords.joinToString(", ")}"
                                } else {
                                    "Download the Wake Word model in Models first"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = wakeEnabled,
                            enabled = kwsReady && hasMic,
                            onCheckedChange = { enabled ->
                                wakeEnabled = enabled
                                app.preferences.wakeWordEnabled = enabled
                                if (enabled) {
                                    WakeWordForegroundService.start(context)
                                } else {
                                    WakeWordForegroundService.stop(context)
                                }
                            },
                        )
                    }
                    if (!hasMic) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onRequestPermissions) {
                            Text("Grant Microphone & Notification Permissions")
                        }
                    }
                    if (!kwsReady || !asrReady) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onGoToModels) {
                            Text("Download On-Device Speech Models")
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onLaunchOverlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Launch Assistant Overlay")
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Engine Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    StatusRow("Wake Word Model (KWS)", if (kwsReady) "Installed" else "Not downloaded")
                    StatusRow("Speech-to-Text (Zipformer)", if (asrReady) "Installed" else "Not downloaded")
                    StatusRow(
                        "Neural Voice (Piper TTS)",
                        if (modelStates[ModelKind.TTS]?.installed == true) "Installed" else "Using Local System TTS",
                    )
                    StatusRow("Cloud / LLM Dependencies", "None (Deterministic Command Engine)")
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModelsTab(app: OpenAssistantApp) {
    val scope = rememberCoroutineScope()
    val states by app.modelManager.states.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "On-Device Speech Models",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "All models run locally via Sherpa-ONNX (downloaded from GitHub k2-fsa/sherpa-onnx). Zero Google servers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(ModelKind.entries) { kind ->
            val st = states[kind] ?: return@items
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(kind.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(kind.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = when {
                                    st.downloading -> "Downloading (${(st.progress * 100).toInt()}%)..."
                                    st.extracting -> "Extracting archive..."
                                    st.installed -> "Installed (${kind.sizeLabel})"
                                    else -> "Download size: ${kind.sizeLabel}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (st.installed) AssistantGreen else AssistantBlue,
                            )
                        }
                        if (st.installed) {
                            IconButton(onClick = { app.modelManager.deleteModel(kind) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete model")
                            }
                        } else if (!st.downloading && !st.extracting) {
                            Button(onClick = { scope.launch { app.modelManager.downloadModel(kind) } }) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Download")
                            }
                        }
                    }

                    if (st.downloading || st.extracting) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { st.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    st.error?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun WakeWordTab(app: OpenAssistantApp) {
    val context = LocalContext.current
    var wakeWords by remember { mutableStateOf(app.preferences.wakeWords) }
    var newPhrase by remember { mutableStateOf("") }
    var threshold by remember { mutableFloatStateOf(app.preferences.wakeWordThreshold) }
    var boost by remember { mutableFloatStateOf(app.preferences.wakeWordBoostScore) }
    var chime by remember { mutableStateOf(app.preferences.chimeEnabled) }

    fun saveAndReload(updated: List<String>) {
        wakeWords = updated
        app.preferences.wakeWords = updated
        if (app.preferences.wakeWordEnabled) {
            WakeWordForegroundService.reload(context)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Wake Word Configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Uses open-vocabulary Sherpa-ONNX keyword spotting. Works with Hey Google, OK Google, or any custom English phrase completely offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newPhrase,
                    onValueChange = { newPhrase = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Add wake phrase (e.g. Hey Google)") },
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val clean = newPhrase.trim().uppercase(Locale.US)
                        if (clean.isNotEmpty() && clean !in wakeWords) {
                            saveAndReload(wakeWords + clean)
                            newPhrase = ""
                        }
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }

        items(wakeWords) { phrase ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(phrase, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    IconButton(
                        enabled = wakeWords.size > 1,
                        onClick = { saveAndReload(wakeWords.filterNot { it == phrase }) },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Detection Threshold: ${String.format(Locale.US, "%.2f", threshold)}")
                    Text(
                        "Lower = easier to trigger; Higher = fewer false positives.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        onValueChangeFinished = {
                            app.preferences.wakeWordThreshold = threshold
                            if (app.preferences.wakeWordEnabled) WakeWordForegroundService.reload(context)
                        },
                        valueRange = 0.10f..0.70f,
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("Keyword Boost Score: ${String.format(Locale.US, "%.2f", boost)}")
                    Slider(
                        value = boost,
                        onValueChange = { boost = it },
                        onValueChangeFinished = {
                            app.preferences.wakeWordBoostScore = boost
                            if (app.preferences.wakeWordEnabled) WakeWordForegroundService.reload(context)
                        },
                        valueRange = 0.8f..3.0f,
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Play activation chime")
                        Switch(
                            checked = chime,
                            onCheckedChange = {
                                chime = it
                                app.preferences.chimeEnabled = it
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutinesTab(app: OpenAssistantApp) {
    val scope = rememberCoroutineScope()
    var routines by remember { mutableStateOf(app.preferences.getRoutines()) }
    var shoppingList by remember { mutableStateOf(app.preferences.getShoppingList()) }
    var lastRunOutput by remember { mutableStateOf<String?>(null) }

    var newName by remember { mutableStateOf("") }
    var newTrigger by remember { mutableStateOf("") }
    var newActions by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Routines", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Say a trigger phrase to run multiple deterministic commands in sequence.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        lastRunOutput?.let { out ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(out, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        items(routines, key = { it.id }) { routine ->
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Triggers: ${routine.triggerPhrases.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AssistantBlue,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val res = app.commandRouter.handle(routine.triggerPhrases.first())
                                lastRunOutput = "${res.title}\n${res.body}"
                                app.ttsEngine.speak(res.spokenText)
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Run routine")
                        }
                        IconButton(onClick = {
                            val updated = routines.filterNot { it.id == routine.id }
                            routines = updated
                            app.preferences.saveRoutines(updated)
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete routine")
                        }
                    }
                    routine.actions.forEachIndexed { i, act ->
                        Text("${i + 1}. $act", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create Routine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Routine name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newTrigger,
                        onValueChange = { newTrigger = it },
                        label = { Text("Trigger phrase (e.g. workout mode)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newActions,
                        onValueChange = { newActions = it },
                        label = { Text("Commands (one per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    Button(
                        onClick = {
                            val steps = newActions.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            if (newName.isNotBlank() && newTrigger.isNotBlank() && steps.isNotEmpty()) {
                                val updated = routines + Routine(
                                    id = UUID.randomUUID().toString(),
                                    name = newName.trim(),
                                    triggerPhrases = listOf(newTrigger.trim().lowercase(Locale.US)),
                                    actions = steps,
                                )
                                routines = updated
                                app.preferences.saveRoutines(updated)
                                newName = ""
                                newTrigger = ""
                                newActions = ""
                            }
                        },
                    ) {
                        Text("Save Routine")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                            "Say \"Add milk to my shopping list\" to populate this list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        shoppingList.forEach { item ->
                            Text("- $item", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(app: OpenAssistantApp) {
    var defaultCity by remember { mutableStateOf(app.preferences.defaultCity) }
    var useFahrenheit by remember { mutableStateOf(app.preferences.useFahrenheit) }
    var speakResponses by remember { mutableStateOf(app.preferences.speakResponses) }
    var preferPiper by remember { mutableStateOf(app.preferences.preferPiperTts) }
    var selectedEngine by remember { mutableStateOf(app.preferences.searchEngine) }
    var haUrl by remember { mutableStateOf(app.preferences.homeAssistantUrl) }
    var haToken by remember { mutableStateOf(app.preferences.homeAssistantToken) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Weather & Voice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = defaultCity,
                        onValueChange = {
                            defaultCity = it
                            app.preferences.defaultCity = it
                        },
                        label = { Text("Default Weather City (Open-Meteo)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Use Fahrenheit (°F)")
                        Switch(checked = useFahrenheit, onCheckedChange = {
                            useFahrenheit = it
                            app.preferences.useFahrenheit = it
                        })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Speak responses aloud")
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
                        Text("Prefer offline Piper neural voice")
                        Switch(checked = preferPiper, onCheckedChange = {
                            preferPiper = it
                            app.preferences.preferPiperTts = it
                        })
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Web Search Engine (Non-Google)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Local Smart Home (Home Assistant)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Control lights, switches, and scenes directly on your LAN with zero Google Home servers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = haUrl,
                        onValueChange = {
                            haUrl = it
                            app.preferences.homeAssistantUrl = it
                        },
                        label = { Text("Home Assistant URL (e.g. http://192.168.1.50:8123)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = haToken,
                        onValueChange = {
                            haToken = it
                            app.preferences.homeAssistantToken = it
                        },
                        label = { Text("Long-Lived Access Token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }
    }
}
