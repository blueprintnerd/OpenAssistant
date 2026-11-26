package com.blueprintnerd.openassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
 * Smooth overlapping radial blooms + continuous luminous gradient core line
 * with zero hard vertical seams between colors.
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
            .height(32.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            val w = size.width
            val h = size.height
            val active = isListening || isThinking
            val energy = when {
                isListening -> (0.45f + amplitude * 0.55f).coerceIn(0.45f, 1f)
                isThinking -> 0.72f
                else -> 0.30f
            }

            // 1. Wide feathered upward radial blooms for each of the 4 Google colors
            val centers = listOf(0.14f, 0.38f, 0.62f, 0.86f)
            colors.forEachIndexed { i, color ->
                val drift = if (active) sin(phase + i * 1.25f) * 0.06f else 0f
                val cx = w * (centers[i] + drift)
                val radius = w * (0.24f + 0.08f * energy)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.55f * energy),
                            color.copy(alpha = 0.22f * energy),
                            Color.Transparent,
                        ),
                        center = Offset(cx, h),
                        radius = radius,
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h),
                )
            }

            // 2. Smooth continuous gradient core strip along the bottom edge
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

            // Subtle arched curve peaking in center when voice is active
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

            // 3. Soft white-hot highlight filament at the very bottom when speaking
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
 * Classic 4-color Google Microphone vector mark (Blue top, Green stem, Yellow/Red cradle).
 */
@Composable
fun GoogleFourColorMicIcon(
    size: Dp = 26.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f

        // Blue mic capsule
        val capW = w * 0.28f
        val capH = h * 0.48f
        drawRoundRect(
            color = AssistantBlue,
            topLeft = Offset(cx - capW / 2f, h * 0.08f),
            size = Size(capW, capH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(capW / 2f, capW / 2f),
        )

        // Left yellow arc + right red arc cradle
        val strokeW = w * 0.095f
        val arcTop = h * 0.28f
        val arcSize = Size(w * 0.56f, h * 0.42f)
        val arcTopLeft = Offset(cx - arcSize.width / 2f, arcTop)
        drawArc(
            color = AssistantYellow,
            startAngle = 180f,
            sweepAngle = -90f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
        drawArc(
            color = AssistantRed,
            startAngle = 90f,
            sweepAngle = -90f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )

        // Green stem
        drawLine(
            color = AssistantGreen,
            start = Offset(cx, h * 0.70f),
            end = Offset(cx, h * 0.90f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
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

/**
 * Iconic 4-dot Google Assistant emblem.
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
