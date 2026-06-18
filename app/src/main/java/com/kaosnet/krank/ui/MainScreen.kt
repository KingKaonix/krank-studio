package com.kaosnet.krank.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kaosnet.krank.MainViewModel
import com.kaosnet.krank.ui.tuner.TunerScreen
import com.kaosnet.krank.ui.tone_matcher.ToneMatcherScreen
import com.kaosnet.krank.ui.transcribe.TranscribeScreen
import com.kaosnet.krank.ui.EffectsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val navController = rememberNavController()
    var showToolsSheet by remember { mutableStateOf(false) }

    // Ambient background glow
    Box(modifier = Modifier.fillMaxSize().background(KrankColors.Bg).then(Modifier.background(KrankGradients.Bg))) {
        // Ambient glow spots
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(KrankColors.Cyan.copy(alpha = 0.03f), radius = size.width * 0.4f, center = Offset(size.width * 0.7f, size.height * 0.15f))
            drawCircle(KrankColors.Purple.copy(alpha = 0.02f), radius = size.width * 0.5f, center = Offset(size.width * 0.2f, size.height * 0.5f))
        }

        var selectedTab by remember { mutableIntStateOf(0) }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { GlassTopBar(vm) },
            bottomBar = { GlassNavBar(vm, selectedTab) { idx ->
                selectedTab = idx
            }}
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                NavHost(modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    startDestination = "effects"
                ) {
                    composable("effects") { EffectsScreen(vm) }
                    composable("tuner") { TunerScreen(vm) }
                    composable("tone_matcher") { ToneMatcherScreen(vm) }
                    composable("transcribe") { TranscribeScreen(vm) }
                    composable("metronome") { MetronomeScreen(vm) }
                    composable("looper") { LooperScreen(vm) }
                    composable("midi") { MidiScreen(vm) }
                    composable("ble") { BleScreen(vm) }
                    composable("tools") { ToolsScreen(vm) }
                }
            }
        }

        // Tools bottom sheet
        if (showToolsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showToolsSheet = false },
                containerColor = KrankColors.BgGradientTop,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                ToolsSheetContent(vm, onNavigate = { route ->
                    showToolsSheet = false
                    navController.navigate(route) { popUpTo(route) { inclusive = true } }
                })
            }
        }

        // Tab sync
        var initialTab by remember { mutableStateOf(true) }
        LaunchedEffect(vm.currentTab) {
            if (initialTab) { initialTab = false; return@LaunchedEffect }
            val routes = listOf("effects", "tuner", "tone_matcher", "transcribe")
            val route = routes.getOrElse(vm.currentTab) { "effects" }
            navController.navigate(route) { popUpTo(route) { inclusive = true } }
        }
    }
}

// ── GLASS TOP BAR ──
@Composable
private fun GlassTopBar(vm: MainViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(KrankColors.Surface)
            .border(0.5.dp, KrankColors.Border, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand
        Column {
            Text("KRANK", fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = KrankColors.Primary, letterSpacing = 2.sp)
            Text("Studio", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = KrankColors.Secondary, letterSpacing = 1.sp)
        }

        // VU Meter - premium bar
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("IN", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = KrankColors.Muted, letterSpacing = 1.sp)
            VuMeterBar(vm.engine.getInputPeakLevel())
        }

        // Status dot
        Box(Modifier.size(8.dp).clip(CircleShape).background(KrankColors.Green.copy(alpha = 0.8f)))
    }
}

