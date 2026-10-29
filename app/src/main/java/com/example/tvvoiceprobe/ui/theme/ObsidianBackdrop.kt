package com.example.tvvoiceprobe.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val ObsidianBlack = Color(0xFF06070C)
private val ObsidianCyan = Color(0xFF00E5FF)
private val ObsidianViolet = Color(0xFF8B5CFF)
private val ObsidianMagenta = Color(0xFFFF2E88)

/**
 * Neon Obsidian chrome: near-black base, three slow-drifting colour glows,
 * a thin cyan-violet-magenta frame and a small pulsing orb.
 *
 * Purely decorative. The frame and orb are drawn on a Canvas above the content
 * and take no focus or touch input, so D-pad and touch navigation are unchanged.
 * Content is expected to use transparent or translucent backgrounds so the
 * glows show through (see KellieTheme).
 */
@Composable
fun ObsidianBackdrop(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "obsidian")
    // 0..1 over 18 s, mapped to a full circle so the loop has no visible jump.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing)),
        label = "drift"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            tween(2_200, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .drawBehind {
                val angle = drift * 2f * Math.PI.toFloat()
                val w = size.width
                val h = size.height
                glow(ObsidianCyan, Offset(w * (0.12f + 0.05f * cos(angle)), h * (0.08f + 0.06f * sin(angle))), w * 0.55f, 0.20f)
                glow(ObsidianViolet, Offset(w * (0.55f + 0.06f * sin(angle)), h * (0.95f + 0.04f * cos(angle))), w * 0.60f, 0.20f)
                glow(ObsidianMagenta, Offset(w * (0.95f + 0.03f * cos(angle)), h * (0.20f + 0.08f * sin(angle))), w * 0.45f, 0.14f)
            }
    ) {
        content()

        // Gradient frame, inside the screen padding so it never covers content.
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        ObsidianCyan.copy(alpha = 0.55f),
                        ObsidianViolet.copy(alpha = 0.55f),
                        ObsidianMagenta.copy(alpha = 0.55f)
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                ),
                cornerRadius = CornerRadius(26.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Pulsing orb in the otherwise empty top-right corner.
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 22.dp, end = 36.dp)
                .size(64.dp)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val orb = size.minDimension * 0.24f * pulse
            val halo = size.minDimension * 0.50f * pulse
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(ObsidianCyan.copy(alpha = 0.45f), ObsidianViolet.copy(alpha = 0.18f), Color.Transparent),
                    center = center, radius = halo
                ),
                radius = halo, center = center
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White, ObsidianCyan, ObsidianViolet),
                    center = Offset(center.x - orb * 0.3f, center.y - orb * 0.3f), radius = orb * 1.6f
                ),
                radius = orb, center = center
            )
        }
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center, radius = radius
        ),
        radius = radius, center = center
    )
}
