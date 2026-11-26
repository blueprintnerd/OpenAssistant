package com.blueprintnerd.openassistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
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
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.AssistantGreen
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
        response = null
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
            ) + fadeIn(tween(180)),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        // Top Header: official 24dp Assistant icon on left, subtle Tune icon on right
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.product_logo_assistant_color_24),
                                contentDescription = "Assistant",
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

                        Spacer(Modifier.height(10.dp))

                        // Conversation Body
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (transcript.isBlank() && response == null) {
                                Text(
                                    text = promptText,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 22.sp,
                                        lineHeight = 28.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                                )
                            }

                            // Spoken/typed query: centered large text while listening, right-aligned bubble once sent
                            if (transcript.isNotBlank()) {
                                if (isListening) {
                                    Text(
                                        text = transcript,
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 56.dp, end = 24.dp, top = 2.dp, bottom = 10.dp),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(
                                                topStart = 20.dp,
                                                topEnd = 20.dp,
                                                bottomStart = 20.dp,
                                                bottomEnd = 6.dp,
                                            ),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                        ) {
                                            Text(
                                                text = transcript,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            // Assistant Response: clean text directly on sheet for simple replies, rich cards for structured data
                            response?.let { res ->
                                AssistantResponseContent(
                                    response = res,
                                    onActionUrl = { url -> app.deviceActions.openUrl(url) },
                                    onActionCommand = { cmd -> executeQuery(cmd) { startListening() } },
                                )
                            }
                        }

                        // Suggestion Pills (only when idle)
                        val chips = response?.suggestions?.ifEmpty { null }
                            ?: if (response == null && !isListening && !isThinking) CommandRouter.DEFAULT_SUGGESTIONS else emptyList()
                        if (chips.isNotEmpty() && !isThinking && !isListening) {
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                            ) {
                                items(chips) { chip ->
                                    AssistChip(
                                        onClick = { executeQuery(chip) { startListening() } },
                                        label = {
                                            Text(
                                                chip,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Medium,
                                            )
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        ),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Bottom Input / Action Area:
                        // - Keyboard open -> flush 56dp input bar
                        // - Thinking -> hide mic & keyboard completely so ONLY the bottom 4-color bar animates
                        // - Listening -> minimal subtle keyboard button + tap-to-stop area above the 4-color bar
                        // - Idle -> clean Google-style floating mic circle + keyboard button
                        when {
                            showKeyboard -> {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
                                                    executeQuery(q) { startListening() }
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
                                                executeQuery(q) { startListening() }
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
                            }
                            isThinking -> {
                                // Clean breathing room above the single animated 4-color bar (no duplicate voice button!)
                                Spacer(Modifier.height(12.dp))
                            }
                            isListening -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    IconButton(
                                        onClick = {
                                            app.sttEngine.requestStop()
                                            isListening = false
                                            showKeyboard = true
                                        },
                                        modifier = Modifier.size(44.dp),
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.quantum_ic_keyboard_grey600_24),
                                            contentDescription = "Keyboard",
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    Text(
                                        text = "Listening...",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { app.sttEngine.requestStop() }
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                    Spacer(Modifier.size(44.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                            else -> {
                                AnimatedVisibility(
                                    visible = !isThinking && !isListening,
                                    enter = fadeIn(tween(180)),
                                    exit = fadeOut(tween(120)),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        IconButton(
                                            onClick = { showKeyboard = true },
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
                                                .size(54.dp)
                                                .shadow(elevation = 4.dp, shape = CircleShape)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { startListening() },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.ic_mic_color),
                                                contentDescription = "Speak",
                                                modifier = Modifier.size(26.dp),
                                            )
                                        }

                                        Spacer(Modifier.size(48.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Crisp 4-color bar: ONLY visible while listening or thinking
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
private fun AssistantResponseContent(
    response: AssistantResponse,
    onActionUrl: (String) -> Unit,
    onActionCommand: (String) -> Unit,
) {
    when {
        // Simple conversational replies & follow-up questions: clean text directly on sheet (like real Google Assistant)
        response.cardType == CardType.TEXT && response.actions.isEmpty() -> {
            Text(
                text = response.body,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        // Action confirmations (Timer, Alarm, Flashlight, Music, Navigation): crisp inline confirmation row
        response.cardType == CardType.ACTION_CONFIRMATION && response.actions.isEmpty() -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AssistantGreen,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = response.body,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Rich structured cards (Weather, Calculator, Wikipedia/Dictionary, List, or actions)
        else -> {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    if (response.cardType == CardType.WEATHER && response.weather != null) {
                        val w = response.weather
                        Text(
                            text = w.location,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = w.temperature,
                                style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp),
                                fontWeight = FontWeight.Normal,
                            )
                            Text(
                                text = w.condition,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = w.details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (w.forecast.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(12.dp))
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
                                        Spacer(Modifier.height(2.dp))
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
                    } else if (response.cardType == CardType.CALCULATOR) {
                        val parts = response.body.split(" = ", limit = 2)
                        if (parts.size == 2) {
                            Text(
                                text = parts[0],
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = parts[1],
                                style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp),
                                fontWeight = FontWeight.Medium,
                            )
                        } else {
                            Text(
                                text = response.body,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    } else if (response.cardType == CardType.LIST && response.listItems.isNotEmpty()) {
                        Text(
                            text = response.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AssistantBlue,
                        )
                        Spacer(Modifier.height(8.dp))
                        response.listItems.forEach { item ->
                            Text(
                                text = "•  $item",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    } else {
                        if (response.cardType == CardType.WIKIPEDIA) {
                            Text(
                                text = response.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = AssistantBlue,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            text = response.body,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (response.actions.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
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
    }
}
