package com.blueprintnerd.openassistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.AssistantGreen
import com.blueprintnerd.openassistant.ui.theme.AssistantRed
import com.blueprintnerd.openassistant.ui.theme.AssistantYellow
import kotlin.math.PI
import kotlin.math.sin

/**
 * Crisp 4-color Google Assistant bottom bar that ONLY appears when actively
 * listening or thinking, with smooth undulating segment widths and zero muddy blur.
 */
@Composable
fun AssistantGlowingEdgeBar(
    amplitude: Float,
    isListening: Boolean,
    isThinking: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val active = isListening || isThinking
    val transition = rememberInfiniteTransition(label = "assistant_edge_bar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isThinking) 900 else 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "edge_phase",
    )

    val colors = listOf(AssistantBlue, AssistantRed, AssistantYellow, AssistantGreen)

    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(140)) + expandVertically(expandFrom = Alignment.Bottom),
        exit = fadeOut(tween(140)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        ) {
            val w = size.width
            val h = size.height
            val energy = when {
                isListening -> (0.55f + amplitude * 0.45f).coerceIn(0.55f, 1f)
                isThinking -> 0.85f
                else -> 0.5f
            }
            val barH = (3.5.dp.toPx() + 2.5.dp.toPx() * energy).coerceAtMost(h)
            val top = h - barH

            val weights = FloatArray(4) { i ->
                1f + 0.35f * sin(phase + i * 1.45f) * energy
            }
            val weightSum = weights.sum()
            var x = 0f
            colors.forEachIndexed { i, color ->
                val segW = w * (weights[i] / weightSum)
                drawRect(
                    color = color,
                    topLeft = Offset(x, top),
                    size = Size(segW + 1f, barH),
                )
                x += segW
            }

            // Subtle crisp top sheen for a luminous glass-bar feel without upward blur
            drawRect(
                color = Color.White.copy(alpha = 0.22f * energy),
                topLeft = Offset(0f, top),
                size = Size(w, 1.dp.toPx()),
            )
        }
    }
}

/**
 * Perfect circle with the 4-color Google gradient (matches the app icon).
 */
@Composable
fun GoogleGradientCircleIcon(
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val brush = Brush.linearGradient(
            colors = listOf(AssistantBlue, AssistantRed, AssistantYellow, AssistantGreen),
            start = Offset(0f, 0f),
            end = Offset(this.size.width, this.size.height),
        )
        drawCircle(brush = brush)
    }
}
