package com.kaonixx.guitarix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── KRANK Brand Colors - Hardware Amp Inspired ──
object KrankColors {
    val Bg = Color(0xFF0A0A0E)
    val Surface0 = Color(0xFF121216)
    val Surface1 = Color(0xFF1A1A22)
    val Surface2 = Color(0xFF22222E)
    val Surface3 = Color(0xFF2A2A36)
    val Border = Color(0xFF2A2A3A)
    val BorderDim = Color(0xFF1E1E2A)
    val BorderBright = Color(0xFF3A3A4E)
    val Cyan = Color(0xFF22D3EE)
    val CyanDim = Color(0xFF1BA3BB)
    val CyanGlow = Color(0x5522D3EE)
    val Green = Color(0xFF22C55E)
    val GreenGlow = Color(0x5522C55E)
    val Red = Color(0xFFFF6B6B)
    val RedGlow = Color(0x55FF6B6B)
    val Orange = Color(0xFFF59E0B)
    val OrangeGlow = Color(0x55F59E0B)
    val Primary = Color(0xFFF1F1F5)
    val Secondary = Color(0xFF8888A0)
    val Muted = Color(0xFF555570)
    val Dim = Color(0xFF2E2E3A)
    val White = Color(0xFFFFFFFF)

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

// Hardware panel gradients
object KrankGradients {
    val PanelDark = Brush.verticalGradient(listOf(
        Color(0xFF14141A), Color(0xFF0E0E12)
    ))
    val PanelLight = Brush.verticalGradient(listOf(
        Color(0xFF1E1E28), Color(0xFF181820)
    ))
    val GlassOverlay = Brush.verticalGradient(listOf(
        Color(0x08FFFFFF), Color(0x04FFFFFF)
    ))
    val GlowCyan = Brush.radialGradient(
        listOf(KrankColors.Cyan.copy(alpha = 0.3f), Color.Transparent),
        radius = 1f
    )
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
    onSurfaceVariant = KrankColors.Secondary,
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

// Glass card composable helper
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val accent = KrankColors.Cyan
    val borderColor = if (enabled) KrankColors.BorderBright.copy(alpha = 0.6f) else KrankColors.BorderDim
    val bgColor = if (enabled) KrankColors.Surface1 else KrankColors.Surface0
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

// Glow effect modifier
fun Modifier.glowBorder(
    enabled: Boolean,
    color: Color = KrankColors.Cyan,
    cornerRadius: Dp = 12.dp
): Modifier = this.then(
    if (enabled) {
        Modifier.border(1.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(cornerRadius))
            .border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(cornerRadius + 1.dp))
    } else {
        Modifier.border(1.dp, KrankColors.BorderDim, RoundedCornerShape(cornerRadius))
    }
)
