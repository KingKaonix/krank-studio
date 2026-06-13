package com.kaonixx.guitarix.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── KRANK Brand Colors ──
object KrankColors {
    val Bg = Color(0xFF0A0A0E)
    val Surface0 = Color(0xFF121216)
    val Surface1 = Color(0xFF1A1A22)
    val Surface2 = Color(0xFF22222E)
    val Border = Color(0xFF2A2A3A)
    val BorderDim = Color(0xFF1E1E2A)
    val Cyan = Color(0xFF22D3EE)
    val CyanDim = Color(0xFF1BA3BB)
    val Green = Color(0xFF22C55E)
    val Red = Color(0xFFFF6B6B)
    val Orange = Color(0xFFF59E0B)
    val Primary = Color(0xFFF1F1F5)
    val Secondary = Color(0xFF8888A0)
    val Muted = Color(0xFF555570)
    val Dim = Color(0xFF2E2E3A)

    // Effect accent colors
    val Distortion = Color(0xFFFF6B6B)
    val AmpSim = Color(0xFF4ECDC4)
    val EQ = Color(0xFF45B7D1)
    val Chorus = Color(0xFF96CEB4)
    val NoiseGate = Color(0xFFA78BFA)
    val Compressor = Color(0xFFF472B6)
    val Delay = Color(0xFFF9E79F)
    val Reverb = Color(0xFFFFD700)
}

private val KrankColorScheme = darkColorScheme(
    primary = KrankColors.Cyan,
    secondary = KrankColors.Secondary,
    tertiary = KrankColors.Green,
    background = KrankColors.Bg,
    surface = KrankColors.Surface1,
    surfaceVariant = KrankColors.Surface0,
    onPrimary = KrankColors.Bg,
    onSecondary = KrankColors.Primary,
    onBackground = KrankColors.Primary,
    onSurface = KrankColors.Primary,
    outline = KrankColors.Border
)

@Composable
fun KrankTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KrankColorScheme,
        content = content
    )
}

// Effect → accent color mapping
fun effectColor(name: String): Color = when (name) {
    "Distortion" -> KrankColors.Distortion
    "Amp Sim" -> KrankColors.AmpSim
    "EQ" -> KrankColors.EQ
    "Chorus" -> KrankColors.Chorus
    "Noise Gate" -> KrankColors.NoiseGate
    "Compressor" -> KrankColors.Compressor
    "Delay" -> KrankColors.Delay
    "Reverb" -> KrankColors.Reverb
    else -> KrankColors.Cyan
}
