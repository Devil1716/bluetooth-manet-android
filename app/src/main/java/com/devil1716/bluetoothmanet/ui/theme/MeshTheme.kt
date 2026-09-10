package com.devil1716.bluetoothmanet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MeshBlack = Color(0xFF000000)
val MeshWhite = Color(0xFFFFFFFF)
val MeshMuted = Color(0xFF8E8E93)
val MeshMint = Color(0xFF9ECFC4)
val MeshLavender = Color(0xFFC4B3DC)
val MeshOnline = Color(0xFF30D158)
val MeshSearchFill = Color(0x4DFFFFFF)
val MeshAddCircle = Color(0x66FFFFFF)
val MeshIncoming = Color(0xFF1C1C1E)
val MeshOutgoing = Color(0xFF5E5A8A)
val MeshComposer = Color(0xFF1C1C1E)
val MeshSurface = Color(0xFF161618)
val MeshDanger = Color(0xFFFF453A)
val MeshChipIdle = Color(0x33FFFFFF)
val MeshChipLive = Color(0x3320C997)

val HeaderGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFA8D4C8), Color(0xFFB7C4D6), Color(0xFFC5B4DC)),
    start = Offset.Zero,
    end = Offset(900f, 1100f)
)

private val MeshColorScheme = darkColorScheme(
    primary = MeshMint,
    secondary = MeshLavender,
    background = MeshBlack,
    surface = MeshBlack,
    onPrimary = MeshBlack,
    onSecondary = MeshBlack,
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