// ── PREMIUM VU METER BAR ──
@Composable
private fun VuMeterBar(level: Float, modifier: Modifier = Modifier) {
    val segments = 16
    val activeSegments = (level.coerceIn(0f, 1f) * segments).toInt().coerceIn(0, segments)

    Row(modifier = modifier.height(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until segments) {
            val isActive = i < activeSegments
            val color = when {
                i < 10 -> if (isActive) KrankColors.Green else KrankColors.BorderDim
                i < 13 -> if (isActive) KrankColors.Orange else KrankColors.BorderDim
                else -> if (isActive) KrankColors.Red else KrankColors.BorderDim
            }
            val alpha = if (isActive) 0.9f else 0.3f
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (i % 4 == 3) 12.dp else 8.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

// ── GLASS BOTTOM NAV ──
@Composable
private fun GlassNavBar(vm: MainViewModel, selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Effects", "Tuner", "Match", "Trans")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(KrankColors.Surface)
            .border(0.5.dp, KrankColors.Border, RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { idx, label ->
            val selected = idx == selectedTab
            val bgColor by animateColorAsState(
                if (selected) KrankColors.SurfaceActive else Color.Transparent,
                spring(Spring.DampingRatioMediumBouncy)
            )
            val textColor by animateColorAsState(
                if (selected) KrankColors.Cyan else KrankColors.Muted,
                spring(Spring.DampingRatioMediumBouncy)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .clickable { onTabSelected(idx) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

// ── METRONOME SCREEN ──
@Composable
fun MetronomeScreen(vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Text("METRONOME", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KrankColors.Secondary, letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp))

        // BPM display
        Text("${vm.metronomeBpm.toInt()}", fontFamily = FontFamily.Monospace, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = KrankColors.Cyan)
        Text("BPM", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = KrankColors.Muted, letterSpacing = 3.sp)
        Spacer(Modifier.height(16.dp))

        // Slider
        Slider(
            value = vm.metronomeBpm,
            onValueChange = { vm.updateMetronomeBpm(it) },
            valueRange = 40f..240f,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = SliderDefaults.colors(thumbColor = KrankColors.Cyan, activeTrackColor = KrankColors.Cyan, inactiveTrackColor = KrankColors.BorderDim)
        )
        Spacer(Modifier.height(16.dp))

        // Tap tempo
        OutlinedButton(
            onClick = { vm.tapTempo() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = KrankColors.Cyan),
            border = androidx.compose.foundation.BorderStroke(1.dp, KrankColors.Cyan.copy(alpha = 0.3f))
        ) {
            Text("TAP TEMPO", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.toggleMetronome() },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (vm.metronomeEnabled) KrankColors.Green else KrankColors.Muted),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (vm.metronomeEnabled) KrankColors.Green.copy(alpha = 0.3f) else KrankColors.BorderDim)
            ) {
                Text(if (vm.metronomeEnabled) "ON" else "OFF", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            if (vm.metronomeEnabled) {
                Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(KrankColors.Cyan.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Text("●", fontSize = 18.sp, color = KrankColors.Cyan.copy(alpha = 0.5f))
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── LOOPER SCREEN ──
@Composable
fun LooperScreen(vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Text("LOOPER", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KrankColors.Secondary, letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp))

        // Mode display
        val modeText = when (vm.looperMode) { 0 -> "IDLE"; 1 -> "RECORDING"; 2 -> "PLAYING"; 3 -> "OVERDUB"; else -> "IDLE" }
        val modeColor = when (vm.looperMode) { 0 -> KrankColors.Muted; 1 -> KrankColors.Red; 2 -> KrankColors.Green; 3 -> KrankColors.Orange; else -> KrankColors.Muted }
        Text(modeText, fontFamily = FontFamily.Monospace, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = modeColor, letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp))

        // Control buttons
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.looperToggleRecord() }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = KrankColors.Red), border = androidx.compose.foundation.BorderStroke(1.dp, KrankColors.Red.copy(alpha = 0.3f))) {
                Text("REC", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            }
            OutlinedButton(onClick = { vm.looperToggleRecord() }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = KrankColors.Green), border = androidx.compose.foundation.BorderStroke(1.dp, KrankColors.Green.copy(alpha = 0.3f))) {
                Text("PLAY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            }
            OutlinedButton(onClick = { vm.looperToggleRecord() }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = KrankColors.Orange), border = androidx.compose.foundation.BorderStroke(1.dp, KrankColors.Orange.copy(alpha = 0.3f))) {
                Text("DUB", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { vm.looperClear() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = KrankColors.Muted), border = androidx.compose.foundation.BorderStroke(1.dp, KrankColors.BorderDim)) {
            Text("CLEAR", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── MIDI SCREEN ──
@Composable
fun MidiScreen(vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Text("MIDI FOOTSWITCH", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KrankColors.Secondary, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth(), enabled = true, accentColor = KrankColors.Cyan) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LEARN MODE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = KrankColors.Cyan, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                Text("Press a MIDI CC to map it", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = KrankColors.Muted)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.toggleMidiLearnMode() }, modifier = Modifier.height(40.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (vm.midiLearnMode) KrankColors.Green else KrankColors.Muted), border = androidx.compose.foundation.BorderStroke(1.dp, if (vm.midiLearnMode) KrankColors.Green.copy(alpha = 0.3f) else KrankColors.BorderDim)) {
                        Text(if (vm.midiLearnMode) "LEARNING..." else "LEARN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth(), enabled = true, accentColor = KrankColors.Purple) {
            Column {
                Text("EFFECT MAPPINGS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = KrankColors.Secondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                listOf("Distortion", "Amp Sim", "EQ", "Chorus", "Noise Gate", "Compressor", "Delay", "Reverb").forEachIndexed { idx, name ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = KrankColors.Primary)
                        Text("CC#$idx", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = KrankColors.Secondary)
                    }
                    if (idx < 7) Divider(color = KrankColors.BorderDim, thickness = 0.5.dp)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── BLE Screen ──
@Composable
fun BleScreen(vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCard(Modifier.fillMaxWidth(), enabled = true, accentColor = if (vm.bleConnected) KrankColors.Cyan else KrankColors.Muted) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Bluetooth, null, tint = if (vm.bleConnected) KrankColors.Cyan else KrankColors.Muted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("BLE FOOT CONTROLLER", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KrankColors.Primary, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(if (vm.bleConnected) "Connected to ${vm.bleDeviceName}" else "Not connected", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (vm.bleConnected) KrankColors.Green else KrankColors.Secondary)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.updateBleScanning(true) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = KrankColors.Cyan), border = androidx.compose.foundation.BorderStroke(1.dp, KrankColors.Cyan.copy(alpha = 0.3f))) {
                        Text("SCAN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                    OutlinedButton(onClick = { vm.setBleConnected(false) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = KrankColors.Muted), border = androidx.compose.foundation.BorderStroke(1.dp, KrankColors.BorderDim)) {
                        Text("DISCONNECT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (vm.bleScanning) {
            GlassCard(Modifier.fillMaxWidth(), enabled = true, accentColor = KrankColors.Cyan) {
                Column {
                    Text("NEARBY DEVICES", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = KrankColors.Secondary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Scanning...", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = KrankColors.Muted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth(), enabled = false) {
            Column {
                Text("COMPATIBLE DEVICES", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = KrankColors.Secondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Text("• AirTurn BT-200/500", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = KrankColors.Muted)
                Text("• PageFlip Firefly", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = KrankColors.Muted)
                Text("• iRig BlueTurn", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = KrankColors.Muted)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── Tools Screen ──
@Composable
private fun ToolsScreen(vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("TOOLS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KrankColors.Secondary, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth(), enabled = true, accentColor = KrankColors.Cyan) {
            Text("USE THE MORE BUTTON IN THE TOOLS SHEET", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = KrankColors.Muted)
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── TOOLS SHEET ──
@Composable
private fun ToolsSheetContent(vm: MainViewModel, onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("TOOLS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = KrankColors.Secondary, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))
        listOf(
            "metronome" to "METRONOME",
            "looper" to "LOOPER",
            "midi" to "MIDI FOOTSWITCH",
            "ble" to "BLE CONTROLLER"
        ).forEach { (route, label) ->
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(KrankColors.SurfaceCard).clickable { onNavigate(route) },
                contentAlignment = Alignment.CenterStart
            ) {
                Text("  $label", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = KrankColors.Primary, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}
