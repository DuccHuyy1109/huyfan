package com.hankukko.huyfan.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hankukko.huyfan.assistant.Phase
import com.hankukko.huyfan.ui.theme.Brand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Quả cầu năng lượng phản ứng theo trạng thái: chờ – nghe – nghĩ – nói. */
@Composable
fun VoiceOrb(phase: Phase, level: Float, modifier: Modifier = Modifier, size: Dp = 160.dp) {
    val inf = rememberInfiniteTransition(label = "orb")
    val spin by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (phase == Phase.THINKING) 1600 else 7000, easing = LinearEasing)),
        label = "spin",
    )
    val breathe by inf.animateFloat(
        0.93f, 1.05f,
        infiniteRepeatable(tween(if (phase == Phase.SPEAKING) 520 else 1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val lvl by animateFloatAsState(level, spring(stiffness = Spring.StiffnessMediumLow), label = "lvl")

    val (a, b, d) = when (phase) {
        Phase.LISTENING -> Triple(Brand.Cyan, Brand.Blue, Color(0xFF9FF3FF))
        Phase.THINKING -> Triple(Brand.Violet, Brand.Cyan, Color(0xFFF0ABFC))
        Phase.SPEAKING -> Triple(Brand.Pink, Brand.Violet, Color(0xFFFFB4D9))
        Phase.GREETING -> Triple(Brand.Teal, Brand.Cyan, Color(0xFFA7F3D0))
        Phase.IDLE -> Triple(Brand.Pink, Brand.Violet, Color(0xFF8FEFFF))
    }
    val c1 by animateColorAsState(a, tween(500), label = "c1")
    val c2 by animateColorAsState(b, tween(500), label = "c2")
    val c3 by animateColorAsState(d, tween(500), label = "c3")

    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val center = this.center
        val scale = when (phase) {
            Phase.LISTENING -> 0.86f + lvl * 0.22f
            Phase.SPEAKING -> breathe
            Phase.THINKING -> 0.92f
            else -> 0.88f + (breathe - 0.93f) * 0.6f
        }

        // Quầng sáng ngoài
        drawCircle(
            brush = Brush.radialGradient(listOf(c1.copy(alpha = 0.38f), Color.Transparent), center, r),
            radius = r, center = center,
        )

        // 3 "đốm" xoay tạo cảm giác chất lỏng
        val colors = listOf(c1, c2, c3)
        for (i in 0 until 3) {
            val ang = (spin + i / 3f) * 2f * PI.toFloat()
            val off = Offset(cos(ang), sin(ang)) * (r * 0.13f)
            val cc = center + off
            drawCircle(
                brush = Brush.radialGradient(listOf(colors[i].copy(alpha = 0.85f), colors[i].copy(alpha = 0f)), cc, r * 0.62f * scale),
                radius = r * 0.62f * scale, center = cc,
            )
        }

        // Lõi
        drawCircle(
            brush = Brush.linearGradient(listOf(c1, c2), center - Offset(r, r) * 0.5f, center + Offset(r, r) * 0.5f),
            radius = r * 0.44f * scale, center = center,
        )
        // Điểm sáng
        val hl = center + Offset(-r * 0.14f, -r * 0.16f)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), hl, r * 0.26f * scale),
            radius = r * 0.26f * scale, center = hl,
        )

        // Vòng sóng khi đang nghe
        if (phase == Phase.LISTENING) {
            drawCircle(c3.copy(alpha = 0.55f), radius = r * (0.56f + lvl * 0.34f), center = center, style = Stroke(2.dp.toPx()))
            drawCircle(c3.copy(alpha = 0.25f), radius = r * (0.66f + lvl * 0.3f), center = center, style = Stroke(1.5.dp.toPx()))
        }
        if (phase == Phase.THINKING) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, c3, Color.Transparent), center),
                startAngle = spin * 360f, sweepAngle = 140f, useCenter = false,
                topLeft = center - Offset(r * 0.6f, r * 0.6f),
                size = androidx.compose.ui.geometry.Size(r * 1.2f, r * 1.2f),
                style = Stroke(3.dp.toPx()),
            )
        }
    }
}
