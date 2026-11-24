package com.stralarp.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/** Soft animated mesh-style gradient background (dark athletic look). */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "mesh")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshAngle"
    )

    val rad = Math.toRadians(t.toDouble()).toFloat()
    val c1 = Offset(
        x = 0.25f + 0.12f * cos(rad),
        y = 0.20f + 0.10f * sin(rad * 0.9f)
    )
    val c2 = Offset(
        x = 0.75f + 0.10f * cos(rad * 1.1f + 1f),
        y = 0.35f + 0.12f * sin(rad * 0.8f)
    )
    val c3 = Offset(
        x = 0.40f + 0.15f * cos(rad * 0.7f + 2f),
        y = 0.78f + 0.08f * sin(rad * 1.2f)
    )
    val c4 = Offset(
        x = 0.85f + 0.08f * cos(rad * 0.95f + 0.5f),
        y = 0.85f + 0.10f * sin(rad * 0.6f)
    )

    // Neon green / teal / deep purple / near-black mesh
    val colorA = Color(0xFF00E676)
    val colorB = Color(0xFF00BFA5)
    val colorC = Color(0xFF7C4DFF)
    val colorD = Color(0xFF1A237E)
    val base = Color(0xFF0A0A0A)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Base
        drawRect(base)

        fun blob(center: Offset, color: Color, radiusFrac: Float, alpha: Float) {
            val cx = center.x * w
            val cy = center.y * h
            val r = radiusFrac * maxOf(w, h)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = r
                ),
                radius = r,
                center = Offset(cx, cy)
            )
        }

        blob(c1, colorA, 0.55f, 0.35f)
        blob(c2, colorB, 0.50f, 0.30f)
        blob(c3, colorC, 0.60f, 0.28f)
        blob(c4, colorD, 0.45f, 0.40f)

        // Subtle top vignette
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.25f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.45f)
                )
            )
        )
    }
}
