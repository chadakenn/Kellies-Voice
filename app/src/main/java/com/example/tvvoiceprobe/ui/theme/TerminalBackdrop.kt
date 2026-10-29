package com.example.tvvoiceprobe.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TerminalBlack = Color(0xFF030A07)
private val TerminalGreen = Color(0xFF39FF88)

/**
 * Jade Terminal chrome: green-black base, faint scanlines, HUD corner brackets,
 * a slow scan band and a blinking prompt cursor in the top-right corner.
 *
 * Purely decorative. Everything above the content is drawn on Canvases or a
 * non-focusable label, so D-pad and touch navigation are unchanged. Animated
 * values are only read in draw and graphics-layer lambdas, so the settings
 * content above is not recomposed on every animation frame.
 */
@Composable
fun TerminalBackdrop(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "terminal")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7_000, easing = LinearEasing)),
        label = "sweep"
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
        label = "blink"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .drawBehind {
                // Soft vignette plus a faint green glow rising from the bottom edge.
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.75f
                    )
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, TerminalGreen.copy(alpha = 0.07f)),
                        startY = size.height * 0.6f,
                        endY = size.height
                    )
                )
            }
    ) {
        content()

        // Static layer: scanlines and corner brackets. Reads no animated state,
        // so it is drawn once rather than every frame.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 3.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = TerminalGreen.copy(alpha = 0.04f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += step
            }

            val inset = 12.dp.toPx()
            val arm = 30.dp.toPx()
            val bracket = TerminalGreen.copy(alpha = 0.85f)
            val width = 2.dp.toPx()
            val w = size.width
            val h = size.height
            // Hairline frame
            drawRect(
                color = TerminalGreen.copy(alpha = 0.16f),
                topLeft = Offset(inset, inset),
                size = Size(w - inset * 2, h - inset * 2),
                style = Stroke(width = 1.dp.toPx())
            )
            // Top-left
            drawLine(bracket, Offset(inset, inset), Offset(inset + arm, inset), width)
            drawLine(bracket, Offset(inset, inset), Offset(inset, inset + arm), width)
            // Top-right
            drawLine(bracket, Offset(w - inset, inset), Offset(w - inset - arm, inset), width)
            drawLine(bracket, Offset(w - inset, inset), Offset(w - inset, inset + arm), width)
            // Bottom-left
            drawLine(bracket, Offset(inset, h - inset), Offset(inset + arm, h - inset), width)
            drawLine(bracket, Offset(inset, h - inset), Offset(inset, h - inset - arm), width)
            // Bottom-right
            drawLine(bracket, Offset(w - inset, h - inset), Offset(w - inset - arm, h - inset), width)
            drawLine(bracket, Offset(w - inset, h - inset), Offset(w - inset, h - inset - arm), width)
        }

        // Animated layer: one slow scan band sweeping top to bottom.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val band = 110.dp.toPx()
            val y = -band + (size.height + band) * sweep
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, TerminalGreen.copy(alpha = 0.07f), Color.Transparent),
                    startY = y,
                    endY = y + band
                ),
                topLeft = Offset(0f, y),
                size = Size(size.width, band)
            )
        }

        // Prompt with a blinking block cursor, in the otherwise empty top-right corner.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "kellie@tv:~$",
                color = TerminalGreen.copy(alpha = 0.85f),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(width = 9.dp, height = 16.dp)
                    .graphicsLayer { alpha = if (blink < 0.5f) 1f else 0f }
                    .background(TerminalGreen)
            )
        }
    }
}
