package com.blueprintnerd.openassistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blueprintnerd.openassistant.OpenAssistantApp
import com.blueprintnerd.openassistant.engine.AssistantResponse
import com.blueprintnerd.openassistant.engine.CardType
import com.blueprintnerd.openassistant.engine.CommandRouter
import com.blueprintnerd.openassistant.speech.SttCallbacks
import com.blueprintnerd.openassistant.ui.components.AssistantFourColorBar
import com.blueprintnerd.openassistant.ui.components.AssistantListeningDots
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.OpenAssistantTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AssistActivity : ComponentActivity() {
    private val app by lazy { application as OpenAssistantApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoListen = intent.getBooleanExtra(EXTRA_AUTO_LISTEN, true)
        val wakePhrase = intent.getStringExtra(EXTRA_WAKE_PHRASE)

        setContent {
            OpenAssistantTheme {
                AssistantOverlayScreen(
                    app = app,
                    autoListen = autoListen,
                    wakePhrase = wakePhrase,
                    hasMicPermission = {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    },
                    onOpenMainApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        app.sttEngine.requestStop()
        app.wakeWordEngine.resume()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTO_LISTEN = "auto_listen"
        const val EXTRA_WAKE_PHRASE = "wake_phrase"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantOverlayScreen(
    app: OpenAssistantApp,
    autoListen: Boolean,
    wakePhrase: String?,
    hasMicPermission: () -> Boolean,
    onOpenMainApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var transcript by remember { mutableStateOf("") }
    var typedInput by remember { mutableStateOf("") }
    var response by remember { mutableStateOf<AssistantResponse?>(null) }
    var statusMessage by remember {
        mutableStateOf(
            if (!wakePhrase.isNullOrBlank()) "Heard \"$wakePhrase\"" else "Hi, how can I help?"
        )
    }

    fun executeQuery(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        transcript = clean
        isListening = false
        isThinking = true
        scope.launch {
            val res = app.commandRouter.handle(clean)
            response = res
            isThinking = false
            app.ttsEngine.speak(res.spokenText)
            res.dismissAfterMs?.let { ms ->
                delay(ms)
                onDismiss()
            }
        }
    }

    fun startListening() {
        if (!hasMicPermission()) {
            statusMessage = "Microphone permission required. Open OpenAssistant settings to grant it."
            showKeyboard = true
            return
        }
        if (!app.sttEngine.isModelReady()) {
            statusMessage = "On-device Speech-to-Text model not downloaded yet. Type below or download it in Models."
            showKeyboard = true
            return
        }
        app.ttsEngine.stop()
        app.wakeWordEngine.pause()
        isListening = true
        transcript = ""
        response = null
        statusMessage = "Listening..."

        scope.launch {
            app.sttEngine.listenOnce(
                callbacks = SttCallbacks(
                    onPartialResult = { partial -> transcript = partial },
                    onAmplitude = { amp -> amplitude = amp },
                    onFinalResult = { finalText ->
                        isListening = false
                        amplitude = 0f
                        app.wakeWordEngine.resume()
                        if (finalText.isNotBlank()) {
                            executeQuery(finalText)
                        } else {
                            statusMessage = "Didn't catch that. Tap the mic to try again."
                        }
                    },
                    onError = { err ->
                        isListening = false
                        amplitude = 0f
                        app.wakeWordEngine.resume()
                        statusMessage = err
                    },
                )
            )
        }
    }

    LaunchedEffect(autoListen) {
        if (autoListen) {
            app.ttsEngine.playActivationChime()
            startListening()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "OpenAssistant",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        IconButton(onClick = onOpenMainApp, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                // Transcript / Status
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (transcript.isNotBlank()) {
                        Text(
                            text = transcript,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                    } else if (response == null) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // Rich Response Card
                    response?.let { res ->
                        AssistantResponseCard(
                            response = res,
                            onActionUrl = { url -> app.deviceActions.openUrl(url) },
                            onActionCommand = { cmd -> executeQuery(cmd) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // Suggestion Chips
                    val chips = response?.suggestions?.ifEmpty { null }
                        ?: if (response == null && !isListening) CommandRouter.DEFAULT_SUGGESTIONS else emptyList()
                    if (chips.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            chips.forEach { chip ->
                                AssistChip(
                                    onClick = { executeQuery(chip) },
                                    label = { Text(chip, style = MaterialTheme.typography.labelMedium) },
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // Optional Keyboard Input
                if (showKeyboard) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = typedInput,
                            onValueChange = { typedInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask or type a command...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val q = typedInput
                                    typedInput = ""
                                    executeQuery(q)
                                }
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val q = typedInput
                                typedInput = ""
                                executeQuery(q)
                            },
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = AssistantBlue)
                        }
                    }
                }

                // Bottom Control Row: Keyboard toggle | Animated 4-dot Mic | Spacer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { showKeyboard = !showKeyboard }) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Keyboard input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (isListening || isThinking) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .clickable {
                                    if (isListening) app.sttEngine.requestStop()
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            AssistantListeningDots(
                                amplitude = amplitude,
                                isListening = isListening,
                                isThinking = isThinking,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(AssistantBlue)
                                .clickable { startListening() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Listen",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            app.sttEngine.requestStop()
                            app.ttsEngine.stop()
                            isListening = false
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Iconic 4-Color Bottom Light Bar
                AssistantFourColorBar(
                    amplitude = amplitude,
                    isListening = isListening || isThinking,
                )
            }
        }
    }
}

@Composable
private fun AssistantResponseCard(
    response: AssistantResponse,
    onActionUrl: (String) -> Unit,
    onActionCommand: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = response.title,
                style = MaterialTheme.typography.labelLarge,
                color = AssistantBlue,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))

            if (response.cardType == CardType.WEATHER && response.weather != null) {
                val w = response.weather
                Text(
                    text = "${w.location}  ${w.temperature}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = w.condition,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = w.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (w.forecast.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        w.forecast.forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.dayLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text("${day.high} / ${day.low}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else if (response.cardType == CardType.LIST && response.listItems.isNotEmpty()) {
                response.listItems.forEach { item ->
                    Text(
                        text = "- $item",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            } else {
                Text(
                    text = response.body,
                    style = if (response.cardType == CardType.CALCULATOR) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (response.cardType == CardType.CALCULATOR) FontWeight.Bold else FontWeight.Normal,
                )
            }

            if (response.actions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    response.actions.forEachIndexed { idx, action ->
                        if (idx == 0) {
                            Button(onClick = {
                                action.url?.let(onActionUrl)
                                action.command?.let(onActionCommand)
                            }) {
                                Text(action.label)
                            }
                        } else {
                            OutlinedButton(onClick = {
                                action.url?.let(onActionUrl)
                                action.command?.let(onActionCommand)
                            }) {
                                Text(action.label)
                            }
                        }
                    }
                }
            }
        }
    }
}
