package com.blueprintnerd.openassistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.blueprintnerd.openassistant.OpenAssistantApp
import com.blueprintnerd.openassistant.engine.AssistantResponse
import com.blueprintnerd.openassistant.engine.CardType
import com.blueprintnerd.openassistant.engine.CommandRouter
import com.blueprintnerd.openassistant.speech.SttCallbacks
import com.blueprintnerd.openassistant.ui.components.AssistantFourDotsMark
import com.blueprintnerd.openassistant.ui.components.AssistantGlowingEdgeBar
import com.blueprintnerd.openassistant.ui.components.GoogleFourColorMicIcon
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.OpenAssistantTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AssistActivity : ComponentActivity() {
    private val app by lazy { application as OpenAssistantApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoListen = intent.getBooleanExtra(EXTRA_AUTO_LISTEN, true)

        setContent {
            OpenAssistantTheme {
                GoogleAssistantOverlay(
                    app = app,
                    autoListen = autoListen,
                    hasMicPermission = {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    },
                    onOpenSettings = {
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

@Composable
private fun GoogleAssistantOverlay(
    app: OpenAssistantApp,
    autoListen: Boolean,
    hasMicPermission: () -> Boolean,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var transcript by remember { mutableStateOf("") }
    var typedInput by remember { mutableStateOf("") }
    var response by remember { mutableStateOf<AssistantResponse?>(null) }
    var promptText by remember { mutableStateOf("Hi, how can I help?") }

    fun executeQuery(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        transcript = clean
        isListening = false
        isThinking = true
        showKeyboard = false
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
            promptText = "Allow microphone access in OpenAssistant to talk."
            showKeyboard = true
            return
        }
        if (!app.sttEngine.isModelReady()) {
            promptText = "Download the Voice Pack in OpenAssistant to talk, or type below."
            showKeyboard = true
            return
        }
        app.ttsEngine.stop()
        app.wakeWordEngine.pause()
        isListening = true
        transcript = ""
        response = null
        promptText = "Hi, how can I help?"

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
                            promptText = "Didn't catch that. Tap the mic to try again."
                        }
                    },
                    onError = { err ->
                        isListening = false
                        amplitude = 0f
                        app.wakeWordEngine.resume()
                        promptText = err
                    },
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        visible = true
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
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(),
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
                shadowElevation = 24.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Subtle Material You drag pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f))
                        )
                    }

                    // Main compact Google Assistant sheet body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Top-left 4-dot emblem (exact NGA placement)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            AssistantFourDotsMark(
                                size = 24.dp,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // Centered greeting or live spoken words
                        if (transcript.isNotBlank()) {
                            Text(
                                text = transcript,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (response == null) {
                            Text(
                                text = promptText,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Rich Answer Card
                        response?.let { res ->
                            Spacer(Modifier.height(14.dp))
                            AssistantAnswerCard(
                                response = res,
                                onActionUrl = { url -> app.deviceActions.openUrl(url) },
                                onActionCommand = { cmd -> executeQuery(cmd) },
                            )
                        }

                        // Horizontal suggestion pills
                        val chips = response?.suggestions?.ifEmpty { null }
                            ?: if (response == null && !isListening) CommandRouter.DEFAULT_SUGGESTIONS else emptyList()
                        if (chips.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(end = 8.dp),
                            ) {
                                items(chips) { chip ->
                                    AssistChip(
                                        onClick = { executeQuery(chip) },
                                        label = { Text(chip) },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                        border = null,
                                    )
                                }
                            }
                        }

                        // Optional keyboard bar
                        if (showKeyboard) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = typedInput,
                                    onValueChange = { typedInput = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Ask anything...") },
                                    shape = RoundedCornerShape(24.dp),
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
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        val q = typedInput
                                        typedInput = ""
                                        executeQuery(q)
                                    },
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = AssistantBlue,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Bottom action bar: Keyboard (left) | 4-color Mic when idle (center) | Settings (right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(onClick = { showKeyboard = !showKeyboard }) {
                                Icon(
                                    imageVector = Icons.Outlined.Keyboard,
                                    contentDescription = "Keyboard",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (!isListening && !isThinking) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { startListening() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    GoogleFourColorMicIcon(size = 24.dp)
                                }
                            } else {
                                // Tap anywhere in the center to finish speaking early
                                Box(
                                    modifier = Modifier
                                        .size(width = 80.dp, height = 40.dp)
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                        ) {
                                            if (isListening) app.sttEngine.requestStop()
                                        }
                                )
                            }

                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Flush edge-to-edge feathered 4-color Google Assistant glowing light bar
                    AssistantGlowingEdgeBar(
                        amplitude = amplitude,
                        isListening = isListening,
                        isThinking = isThinking,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantAnswerCard(
    response: AssistantResponse,
    onActionUrl: (String) -> Unit,
    onActionCommand: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (response.cardType == CardType.WEATHER && response.weather != null) {
                val w = response.weather
                Text(
                    text = w.location,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = w.temperature,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = w.condition,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = w.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (w.forecast.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        w.forecast.forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    day.dayLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "${day.high} / ${day.low}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            } else if (response.cardType == CardType.LIST && response.listItems.isNotEmpty()) {
                Text(
                    text = response.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                response.listItems.forEach { item ->
                    Text(
                        text = "- $item",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            } else {
                if (response.cardType == CardType.WIKIPEDIA || response.cardType == CardType.CALCULATOR) {
                    Text(
                        text = response.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = response.body,
                    style = if (response.cardType == CardType.CALCULATOR) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (response.cardType == CardType.CALCULATOR) FontWeight.SemiBold else FontWeight.Normal,
                )
            }

            if (response.actions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    response.actions.forEachIndexed { idx, action ->
                        if (idx == 0) {
                            Button(
                                onClick = {
                                    action.url?.let(onActionUrl)
                                    action.command?.let(onActionCommand)
                                },
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Text(action.label)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    action.url?.let(onActionUrl)
                                    action.command?.let(onActionCommand)
                                },
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Text(action.label)
                            }
                        }
                    }
                }
            }
        }
    }
}
