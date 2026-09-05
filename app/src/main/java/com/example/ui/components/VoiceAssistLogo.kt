package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas

/**
 * Material You Native Dynamic Logo for Voice Assist.
 * Renders the central microphone capsule, rounded cradle, acoustic arc, and AI sparkle
 * strictly conforming to Material Design 3 and Android Themed Icons standards.
 */
@Composable
fun VoiceAssistLogo(
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
    showContainer: Boolean = true
) {
    val cornerRadius = size * 0.28f
    val baseModifier = if (showContainer) {
        modifier
            .size(size)
            .shadow(4.dp, RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
    } else {
        modifier.size(size)
    }

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            val w = this.size.width
            val h = this.size.height
            val scale = w / 108f

            // 1. Minimalist Microphone Capsule
            val capLeft = 46f * scale
            val capTop = 39f * scale
            val capWidth = 16f * scale
            val capHeight = 23f * scale
            val capRadius = 8f * scale
            drawRoundRect(
                color = iconColor,
                topLeft = Offset(capLeft, capTop),
                size = Size(capWidth, capHeight),
                cornerRadius = CornerRadius(capRadius, capRadius)
            )

            // 2. Slender Cradle & Stand
            val cradlePath = Path().apply {
                moveTo(41f * scale, 51f * scale)
                cubicTo(
                    41f * scale, 58.5f * scale,
                    46.8f * scale, 66f * scale,
                    54f * scale, 66f * scale
                )
                cubicTo(
                    61.2f * scale, 66f * scale,
                    67f * scale, 58.5f * scale,
                    67f * scale, 51f * scale
                )
                moveTo(54f * scale, 66f * scale)
                lineTo(54f * scale, 73f * scale)
                moveTo(48f * scale, 73f * scale)
                lineTo(60f * scale, 73f * scale)
            }
            drawPath(
                path = cradlePath,
                color = iconColor,
                style = Stroke(
                    width = 2.8f * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 3. Subtle AI 4-point Sparkle Accent
            val sparklePath = Path().apply {
                moveTo(69f * scale, 34f * scale)
                cubicTo(69f * scale, 36.5f * scale, 71f * scale, 38.5f * scale, 73.5f * scale, 38.5f * scale)
                cubicTo(71f * scale, 38.5f * scale, 69f * scale, 40.5f * scale, 69f * scale, 43f * scale)
                cubicTo(69f * scale, 40.5f * scale, 67f * scale, 38.5f * scale, 64.5f * scale, 38.5f * scale)
                cubicTo(67f * scale, 38.5f * scale, 69f * scale, 36.5f * scale, 69f * scale, 34f * scale)
                close()
            }
            drawPath(
                path = sparklePath,
                color = accentColor
            )
        }
    }
}
