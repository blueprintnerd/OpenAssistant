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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.blueprintnerd.openassistant.OpenAssistantApp
import com.blueprintnerd.openassistant.R
import com.blueprintnerd.openassistant.engine.AssistantResponse
import com.blueprintnerd.openassistant.engine.CardType
import com.blueprintnerd.openassistant.engine.CommandRouter
import com.blueprintnerd.openassistant.speech.SttCallbacks
import com.blueprintnerd.openassistant.ui.components.AssistantGlowingEdgeBar
import com.blueprintnerd.openassistant.ui.components.AssistantListeningDots
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.OpenAssistantTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AssistActivity : ComponentActivity() {
    private val app by lazy { application as OpenAssistantApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        app.commandRouter.clearPendingFollowUp()
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
    val focusRequester = remember { FocusRequester() }

    fun executeQuery(query: String, autoListenFollowUp: () -> Unit = {}) {
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
            if (res.expectFollowUp) {
                app.ttsEngine.speak(res.spokenText) {
                    autoListenFollowUp()
                }
            } else {
                app.ttsEngine.speak(res.spokenText)
            }
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
        showKeyboard = false
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
                            executeQuery(finalText) { startListening() }
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

    LaunchedEffect(showKeyboard) {
        if (showKeyboard) {
            runCatching { focusRequester.requestFocus() }
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
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
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
                    // Main Sheet Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // Header row with authentic product_logo_assistant_color_24
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.product_logo_assistant_color_24),
                                contentDescription = "Google Assistant Logo",
                                modifier = Modifier.size(24.dp),
                            )
                            IconButton(onClick = onOpenSettings, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Conversational Plate
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (transcript.isBlank() && response == null) {
                                Text(
                                    text = promptText,
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                                )
                            }

                            if (transcript.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 40.dp, end = 24.dp, top = 4.dp, bottom = 6.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                    ) {
                                        Text(
                                            text = transcript,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }

                            response?.let { res ->
                                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                                    AssistantAnswerCard(
                                        response = res,
                                        onActionUrl = { url -> app.deviceActions.openUrl(url) },
                                        onActionCommand = { cmd -> executeQuery(cmd) },
                                    )
                                }
                            }
                        }

                        // Horizontal Suggestion Rail
                        val chips = response?.suggestions?.ifEmpty { null }
                            ?: if (response == null && !isListening) CommandRouter.DEFAULT_SUGGESTIONS else emptyList()
                        if (chips.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                            ) {
                                items(chips) { chip ->
                                    AssistChip(
                                        onClick = { executeQuery(chip) },
                                        label = {
                                            Text(
                                                chip,
                                                color = AssistantBlue,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                        ),
                                        border = AssistChipDefaults.assistChipBorder(
                                            enabled = true,
                                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                                        ),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Match inputui_keyboard.xml vs inputui_layout.xml:
                        // When keyboard is open, replace the mic plate cleanly with the flush 56dp type bar.
                        if (showKeyboard) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(start = 24.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (typedInput.isEmpty()) {
                                        Text(
                                            text = "Type a message",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    BasicTextField(
                                        value = typedInput,
                                        onValueChange = { typedInput = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(AssistantBlue),
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
                                }
                                IconButton(
                                    onClick = {
                                        if (typedInput.isBlank()) {
                                            startListening()
                                        } else {
                                            val q = typedInput
                                            typedInput = ""
                                            executeQuery(q)
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Image(
                                        painter = painterResource(
                                            id = if (typedInput.isBlank()) {
                                                R.drawable.ic_mic_color
                                            } else {
                                                R.drawable.quantum_ic_send_googblue_24
                                            }
                                        ),
                                        contentDescription = if (typedInput.isBlank()) "Speak" else "Send",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                IconButton(
                                    onClick = {
                                        app.sttEngine.requestStop()
                                        isListening = false
                                        showKeyboard = true
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.quantum_ic_keyboard_grey600_24),
                                        contentDescription = "Keyboard",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .shadow(elevation = 3.dp, shape = CircleShape)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            if (isListening) {
                                                app.sttEngine.requestStop()
                                            } else {
                                                startListening()
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isListening) {
                                        AssistantListeningDots(
                                            amplitude = amplitude,
                                            isListening = true,
                                            isThinking = false,
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_mic_color),
                                            contentDescription = "Microphone",
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }

                                Box(modifier = Modifier.size(48.dp))
                            }
                        }
                    }

                    // 4-color bar: ONLY shown when actively listening/talking
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
