package com.kaonixx.guitarix.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaonixx.guitarix.MainViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Palette ──
private val Bg          = Color(0xFF0A0A0E)
private val S0          = Color(0xFF121216)  // outer shell
private val S1          = Color(0xFF1A1A22)  // inner card
private val BorderOn    = Color(0xFF2A2A3A)
private val BorderOff   = Color(0xFF2E2E3A)
private val Cyan        = Color(0xFF22D3EE)
private val TPrimary    = Color(0xFFF1F1F5)
private val TSecondary  = Color(0xFF8888A0)
private val TMuted      = Color(0xFF555570)
private val Disabled    = Color(0xFF2E2E3A)

@Composable
fun MainScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Header(vm)
        Spacer(Modifier.height(20.dp))
        PresetRow(vm)
        Spacer(Modifier.height(20.dp))
        EffectCards(vm)
        Spacer(Modifier.height(40.dp))
    }
}

// ── Header ──
@Composable
private fun Header(vm: MainViewModel) {
    val ledCol by animateColorAsState(
        if (vm.isRunning) Color(0xFF22C55E) else TMuted,
        spring(Spring.DampingRatioMediumBouncy)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth().padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(S0)
            .border(1.dp, BorderOn, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(ledCol).then(
                if (vm.isRunning) Modifier.border(1.dp, ledCol.copy(alpha = 0.3f), CircleShape) else Modifier
            ))
            Spacer(Modifier.width(10.dp))
            Text("GUITARIX", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = TPrimary, letterSpacing = 3.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (vm.isRunning) "ON" else "OFF",
                fontSize = 11.sp, fontWeight = FontWeight.Medium,
                color = if (vm.isRunning) Cyan else TMuted, letterSpacing = 1.sp
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = vm.isRunning,
                onCheckedChange = { vm.toggleEngine() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Cyan, checkedTrackColor = Cyan.copy(alpha = 0.4f),
                    uncheckedThumbColor = Color(0xFF3A3A4A), uncheckedTrackColor = Color(0xFF1A1A22)
                )
            )
        }
    }
}

// ── Presets ──
@Composable
private fun PresetRow(vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        vm.presetNames.forEachIndexed { idx, name ->
            val sel = idx == vm.currentPresetIndex
            val bg by animateColorAsState(if (sel) Cyan else S1, spring(Spring.DampingRatioMediumBouncy))
            val tc by animateColorAsState(if (sel) Bg else TSecondary, spring(Spring.DampingRatioMediumBouncy))
            Button(
                onClick = { vm.loadPreset(idx) },
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = tc),
                contentPadding = PaddingValues(0.dp)
            ) { Text(name, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

// ── Effect data helpers ──
private data class Knob(val label: String, val value: () -> Float, val set: (Float) -> Unit)

private fun knobData(vm: MainViewModel): List<EffectFxs> = listOf(
    EffectFxs("Distortion", { vm.distortionOn }, { vm.toggleDistortion() }, listOf(
        Knob("Drive", { vm.distortionDrive }, { vm.updateDistortionDrive(it) }),
        Knob("Tone",  { vm.distortionTone  }, { vm.updateDistortionTone(it) }),
        Knob("Level", { vm.distortionLevel }, { vm.updateDistortionLevel(it) })
    )),
    EffectFxs("Amp Sim", { vm.ampSimOn }, { vm.toggleAmpSim() }, listOf(
        Knob("Gain",   { vm.ampSimGain   }, { vm.updateAmpSimGain(it) }),
        Knob("Tone",   { vm.ampSimTone   }, { vm.updateAmpSimTone(it) }),
        Knob("Master", { vm.ampSimMaster }, { vm.updateAmpSimMaster(it) })
    )),
    EffectFxs("EQ", { vm.eqOn }, { vm.toggleEQ() }, listOf(
        Knob("Bass",   { vm.eqBass   }, { vm.updateEqBass(it) }),
        Knob("Mid",    { vm.eqMid    }, { vm.updateEqMid(it) }),
        Knob("Treble", { vm.eqTreble }, { vm.updateEqTreble(it) })
    )),
    EffectFxs("Chorus", { vm.chorusOn }, { vm.toggleChorus() }, listOf(
        Knob("Rate",  { vm.chorusRate  }, { vm.updateChorusRate(it) }),
        Knob("Depth", { vm.chorusDepth }, { vm.updateChorusDepth(it) }),
        Knob("Mix",   { vm.chorusMix   }, { vm.updateChorusMix(it) })
    )),
    EffectFxs("Delay", { vm.delayOn }, { vm.toggleDelay() }, listOf(
        Knob("Mix",      { vm.delayMix     }, { vm.updateDelayMix(it) }),
        Knob("Feedback", { vm.delayFeedback }, { vm.updateDelayFeedback(it) }),
        Knob("Time",     { vm.delayMs / 2000f }, { vm.updateDelayMs(it * 2000f) })
    )),
    EffectFxs("Reverb", { vm.reverbOn }, { vm.toggleReverb() }, listOf(
        Knob("Size", { vm.reverbRoomSize }, { vm.updateReverbRoomSize(it) }),
        Knob("Mix",  { vm.reverbMix      }, { vm.updateReverbMix(it) })
    ))
)

private data class EffectFxs(
    val name: String, val enabled: () -> Boolean,
    val toggle: () -> Unit, val knobs: List<Knob>
)

@Composable
private fun EffectCards(vm: MainViewModel) {
    knobData(vm).forEach { fx ->
        CardPremium(fx.name, fx.enabled(), fx.toggle, fx.knobs)
        Spacer(Modifier.height(10.dp))
    }
}

// ── Premium double-bezel card ──
@Composable
private fun CardPremium(name: String, enabled: Boolean, onToggle: () -> Unit, knobs: List<Knob>) {
    val accent by animateColorAsState(if (enabled) Cyan else TMuted, spring(Spring.DampingRatioMediumBouncy))
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(20.dp), color = S0
    ) {
        Box(
            Modifier.padding(2.dp).fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(S1)
                .border(1.dp, if (enabled) BorderOn else BorderOff, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(10.dp))
                        Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = TPrimary, letterSpacing = 0.5.sp)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Cyan, checkedTrackColor = Cyan.copy(alpha = 0.4f),
                            uncheckedThumbColor = Color(0xFF3A3A4A), uncheckedTrackColor = Color(0xFF222230)
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    knobs.forEach { knob -> KnobView(knob.label, knob.value(), knob.set, enabled) }
                    if (knobs.size == 2) Spacer(Modifier.width(72.dp))
                }
            }
        }
    }
}

