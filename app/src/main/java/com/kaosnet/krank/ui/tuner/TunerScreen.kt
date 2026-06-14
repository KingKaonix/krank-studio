package com.kaosnet.krank.ui.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaosnet.krank.MainViewModel

private val Bg         = Color(0xFF0A0A0E)
private val S1         = Color(0xFF1A1A22)
private val S2         = Color(0xFF22222E)
private val Border     = Color(0xFF2A2A3A)
private val Cyan       = Color(0xFF22D3EE)
private val CyanDim    = Color(0xFF1BA3BB)
private val Green      = Color(0xFF22C55E)
private val TPrimary   = Color(0xFFF1F1F5)
private val TSecondary = Color(0xFF8888A0)
private val TMuted     = Color(0xFF555570)
private val tuningNames = listOf("Standard", "Drop D", "Drop C", "Open D", "Open G", "Open E", "DADGAD",
    "Half-Step Down", "Full-Step Down", "Drop B", "Open A", "Custom")

@Composable
fun TunerScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        // Section header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(S1)
                .border(1.dp, Border, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CHROMATIC TUNER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TSecondary,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (vm.isTunerNoteDetected) Green else TMuted)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        TuningPresetSelector(vm)
        Spacer(Modifier.height(24.dp))
        HardwareTunerDisplay(vm)
        Spacer(Modifier.height(24.dp))
        TunerInfoRow(vm)
        Spacer(Modifier.height(24.dp))
        Instructions()
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TuningPresetSelector(vm: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            "TUNING",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TSecondary,
            letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Border)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TPrimary,
                    containerColor = S1
                )
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tuningNames[vm.tunerCurrentTuning],
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "▼",
                        fontSize = 16.sp,
                        color = Cyan,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(S1)
                    .border(1.dp, Border, RoundedCornerShape(10.dp))
            ) {
                tuningNames.forEachIndexed { i, name ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                name,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (i == vm.tunerCurrentTuning) Cyan else TPrimary
                            )
                        },
                        onClick = { vm.setTunerTuning(i); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareTunerDisplay(vm: MainViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(S1)
            .border(2.dp, Border, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glowing note display
            if (vm.isTunerNoteDetected) {
                val noteName = if (vm.tunerNoteIndex >= 0) {
                    runCatching { vm.engine.getTunerNoteName(vm.tunerNoteIndex) }.getOrDefault("?")
                } else "?"

                val infiniteTransition = rememberInfiniteTransition(label = "noteGlow")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                // Note display with glow
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(S2)
                        .border(2.dp, Cyan.copy(alpha = glowAlpha * 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner glow
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(Cyan.copy(alpha = glowAlpha * 0.08f))
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            noteName,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = Cyan,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "OCT ${vm.tunerOctave}",
                            fontSize = 12.sp,
                            color = CyanDim,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            } else {
                // No signal display
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(S2)
                        .border(2.dp, Border, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "--",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = TMuted,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "SIGNAL",
                            fontSize = 10.sp,
                            color = TMuted,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Frequency display
            Text(
                if (vm.isTunerNoteDetected) "%.1f Hz".format(vm.tunerFrequency) else "--- Hz",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(16.dp))

            // Needle-style cents indicator
            CentsIndicator(vm.tunerCents, vm.isTunerNoteDetected)
        }
    }
}

@Composable
private fun CentsIndicator(cents: Float, detected: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "CENTS",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TSecondary,
            letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(6.dp))

        val normalized = (cents.coerceIn(-50f, 50f) / 50f).coerceIn(-1f, 1f)
        val needleColor = when {
            !detected -> TMuted
            kotlin.math.abs(cents) < 2f -> Green
            kotlin.math.abs(cents) < 10f -> Color(0xFFF59E0B)
            else -> Color(0xFFFF6B6B)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(S2)
        ) {
            // Tick marks
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerX = w / 2f

                // Background bar segments
                val segColors = listOf(
                    Color(0xFFFF6B6B).copy(alpha = 0.2f),
                    Color(0xFFF59E0B).copy(alpha = 0.2f),
                    Green.copy(alpha = 0.2f),
                    Green.copy(alpha = 0.2f),
                    Color(0xFFF59E0B).copy(alpha = 0.2f),
                    Color(0xFFFF6B6B).copy(alpha = 0.2f)
                )
                val segW = w / 6f
                for (i in 0..5) {
                    drawRect(
                        color = segColors[i],
                        topLeft = Offset(i * segW, 0f),
                        size = Size(segW, h)
                    )
                }

                // Center line
                drawLine(
                    TSecondary.copy(alpha = 0.5f),
                    Offset(centerX, 0f),
                    Offset(centerX, h),
                    strokeWidth = 1.dp.toPx()
                )

                // Needle
                val needleX = centerX + normalized * (w / 2f - 12.dp.toPx())
                drawLine(
                    needleColor,
                    Offset(needleX, 4.dp.toPx()),
                    Offset(needleX, h - 4.dp.toPx()),
                    3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Needle center dot
                drawCircle(needleColor, radius = 4.dp.toPx(), center = Offset(needleX, h / 2f))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "%+.0f¢".format(cents),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = needleColor,
            fontFamily = FontFamily.Monospace
        )

        if (detected && kotlin.math.abs(cents) < 2f) {
            Spacer(Modifier.height(4.dp))
            Text(
                "✓ IN TUNE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Green,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
        }
    }
}

@Composable
private fun TunerInfoRow(vm: MainViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = S1),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "STRING",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TSecondary,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "--",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = S1),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "NOTE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = TSecondary,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (vm.isTunerNoteDetected && vm.tunerNoteIndex >= 0)
                        runCatching { vm.engine.getTunerNoteName(vm.tunerNoteIndex) }.getOrDefault("-")
                    else "-",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (vm.isTunerNoteDetected) Cyan else TMuted,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun Instructions() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = S1.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "TIPS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TSecondary,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "• Select a tuning preset matching your guitar setup",
                    fontSize = 11.sp,
                    color = TSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
                Text(
                    "• Play a single string cleanly for best accuracy",
                    fontSize = 11.sp,
                    color = TSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
                Text(
                    "• Tune until the needle centers and \"IN TUNE\" appears",
                    fontSize = 11.sp,
                    color = TSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "* Best with clean sustained notes in quiet environment",
                    fontSize = 10.sp,
                    color = TMuted,
                    fontFamily = FontFamily.Monospace,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}
