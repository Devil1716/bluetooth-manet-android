package com.devil1716.bluetoothmanet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devil1716.bluetoothmanet.ui.theme.AccentGradient
import com.devil1716.bluetoothmanet.ui.theme.MeshNavy
import com.devil1716.bluetoothmanet.ui.theme.MeshWhite

@Composable
fun MeshAtmosphere(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.background(MeshNavy)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x668B8CFF), Color(0x00070A14)),
                    center = Offset(size.width * 0.92f, size.height * 0.08f),
                    radius = size.minDimension * 0.85f
                ),
                radius = size.minDimension * 0.85f,
                center = Offset(size.width * 0.92f, size.height * 0.08f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x334F46E5), Color(0x00070A14)),
                    center = Offset(size.width * 0.1f, size.height * 0.95f),
                    radius = size.minDimension * 0.7f
                ),
                radius = size.minDimension * 0.7f,
                center = Offset(size.width * 0.1f, size.height * 0.95f)
            )
        }
        content()
    }
}

@Composable
fun MeshMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val left = leftLobe(w, h)
        val right = rightLobe(w, h)
        drawPath(
            left,
            Brush.linearGradient(
                colors = listOf(Color(0xFFC5CBFF), Color(0xFF7B82F5), Color(0xFF5B4FE0)),
                start = Offset(w * 0.22f, h * 0.08f),
                end = Offset(w * 0.55f, h * 0.95f)
            )
        )
        drawPath(
            right,
            Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFE8E6FF), Color(0xFFB8B4E8)),
                start = Offset(w * 0.62f, h * 0.05f),
                end = Offset(w * 0.78f, h * 0.92f)
            )
        )
    }
}

@Composable
fun MeshWordmark(
    modifier: Modifier = Modifier,
    color: Color = MeshWhite,
    fontSize: TextUnit = 28.sp,
    letterSpacing: Dp = 10.dp
) {
    val barHeight = (fontSize.value * 0.72f).dp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(letterSpacing)
    ) {
        MeshLetter("M", color, fontSize)
        MeshEMark(color = color, height = barHeight)
        MeshLetter("S", color, fontSize)
        MeshLetter("H", color, fontSize)
    }
}

@Composable
private fun MeshLetter(letter: String, color: Color, fontSize: TextUnit) {
    Text(
        text = letter,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Light,
        letterSpacing = 0.sp
    )
}

@Composable
private fun MeshEMark(color: Color, height: Dp) {
    Canvas(Modifier.width(height * 0.72f).height(height)) {
        val barH = size.height * 0.13f
        val radius = barH / 2f
        listOf(0.08f, 0.435f, 0.79f).forEach { top ->
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, size.height * top),
                size = Size(size.width, barH),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

@Composable
fun MeshPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(AccentGradient)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text, color = MeshWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MeshWhite,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun DrawScope.leftLobe(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.20f, h * 0.26f)
    cubicTo(w * 0.20f, h * 0.08f, w * 0.40f, h * 0.04f, w * 0.50f, h * 0.18f)
    cubicTo(w * 0.60f, h * 0.34f, w * 0.68f, h * 0.58f, w * 0.62f, h * 0.76f)
    cubicTo(w * 0.56f, h * 0.92f, w * 0.38f, h * 0.90f, w * 0.28f, h * 0.80f)
    cubicTo(w * 0.12f, h * 0.64f, w * 0.10f, h * 0.44f, w * 0.20f, h * 0.26f)
    close()
}

private fun DrawScope.rightLobe(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.80f, h * 0.26f)
    cubicTo(w * 0.80f, h * 0.08f, w * 0.60f, h * 0.04f, w * 0.50f, h * 0.18f)
    cubicTo(w * 0.40f, h * 0.34f, w * 0.32f, h * 0.58f, w * 0.38f, h * 0.76f)
    cubicTo(w * 0.44f, h * 0.92f, w * 0.62f, h * 0.90f, w * 0.72f, h * 0.80f)
    cubicTo(w * 0.88f, h * 0.64f, w * 0.90f, h * 0.44f, w * 0.80f, h * 0.26f)
    close()
}
