package com.stralarp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GlassShape = RoundedCornerShape(16.dp)

/** Glassmorphism-style button over mesh gradient. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val bg = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (enabled) 0.18f else 0.08f),
            Color.White.copy(alpha = if (enabled) 0.08f else 0.04f)
        )
    )
    val border = Color.White.copy(alpha = if (enabled) 0.28f else 0.12f)

    Box(
        modifier = modifier
            .clip(GlassShape)
            .background(bg)
            .border(1.dp, border, GlassShape)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White.copy(alpha = if (enabled) 1f else 0.45f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val border = Color(0xFF00E676).copy(alpha = if (enabled) 0.55f else 0.25f)
    Box(
        modifier = modifier
            .clip(GlassShape)
            .background(Color.White.copy(alpha = if (enabled) 0.06f else 0.03f))
            .border(1.5.dp, border, GlassShape)
            .clickable(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) Color(0xFF00E676) else Color(0xFF00E676).copy(alpha = 0.4f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
fun GlassText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalContentColor.current,
        modifier = modifier
    )
}
