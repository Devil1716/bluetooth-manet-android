package com.devil1716.bluetoothmanet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MeshNavy = Color(0xFF070A14)
val MeshBlack = MeshNavy
val MeshWhite = Color(0xFFFFFFFF)
val MeshMuted = Color(0xFF8B91A8)
val MeshAccent = Color(0xFF8B8CFF)
val MeshAccentDeep = Color(0xFF6B6EFF)
val MeshMint = MeshAccent
val MeshLavender = Color(0xFFC5C0F5)
val MeshOnline = Color(0xFF4ADE80)
val MeshAway = Color(0xFF8B8CFF)
val MeshSearchFill = Color(0x1AFFFFFF)
val MeshAddCircle = Color(0x22FFFFFF)
val MeshIncoming = Color(0xFF1A2033)
val MeshOutgoing = Color(0xFF6B74F0)
val MeshComposer = Color(0xFF151B2C)
val MeshCard = Color(0xFF101628)
val MeshBar = Color(0xFF0C1020)
val MeshSurface = Color(0xFF101628)
val MeshDanger = Color(0xFFFF453A)
val MeshChipIdle = Color(0x33FFFFFF)
val MeshChipLive = Color(0x338B8CFF)

val AccentGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF8B8CFF), Color(0xFF6B74F0), Color(0xFFA78BFA))
)

val BubbleGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF7B82F5), Color(0xFF6B6EFF)),
    start = Offset.Zero,
    end = Offset(400f, 280f)
)

val HeaderGradient = AccentGradient

/** Soft lavender wash behind the inbox hero, fading into navy like the chat mock. */
val HeroGradient = Brush.verticalGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFFC5C0F5),
        0.18f to Color(0xFFA8A9FF),
        0.42f to Color(0xFF8B8CFF),
        0.68f to Color(0xFF3D4278),
        1.00f to Color(0xFF070A14)
    )
)

val HeroSearchFill = Color(0x66FFFFFF)

private val MeshColorScheme = darkColorScheme(
    primary = MeshAccent,
    secondary = MeshLavender,
    background = MeshNavy,
    surface = MeshNavy,
    onPrimary = MeshWhite,
    onSecondary = MeshNavy,
    onBackground = MeshWhite,
    onSurface = MeshWhite
)

@Composable
fun MeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeshColorScheme,
        typography = MaterialTheme.typography.copy(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                color = MeshWhite
            )
        ),
        content = content
    )
}
