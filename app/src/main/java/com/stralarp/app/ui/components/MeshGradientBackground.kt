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

/** Soft animated mesh: white – gray – black (monochrome athletic look). */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "mesh")
    val t by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "meshAngle"
    )

    val rad = Math.toRadians(t.toDouble()).toFloat()
    val c1 = Offset(
        x = 0.22f + 0.14f * cos(rad),
        y = 0.18f + 0.11f * sin(rad * 0.9f)
    )
    val c2 = Offset(
        x = 0.78f + 0.11f * cos(rad * 1.1f + 1f),
        y = 0.32f + 0.13f * sin(rad * 0.8f)
    )
    val c3 = Offset(
        x = 0.38f + 0.16f * cos(rad * 0.7f + 2f),
        y = 0.76f + 0.09f * sin(rad * 1.2f)
    )
    val c4 = Offset(
        x = 0.88f + 0.09f * cos(rad * 0.95f + 0.5f),
        y = 0.88f + 0.10f * sin(rad * 0.6f)
    )
    val c5 = Offset(
        x = 0.55f + 0.10f * cos(rad * 0.85f + 3f),
        y = 0.48f + 0.12f * sin(rad * 1.05f)
    )

    // White / light gray / mid gray / charcoal – no neon
    val white = Color(0xFFF5F5F5)
    val lightGray = Color(0xFFBDBDBD)
    val midGray = Color(0xFF757575)
    val darkGray = Color(0xFF424242)
    val nearBlack = Color(0xFF121212)
    val base = Color(0xFF0A0A0A)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

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

        // Soft light blobs on dark base → white–gray–black mesh
        blob(c1, white, 0.58f, 0.22f)
        blob(c2, lightGray, 0.52f, 0.28f)
        blob(c3, midGray, 0.62f, 0.32f)
        blob(c4, darkGray, 0.48f, 0.38f)
        blob(c5, white, 0.40f, 0.12f)

        // Depth: top highlight + bottom vignette
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.06f),
                    Color.Transparent,
                    nearBlack.copy(alpha = 0.55f)
                )
            )
        )
    }
}
