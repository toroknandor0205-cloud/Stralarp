package com.stralarp.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.stralarp.app.data.model.TrackPoint

@Composable
fun ElevationProfile(
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF00E676)
) {
    val elevs = points.mapNotNull { it.elevation }
    if (elevs.size < 2) return

    val minE = elevs.minOrNull() ?: return
    val maxE = elevs.maxOrNull() ?: return
    val range = (maxE - minE).coerceAtLeast(1.0)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val w = size.width
        val h = size.height
        val path = Path()
        elevs.forEachIndexed { i, e ->
            val x = i.toFloat() / (elevs.size - 1) * w
            val y = h - ((e - minE) / range * h).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        // baseline
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(0f, h),
            end = Offset(w, h),
            strokeWidth = 1.dp.toPx()
        )
    }
}