// ── Rotary knob ──
@Composable
private fun KnobView(label: String, value: Float, onChange: (Float) -> Unit, enabled: Boolean) {
    val dsp = remember(value) {
        when {
            label == "Time" -> "${(value * 2000).toInt()}ms"
            label in listOf("Mix","Size","Depth","Rate","Feedback") -> "${(value * 100).toInt()}%"
            label in listOf("Drive","Gain","Level","Master") -> "${(value * 10).toInt()}"
            label in listOf("Tone","Bass","Mid","Treble") -> "${(value * 10).toInt()}"
            else -> "%.2f".format(value)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        // Track accumulated drag delta
        var acc by remember { mutableFloatStateOf(value) }

        LaunchedEffect(value) { acc = value }

        Box(Modifier.size(64.dp)) {
            Canvas(Modifier.size(64.dp).pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures { _, dragAmount ->
                    val sens = 200f  // pixels for full 0..1 sweep
                    acc = (acc - dragAmount / sens).coerceIn(0f, 1f)
                    onChange(acc)
                }
            }) {
                val stroke = 4.dp.toPx()
                val r = (64.dp.toPx() - stroke) / 2f
                val c = Offset(32.dp.toPx(), 32.dp.toPx())
                val sa = 135f
                val sw = 270f

                drawArc(Disabled, sa, sw, false, Offset(stroke/2,stroke/2),
                    Size(64.dp.toPx()-stroke, 64.dp.toPx()-stroke), style=Stroke(stroke, cap=StrokeCap.Round))
                drawArc(if(enabled) Cyan else Disabled, sa, sw * value, false, Offset(stroke/2,stroke/2),
                    Size(64.dp.toPx()-stroke, 64.dp.toPx()-stroke), style=Stroke(stroke, cap=StrokeCap.Round))

                // Tick
                val ang = (sa + sw * value) * PI / 180f
                val inner = r * 0.6f; val outer = r * 0.9f
                drawLine(if(enabled) TPrimary else TMuted,
                    Offset(c.x+cos(ang).toFloat()*inner, c.y+sin(ang).toFloat()*inner),
                    Offset(c.x+cos(ang).toFloat()*outer, c.y+sin(ang).toFloat()*outer),
                    2.5.dp.toPx(), cap=StrokeCap.Round)
                drawCircle(if(enabled) Color(0xFF222230) else Disabled, radius=4.dp.toPx(), center=c)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = TSecondary, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
        Text(dsp, fontSize = 11.sp, color = if(enabled) Cyan else TMuted, fontWeight = FontWeight.Bold)
    }
}
