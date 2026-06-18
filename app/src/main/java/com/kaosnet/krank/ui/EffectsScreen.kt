package com.kaosnet.krank.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaosnet.krank.MainViewModel
import com.kaosnet.krank.KrankEngine

@Composable
@Composable
fun EffectsScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        PresetRow(vm)
        Spacer(Modifier.height(16.dp))
        EffectCards(vm)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun PresetRow(vm: MainViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KrankEngine.presetNames.forEachIndexed { idx, name ->
            val sel = idx == vm.currentPresetIndex
            val bg by animateColorAsState(
                if (sel) KrankColors.Cyan else KrankColors.SurfaceCard,
                spring(Spring.DampingRatioMediumBouncy)
            )
            val tc by animateColorAsState(
                if (sel) KrankColors.Bg else KrankColors.Secondary,
                spring(Spring.DampingRatioMediumBouncy)
            )
            val borderColor by animateColorAsState(
                if (sel) KrankColors.Cyan.copy(alpha = 0.3f) else KrankColors.BorderDim,
                spring(Spring.DampingRatioMediumBouncy)
            )
            Surface(
                onClick = { vm.loadPreset(idx) },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = bg,
                tonalElevation = 0.dp
            ) {
                Box(
                    Modifier.fillMaxSize().border(0.5.dp, borderColor, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = tc, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// ── EFFECT CARDS ──

data class EffectData(val name: String, val enabled: Boolean, val onToggle: () -> Unit, val accent: Color, val params: List<ParamDef>)
data class ParamDef(val label: String, val value: Float, val onChange: (Float) -> Unit)

@Composable
fun EffectCards(vm: MainViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val allEffects = listOf(
            EffectData("Amp Sim", vm.ampSimOn, { vm.toggleAmpSim() }, KrankColors.AmpSim, listOf(
                ParamDef("Drive", vm.ampSimGain, { vm.updateAmpSimGain(it) }),
                ParamDef("Tone", vm.ampSimTone, { vm.updateAmpSimTone(it) }),
                ParamDef("Level", vm.ampSimMaster, { vm.updateAmpSimMaster(it) })
            )),
            EffectData("Distortion", vm.distortionOn, { vm.toggleDistortion() }, KrankColors.Distortion, listOf(
                ParamDef("Drive", vm.distortionDrive, { vm.updateDistortionDrive(it) }),
                ParamDef("Tone", vm.distortionTone, { vm.updateDistortionTone(it) }),
                ParamDef("Level", vm.distortionLevel, { vm.updateDistortionLevel(it) })
            )),
            EffectData("EQ", vm.eqOn, { vm.toggleEq() }, KrankColors.EQ, listOf(
                ParamDef("Bass", vm.eqBass, { vm.updateEqBass(it) }),
                ParamDef("Mid", vm.eqMid, { vm.updateEqMid(it) }),
                ParamDef("Treble", vm.eqTreble, { vm.updateEqTreble(it) })
            )),
            EffectData("Chorus", vm.chorusOn, { vm.toggleChorus() }, KrankColors.Chorus, listOf(
                ParamDef("Rate", vm.chorusRate, { vm.updateChorusRate(it) }),
                ParamDef("Depth", vm.chorusDepth, { vm.updateChorusDepth(it) }),
                ParamDef("Mix", vm.chorusMix, { vm.updateChorusMix(it) })
            )),
            EffectData("Noise Gate", vm.noiseGateOn, { vm.toggleNoiseGate() }, KrankColors.NoiseGate, listOf(
                ParamDef("Threshold", vm.noiseGateThreshold, { vm.updateNoiseGateThreshold(it) }),
                ParamDef("Attack", vm.noiseGateAttack, { vm.updateNoiseGateAttack(it) }),
                ParamDef("Release", vm.noiseGateRelease, { vm.updateNoiseGateRelease(it) })
            )),
            EffectData("Compressor", vm.compressorOn, { vm.toggleCompressor() }, KrankColors.Compressor, listOf(
                ParamDef("Threshold", vm.compressorThreshold, { vm.updateCompressorThreshold(it) }),
                ParamDef("Ratio", vm.compressorRatio, { vm.updateCompressorRatio(it) }),
                ParamDef("Attack", vm.compressorAttack, { vm.updateCompressorAttack(it) }),
                ParamDef("Release", vm.compressorRelease, { vm.updateCompressorRelease(it) })
            )),
            EffectData("Delay", vm.delayOn, { vm.toggleDelay() }, KrankColors.Delay, listOf(
                ParamDef("Mix", vm.delayMix, { vm.updateDelayMix(it) }),
                ParamDef("Feedback", vm.delayFeedback, { vm.updateDelayFeedback(it) }),
                ParamDef("Time", vm.delayMs / 400f, { vm.updateDelayMs(it * 400f) })
            )),
            EffectData("Reverb", vm.reverbOn, { vm.toggleReverb() }, KrankColors.Reverb, listOf(
                ParamDef("Size", vm.reverbRoomSize, { vm.updateReverbRoomSize(it) }),
                ParamDef("Mix", vm.reverbMix, { vm.updateReverbMix(it) })
            ))
        )

        allEffects.forEach { effect ->
            GlassEffectCard(
                name = effect.name,
                enabled = effect.enabled,
                accent = effect.accent,
                onToggle = effect.onToggle,
                params = effect.params
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── GLASS EFFECT CARD ──
@Composable
private fun GlassEffectCard(
    name: String,
    enabled: Boolean,
    accent: Color,
    onToggle: () -> Unit,
    params: List<ParamDef>
) {
    val borderColor by animateColorAsState(
        if (enabled) accent.copy(alpha = 0.25f) else KrankColors.BorderDim,
        spring(Spring.DampingRatioMediumBouncy)
    )
    val bgColor = if (enabled) KrankColors.SurfaceCard else KrankColors.Surface.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(0.5.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column {
            // Top accent bar
            Box(Modifier.fillMaxWidth().height(2.dp).background(if (enabled) accent.copy(alpha = 0.5f) else KrankColors.BorderDim))

            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Glow dot
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (enabled) accent else KrankColors.Muted.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) KrankColors.Primary else KrankColors.Secondary,
                        letterSpacing = 1.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (enabled) "ON" else "OFF",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) accent else KrankColors.Muted,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = accent,
                            checkedTrackColor = accent.copy(alpha = 0.25f),
                            uncheckedThumbColor = KrankColors.Dim,
                            uncheckedTrackColor = KrankColors.BorderDim
                        ),
                        modifier = Modifier.height(20.dp)
                    )
                }
            }

            // Sliders
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                params.forEach { param ->
                    PremiumSlider(label = param.label, value = param.value, onChange = param.onChange, enabled = enabled, accent = accent)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

// ── PREMIUM SLIDER ──
@Composable
private fun PremiumSlider(label: String, value: Float, onChange: (Float) -> Unit, enabled: Boolean, accent: Color) {
    val displayValue = remember(value) {
        when (label) {
            "Time" -> "${(value * 2000).toInt()} ms"
            "Ratio" -> "%.1f:1".format(1f + value * 19f)
            "Threshold" -> "%.0f dB".format(-60f + value * 60f)
            "Drive", "Tone", "Level", "Gain", "Master" -> "${(value * 100).toInt()}"
            "Bass", "Mid", "Treble" -> "${(value * 100).toInt()}"
            else -> "${(value * 100).toInt()}%"
        }
    }
    val activeColor = if (enabled) accent else KrankColors.Muted

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = KrankColors.Secondary, letterSpacing = 0.5.sp)
            Text(displayValue, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = activeColor)
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = KrankColors.BorderDim,
                disabledThumbColor = KrankColors.Muted,
                disabledActiveTrackColor = KrankColors.Muted.copy(alpha = 0.3f),
                disabledInactiveTrackColor = KrankColors.BorderDim
            ),
            modifier = Modifier.height(20.dp)
        )
    }
}
