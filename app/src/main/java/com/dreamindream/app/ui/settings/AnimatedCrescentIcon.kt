package com.dreamindream.app.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GoldCore = Color(0xFFFFF3C0)
private val GoldEdge = Color(0xFFFFC94F)
private val GlowColor = Color(0xFFFFE082)
private val OutlineColor = Color(0xFFFFF7DF)

@Composable
fun AnimatedCrescentIcon(
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "crescent_anim")

    val bobOffset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle"
    )

    Box(
        modifier = modifier.size(iconSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(iconSize)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val center = Offset(canvasWidth / 2f, canvasHeight / 2f + bobOffset)
            val radius = minOf(canvasWidth, canvasHeight) / 2.4f

            val glowBrush = Brush.radialGradient(
                colors = listOf(GlowColor.copy(alpha = glowAlpha), Color.Transparent),
                center = center,
                radius = radius * 1.6f
            )
            drawCircle(
                brush = glowBrush,
                center = center,
                radius = radius * 1.6f
            )

            val outerPath = Path().apply {
                addOval(Rect(center = center, radius = radius))
            }

            val innerPath = Path().apply {
                addOval(
                    Rect(
                        center = Offset(center.x + radius * 0.45f, center.y),
                        radius = radius * 0.92f
                    )
                )
            }

            val crescentPath = Path.combine(
                PathOperation.Difference,
                outerPath,
                innerPath
            )

            val moonBrush = Brush.linearGradient(
                colors = listOf(GoldCore, GoldEdge),
                start = Offset(center.x - radius, center.y - radius),
                end = Offset(center.x + radius, center.y + radius)
            )

            drawPath(
                path = crescentPath,
                brush = moonBrush
            )

            drawPath(
                path = crescentPath,
                color = OutlineColor,
                style = Stroke(width = radius * 0.08f),
                alpha = 0.85f
            )

            val sparkleRadius = radius * 0.16f
            val sparkleCenter = Offset(
                x = center.x + radius * 0.1f,
                y = center.y - radius * 0.5f
            )
            drawCircle(
                color = OutlineColor.copy(alpha = sparkleAlpha),
                radius = sparkleRadius,
                center = sparkleCenter
            )
        }
    }
}
