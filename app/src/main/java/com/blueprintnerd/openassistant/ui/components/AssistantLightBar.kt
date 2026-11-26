package com.blueprintnerd.openassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blueprintnerd.openassistant.ui.theme.AssistantBlue
import com.blueprintnerd.openassistant.ui.theme.AssistantGreen
import com.blueprintnerd.openassistant.ui.theme.AssistantRed
import com.blueprintnerd.openassistant.ui.theme.AssistantYellow
import kotlin.math.PI
import kotlin.math.sin

/**
 * Google Assistant NGA-style feathered 4-color glowing bottom edge bar.
 * Matches AssistantP6GlowView specs from inputui_layout.xml:
 * - 15dp to 30dp upward gradient glow
 * - 2dp light line core with smooth continuous 4-color blending
 */
@Composable
fun AssistantGlowingEdgeBar(
    amplitude: Float,
    isListening: Boolean,
    isThinking: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "assistant_glow_bar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glow_phase",
    )

    val colors = listOf(AssistantBlue, AssistantRed, AssistantYellow, AssistantGreen)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(30.dp)) {
            val w = size.width
            val h = size.height
            val active = isListening || isThinking
            val energy = when {
                isListening -> (0.45f + amplitude * 0.55f).coerceIn(0.45f, 1f)
                isThinking -> 0.72f
                else -> 0.28f
            }

            // Feathered upward blooms for the 4 colors
            val centers = listOf(0.14f, 0.38f, 0.62f, 0.86f)
            colors.forEachIndexed { i, color ->
                val drift = if (active) sin(phase + i * 1.25f) * 0.06f else 0f
                val cx = w * (centers[i] + drift)
                val radius = w * (0.24f + 0.08f * energy)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.55f * energy),
                            color.copy(alpha = 0.20f * energy),
                            Color.Transparent,
                        ),
                        center = Offset(cx, h),
                        radius = radius,
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                )
            }

            // Smooth continuous 4-color core along bottom edge
            val stripH = if (active) (4.5.dp.toPx() + 3.5.dp.toPx() * energy) else 4.dp.toPx()
            val shiftPx = if (active) sin(phase) * w * 0.06f else 0f
            val coreBrush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to AssistantBlue,
                    0.28f to AssistantBlue,
                    0.42f to AssistantRed,
                    0.58f to AssistantYellow,
                    0.76f to AssistantGreen,
                    1.00f to AssistantGreen,
                ),
                startX = shiftPx,
                endX = w + shiftPx,
            )

            val archHeight = if (active) stripH * (1f + 0.55f * amplitude) else stripH
            val path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, h - stripH * 0.75f)
                cubicTo(
                    w * 0.28f, h - archHeight,
                    w * 0.72f, h - archHeight,
                    w, h - stripH * 0.75f,
                )
                lineTo(w, h)
                close()
            }
            drawPath(path = path, brush = coreBrush)

            if (active) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.55f * energy),
                            Color.White.copy(alpha = 0.55f * energy),
                            Color.Transparent,
                        )
                    ),
                    start = Offset(w * 0.1f, h - 1.5.dp.toPx()),
                    end = Offset(w * 0.9f, h - 1.5.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * Animated 4-dot / 4-bar equalizer inside the mic plate when active.
 */
@Composable
fun AssistantListeningDots(
    amplitude: Float,
    isListening: Boolean,
    isThinking: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "assistant_dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dots_phase",
    )

    val colors = listOf(AssistantBlue, AssistantRed, AssistantYellow, AssistantGreen)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { idx, color ->
            Canvas(modifier = Modifier.size(width = 6.dp, height = 24.dp)) {
                val wave = sin(phase + idx * 0.9f)
                val barHeight = when {
                    isListening -> {
                        val dynamic = (0.35f + amplitude * 0.65f) * (0.65f + 0.35f * wave)
                        (size.height * dynamic).coerceIn(6f, size.height)
                    }
                    isThinking -> {
                        val pulse = 0.4f + 0.3f * wave
                        (size.height * pulse).coerceIn(6f, size.height)
                    }
                    else -> 6f
                }
                val top = (size.height - barHeight) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset((size.width - 5f) / 2f, top),
                    size = Size(5f, barHeight),
                    cornerRadius = CornerRadius(2.5f, 2.5f),
                )
            }
        }
    }
}

/**
 * Perfect circle with the 4-color Google gradient.
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

/**
 * 4-dot Google Assistant emblem.
 */
@Composable
fun AssistantFourDotsMark(
    size: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(
            color = AssistantBlue,
            radius = s * 0.26f,
            center = Offset(s * 0.30f, s * 0.42f),
        )
        drawCircle(
            color = AssistantRed,
            radius = s * 0.13f,
            center = Offset(s * 0.68f, s * 0.30f),
        )
        drawCircle(
            color = AssistantYellow,
            radius = s * 0.16f,
            center = Offset(s * 0.68f, s * 0.64f),
        )
        drawCircle(
            color = AssistantGreen,
            radius = s * 0.08f,
            center = Offset(s * 0.88f, s * 0.44f),
        )
    }
}
