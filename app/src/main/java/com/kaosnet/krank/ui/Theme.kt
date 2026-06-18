package com.kaosnet.krank.ui

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

// ── KRANK Premium Color Palette - Glass / High-End ──
object KrankColors {
    val Bg = Color(0xFF080810)
    val BgGradientTop = Color(0xFF0C0C1A)
    val BgGradientBottom = Color(0xFF06060D)
    val Surface = Color(0x0CFFFFFF)   // glass base
    val SurfaceElevated = Color(0x10FFFFFF)
    val SurfaceCard = Color(0x0FFFFFFF)
    val SurfaceActive = Color(0x18FFFFFF)
    val Border = Color(0x0FFFFFFF)
    val BorderBold = Color(0x1AFFFFFF)
    val BorderDim = Color(0x08FFFFFF)
    val Cyan = Color(0xFF22D3EE)
    val CyanGlow = Color(0x3322D3EE)
    val CyanDim = Color(0xFF1BA3BB)
    val Green = Color(0xFF22C55E)
    val GreenGlow = Color(0x3322C55E)
    val Red = Color(0xFFEF4444)
    val RedGlow = Color(0x33EF4444)
    val Orange = Color(0xFFF59E0B)
    val Purple = Color(0xFFA78BFA)
    val Pink = Color(0xFFF472B6)
    val Yellow = Color(0xFFF9E79F)
    val Primary = Color(0xFFF1F1F5)
    val Secondary = Color(0xFF8888A0)
    val Muted = Color(0xFF555570)
    val Dim = Color(0xFF2E2E3A)

    // Effect accent colors - premium muted palette
    val Distortion = Color(0xFFEF4444)
    val AmpSim = Color(0xFF14B8A6)
    val EQ = Color(0xFF3B82F6)
    val Chorus = Color(0xFF84CC16)
    val NoiseGate = Color(0xFFA78BFA)
    val Compressor = Color(0xFFF472B6)
    val Delay = Color(0xFFF59E0B)
    val Reverb = Color(0xFFF9E79F)
}

// Premium gradients
object KrankGradients {
    val Bg = Brush.verticalGradient(listOf(KrankColors.BgGradientTop, KrankColors.BgGradientBottom))
    val GlassOverlay = Brush.verticalGradient(listOf(Color(0x0CFFFFFF), Color(0x04FFFFFF)))
    val GlowCyan = Brush.radialGradient(
        listOf(KrankColors.Cyan.copy(alpha = 0.12f), Color.Transparent),
        radius = 1f
    )
    val GlowPurple = Brush.radialGradient(
        listOf(KrankColors.Purple.copy(alpha = 0.08f), Color.Transparent),
        radius = 1f
    )
}

private val KrankColorScheme = darkColorScheme(
    primary = KrankColors.Cyan,
    secondary = KrankColors.Secondary,
    tertiary = KrankColors.Green,
    background = KrankColors.Bg,
    surface = KrankColors.Surface,
    surfaceVariant = KrankColors.SurfaceCard,
    onPrimary = KrankColors.Bg,
    onSecondary = KrankColors.Primary,
    onBackground = KrankColors.Primary,
    onSurface = KrankColors.Primary,
    onSurfaceVariant = KrankColors.Secondary,
    outline = KrankColors.Border.copy(alpha = 0.3f)
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

// Glass card composable
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = KrankColors.Cyan,
    content: @Composable () -> Unit
) {
    val borderColor = if (enabled) accentColor.copy(alpha = 0.2f) else KrankColors.BorderDim
    val cardBg = if (enabled) KrankColors.SurfaceCard else KrankColors.Surface.copy(alpha = 0.3f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(0.5.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

// Glow border modifier
fun Modifier.glowBorder(
    enabled: Boolean,
    color: Color = KrankColors.Cyan,
    cornerRadius: Dp = 12.dp
): Modifier = this.then(
    if (enabled) {
        Modifier.border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(cornerRadius))
            .border(0.5.dp, color.copy(alpha = 0.15f), RoundedCornerShape(cornerRadius + 1.dp))
    } else {
        Modifier.border(0.5.dp, KrankColors.BorderDim, RoundedCornerShape(cornerRadius))
    }
)
