package com.dreamindream.app.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 색 정의
private val PremiumYellow = Color(0xFFFFE56A)
private val PremiumGlow = Color(0xFFFFF1A0)
private val PremiumStroke = Color(0xFFFFE56A)
private val PremiumBlack = Color(0xFF050509)

/**
 * 이제 초승달 대신 PREMIUM 뱃지를 그려주는 컴포저블.
 * - 검정 배경
 * - 노란 글씨
 * - 얇은 노란 stroke
 * - 노란 후광 + 살짝 떠다니는 애니메이션
 */
@Composable
fun AnimatedCrescentIcon(
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp   // 여기 값이 "높이" 느낌이라고 생각하면 됨 (가로는 자동으로 더 길게 잡음)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "premium_badge_anim")

    // 위아래로 살짝 떠다니는 애니메이션
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    // 후광 알파 애니메이션 (숨쉬는 느낌)
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // iconSize를 높이로 쓰고, 가로는 좀 더 길게 (배지 느낌)
    val badgeHeight = iconSize
    val badgeWidth = iconSize * 2.4f

    Box(
        modifier = modifier.size(width = badgeWidth, height = badgeHeight),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            val center = Offset(x = w / 2f, y = h / 2f + bobOffset.dp.toPx())

            // 배지 사각형
            val rectWidth = w * 0.9f
            val rectHeight = h * 0.9f
            val left = center.x - rectWidth / 2f
            val top = center.y - rectHeight / 2f
            val rectSize = Size(rectWidth, rectHeight)
            val rect = Rect(left = left, top = top, right = left + rectWidth, bottom = top + rectHeight)

            // 뒤에 노란 후광
            val glowBrush = Brush.radialGradient(
                colors = listOf(PremiumGlow.copy(alpha = glowAlpha), Color.Transparent),
                center = center,
                radius = rectWidth * 1.2f
            )

            drawCircle(
                brush = glowBrush,
                center = center,
                radius = rectWidth * 0.9f
            )

            // 배경(검정)
            drawRoundRect(
                color = PremiumBlack.copy(alpha = 0.98f),
                topLeft = Offset(rect.left, rect.top),
                size = rectSize,
                cornerRadius = CornerRadius(x = rectHeight / 2f, y = rectHeight / 2f)
            )

            // 얇은 노란 stroke
            drawRoundRect(
                color = PremiumStroke,
                topLeft = Offset(rect.left, rect.top),
                size = rectSize,
                cornerRadius = CornerRadius(x = rectHeight / 2f, y = rectHeight / 2f),
                style = Stroke(width = rectHeight * 0.08f) // 얇게
            )
        }

        // PREMIUM 텍스트
        Text(
            text = "PREMIUM",
            modifier = Modifier.offset(y = bobOffset.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = PremiumYellow,
            maxLines = 1
        )
    }
}
