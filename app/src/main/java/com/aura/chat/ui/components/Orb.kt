package com.aura.chat.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.chat.ui.theme.LocalAura

/** The living Aura orb: breathing glow, rotating colour core, specular highlight. */
@Composable
fun AuraOrb(modifier: Modifier = Modifier, size: Dp = 120.dp, active: Boolean = false) {
    val c = LocalAura.current
    val t = rememberInfiniteTransition(label = "orb")
    val rotation by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(if (active) 3200 else 10000, easing = LinearEasing)),
        label = "rotation",
    )
    val pulse by t.animateFloat(
        0.93f, 1.06f,
        infiniteRepeatable(tween(if (active) 900 else 2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        // Outer glow
        drawCircle(
            brush = Brush.radialGradient(listOf(c.accent.copy(alpha = 0.55f), c.accent2.copy(alpha = 0.18f), Color.Transparent), center = center, radius = r * pulse),
            radius = r * pulse,
            center = center,
        )
        // Rotating colour core
        rotate(rotation) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(c.accent, c.accent2, Color.White.copy(alpha = 0.85f), c.accent2, c.accent), center = center),
                radius = r * 0.58f,
                center = center,
            )
        }
        // Depth shading
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)), center = center, radius = r * 0.58f),
            radius = r * 0.58f,
            center = center,
        )
        // Specular highlight
        val hl = Offset(center.x - r * 0.16f, center.y - r * 0.2f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), center = hl, radius = r * 0.3f),
            radius = r * 0.3f,
            center = hl,
        )
    }
}
