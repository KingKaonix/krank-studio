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

private val Bg          = Color(0xFF0A0A0E)
private val S0          = Color(0xFF121216)
private val S1          = Color(0xFF1A1A22)
private val S2          = Color(0xFF22222E)
private val S3          = Color(0xFF2A2A36)
private val BorderOn    = Color(0xFF2A2A3A)
private val BorderDim   = Color(0xFF1E1E2A)
private val Cyan        = Color(0xFF22D3EE)
private val TPrimary    = Color(0xFFF1F1F5)
private val TSecondary  = Color(0xFF8888A0)
private val TMuted      = Color(0xFF555570)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val navController = rememberNavController()
    var showToolsSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        topBar = { HardwareTopBar(vm) },
        bottomBar = { HardwareNavBar(vm, onToolsClick = { showToolsSheet = true }) }
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
            containerColor = S1,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            ToolsSheetContent(vm, onNavigate = { route ->
                showToolsSheet = false
                navController.navigate(route) { popUpTo(route) { inclusive = true } }
            })
        }
    }

    var initialTab by remember { mutableStateOf(true) }
    LaunchedEffect(vm.currentTab) {
        if (initialTab) { initialTab = false; return@LaunchedEffect }
        val routes = listOf("effects", "tuner", "tone_matcher", "transcribe")
        val route = routes.getOrElse(vm.currentTab) { "effects" }
        navController.navigate(route) { popUpTo(route) { inclusive = true } }
    }
}

// ── VU METER COMPOSABLE ──
@Composable
private fun VuMeter(level: Float, modifier: Modifier = Modifier) {
    val dbLevel = if (level > 0.001f) (20.0f * kotlin.math.log10(level.toDouble())).toFloat() + 40.0f else 0.0f
    val displayLevel = (dbLevel / 40.0f).coerceIn(0f, 1f)
    val segments = 12
    val activeSegments = (displayLevel * segments).toInt().coerceIn(0, segments)

    Box(modifier = modifier.width(72.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(S2.copy(alpha = 0.8f))) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            for (i in 0 until segments) {
                val segColor = when {
                    i >= segments - 1 -> Color(0xFFFF6B6B)
                    i >= segments - 2 -> Color(0xFFF59E0B)
                    else -> Color(0xFF22C55E)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (i % 2 == 0) 12.dp else 8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (i < activeSegments) segColor else Color(0xFF2A2A36))
                )
            }
        }
    }
}

// ── TOP BAR ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HardwareTopBar(vm: MainViewModel) {
    val ledCol by animateColorAsState(if (vm.isRunning) Color(0xFF22C55E) else TMuted, spring(Spring.DampingRatioMediumBouncy))
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(ledCol))
                Spacer(Modifier.width(8.dp))
                Text("KRANK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TPrimary, letterSpacing = 4.sp, fontSize = 18.sp)
                Spacer(Modifier.width(4.dp))
                Text("STUDIO", fontFamily = FontFamily.Monospace, color = TSecondary, letterSpacing = 2.sp, fontSize = 7.sp)
            }
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // VU Meter
                if (vm.isRunning) {
                    VuMeter(level = vm.inputPeakLevel)
                    Spacer(Modifier.width(12.dp))
                }
                // Power LED + switch
                Text(if (vm.isRunning) "ON" else "OFF", fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (vm.isRunning) Color(0xFF22C55E) else TMuted, letterSpacing = 1.5.sp)
                Spacer(Modifier.width(6.dp))
                Switch(checked = vm.isRunning, onCheckedChange = { vm.toggleEngine() },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF22C55E), checkedTrackColor = Color(0xFF22C55E).copy(alpha = 0.3f), uncheckedThumbColor = Color(0xFF3A3A4A), uncheckedTrackColor = Color(0xFF1A1A22)),
                    modifier = Modifier.padding(end = 12.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = S0, titleContentColor = TPrimary)
    )
}

// ── BOTTOM NAV (4 primary tabs + tools) ──
@Composable
private fun HardwareNavBar(vm: MainViewModel, onToolsClick: () -> Unit) {
    NavigationBar(
        containerColor = S0, tonalElevation = 0.dp,
        modifier = Modifier.height(64.dp).border(1.dp, BorderDim, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        val primaryTabs = listOf(
            TabDef(0, "EFFECTS", Icons.Filled.Tune, Cyan),
            TabDef(1, "TUNER", Icons.Filled.Search, Color(0xFF22C55E)),
            TabDef(2, "TONE MATCH", Icons.Filled.Star, Color(0xFFF59E0B)),
            TabDef(3, "TRANSCRIBE", Icons.Filled.MusicNote, Color(0xFFA78BFA)),
        )
        primaryTabs.forEach { tab ->
            val selected = vm.currentTab == tab.index
            val itemBg by animateColorAsState(if (selected) tab.color.copy(alpha = 0.15f) else Color.Transparent, spring(Spring.DampingRatioMediumBouncy))
            val iconTint by animateColorAsState(if (selected) tab.color else TMuted, spring(Spring.DampingRatioMediumBouncy))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp, horizontal = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(itemBg)
                    .clickable { vm.setTab(tab.index) }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = tab.icon, contentDescription = tab.label, tint = iconTint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = iconTint, letterSpacing = 0.8.sp, maxLines = 1)
                }
            }
        }

        // Tools button
        val toolsColor = Color(0xFF8888A0)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp, horizontal = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(S1.copy(alpha = 0.5f))
                .clickable { onToolsClick() }
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).background(S2),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, null, tint = toolsColor, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text("MORE", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = toolsColor, letterSpacing = 0.8.sp, maxLines = 1)
            }
        }
    }
}

private data class TabDef(val index: Int, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

// ── TOOLS BOTTOM SHEET ──
@Composable
private fun ToolsSheetContent(vm: MainViewModel, onNavigate: (String) -> Unit) {
    val tools = listOf(
        ToolItem("Metronome", Icons.Filled.Timer, Color(0xFFFF6B6B), "metronome"),
        ToolItem("Looper", Icons.Filled.Repeat, Color(0xFF4ECDC4), "looper"),
        ToolItem("MIDI", Icons.Filled.SettingsInputComponent, Color(0xFFA78BFA), "midi"),
        ToolItem("BLE", Icons.Filled.Bluetooth, Color(0xFF22D3EE), "ble"),
    )

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("TOOLS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TSecondary, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tools.take(2).forEach { tool ->
                ToolButton(tool, onNavigate, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            tools.drop(2).forEach { tool ->
                ToolButton(tool, onNavigate, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))

        // Preset save/load inline
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = S2),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Save, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("PRESETS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TPrimary, letterSpacing = 1.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    SmallButton("SAVE", Color(0xFFF59E0B)) {
                        val path = context.getExternalFilesDir(null)?.absolutePath + "/preset_${vm.currentPresetIndex}.json"
                        vm.engine.savePresetToFile(path, vm.currentPresetIndex)
                    }
                    SmallButton("LOAD", Cyan) {
                        val path = context.getExternalFilesDir(null)?.absolutePath + "/preset_${vm.currentPresetIndex}.json"
                        vm.engine.loadPresetFromFile(path)
                        vm.syncParamsFromPreset(vm.currentPresetIndex)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // Tuner mute & Monitor toggles
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = S2),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Hearing, null, tint = if (vm.tunerMuteDry) Color(0xFFFF6B6B) else TMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("TUNER MUTE", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TPrimary, letterSpacing = 0.5.sp)
                    }
                    Switch(checked = vm.tunerMuteDry, onCheckedChange = { vm.toggleTunerMuteDry() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFF6B6B), checkedTrackColor = Color(0xFFFF6B6B).copy(alpha = 0.3f)))
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Headphones, null, tint = if (vm.monitoringEnabled) Color(0xFF22C55E) else TMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("MONITOR", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TPrimary, letterSpacing = 0.5.sp)
                    }
                    Switch(checked = vm.monitoringEnabled, onCheckedChange = { vm.toggleMonitoring() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF22C55E), checkedTrackColor = Color(0xFF22C55E).copy(alpha = 0.3f)))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class ToolItem(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color, val route: String)

@Composable
private fun ToolButton(tool: ToolItem, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = { onNavigate(tool.route) },
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tool.color, containerColor = tool.color.copy(alpha = 0.05f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(tool.icon, null, modifier = Modifier.size(20.dp), tint = tool.color)
            Spacer(Modifier.height(2.dp))
            Text(tool.name, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = tool.color, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun SmallButton(text: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(28.dp),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

// ── Effects Screen ──
@Composable
private fun EffectsScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(8.dp)).background(S1).border(1.dp, BorderOn, RoundedCornerShape(8.dp)).padding(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("CHANNEL STRIP", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 2.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (vm.isRunning) Color(0xFF22C55E) else TMuted))
                    Spacer(Modifier.width(4.dp))
                    Text(if (vm.isRunning) "SIGNAL FLOW" else "BYPASS", fontFamily = FontFamily.Monospace, fontSize = 8.sp, color = if (vm.isRunning) Color(0xFF22C55E) else TMuted, letterSpacing = 1.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PresetRow(vm)
        Spacer(Modifier.height(12.dp))
        EffectCards(vm)
        Spacer(Modifier.height(40.dp))
    }
}

// ── Metronome Screen ──
@Composable
private fun MetronomeScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BPM", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TSecondary, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text("%.0f".format(vm.metronomeBpm), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 64.sp, color = Color(0xFFFF6B6B), letterSpacing = 4.sp)
                Spacer(Modifier.height(16.dp))
                Slider(value = (vm.metronomeBpm - 40f) / 200f, onValueChange = { vm.updateMetronomeBpm(40f + it * 200f) },
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF6B6B), activeTrackColor = Color(0xFFFF6B6B), inactiveTrackColor = Color(0xFF2A2A36)),
                    modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("40", fontSize = 10.sp, color = TMuted, fontFamily = FontFamily.Monospace)
                    Text("240", fontSize = 10.sp, color = TMuted, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.tapTempo() }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B), containerColor = Color(0xFFFF6B6B).copy(alpha = 0.05f))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.TouchApp, null, modifier = Modifier.size(20.dp)); Text("TAP", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            OutlinedButton(onClick = { vm.toggleMetronome() }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (vm.metronomeEnabled) Color(0xFFFF6B6B) else TMuted,
                    containerColor = if (vm.metronomeEnabled) Color(0xFFFF6B6B).copy(alpha = 0.1f) else Color.Transparent)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (vm.metronomeEnabled) Icons.Filled.MusicNote else Icons.Filled.MusicOff, null, modifier = Modifier.size(20.dp))
                    Text(if (vm.metronomeEnabled) "ON" else "OFF", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("VOLUME", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.sp)
                Slider(value = vm.metronomeVolume, onValueChange = { vm.updateMetronomeVolume(it) },
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF6B6B), activeTrackColor = Color(0xFFFF6B6B), inactiveTrackColor = Color(0xFF2A2A36)))
            }
        }
    }
}

// ── Looper Screen ──
@Composable
private fun LooperScreen(vm: MainViewModel) {
    val modeLabel = when (vm.looperMode) { 0 -> "STOPPED"; 1 -> "RECORDING"; 2 -> "PLAYING"; 3 -> "OVERDUB"; else -> "STOPPED" }
    val modeColor = when (vm.looperMode) { 1 -> Color(0xFFFF6B6B); 2 -> Color(0xFF22C55E); 3 -> Color(0xFFF59E0B); else -> TMuted }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(modeColor.copy(alpha = 0.1f)).border(2.dp, modeColor, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(when (vm.looperMode) { 1 -> Icons.Filled.FiberManualRecord; 2 -> Icons.Filled.PlayArrow; 3 -> Icons.Filled.AddCircleOutline; else -> Icons.Filled.Stop }, null, tint = modeColor, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(modeLabel, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = modeColor, letterSpacing = 3.sp)
                if (vm.looperLoopDuration > 0) Text("%.1fs".format(vm.looperLoopDuration), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TSecondary)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.looperToggleRecord() }, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = modeColor.copy(alpha = 0.2f))) {
                Icon(when (vm.looperMode) { 1 -> Icons.Filled.Stop; else -> Icons.Filled.FiberManualRecord }, null, tint = modeColor, modifier = Modifier.size(28.dp))
            }
            OutlinedButton(onClick = { vm.looperClear() }, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TMuted)) {
                Icon(Icons.Filled.Delete, null, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(S1).border(1.dp, BorderOn, RoundedCornerShape(12.dp)).padding(16.dp)) {
            Text("Record a loop by pressing REC, play it back, or overdub by pressing REC again while playing.", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TSecondary, lineHeight = 18.sp)
        }
    }
}

// ── MIDI Screen ──
@Composable
private fun MidiScreen(vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SettingsInputComponent, null, tint = Color(0xFFA78BFA), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("MIDI FOOT CONTROLLER", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TPrimary, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Connect a USB MIDI foot controller. CC#64 (sustain) toggles looper. Use Learn mode to map CCs to effect params.", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TSecondary, lineHeight = 16.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("MIDI LEARN MODE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TPrimary, letterSpacing = 1.sp)
                    Switch(checked = vm.midiLearnMode, onCheckedChange = { vm.toggleMidiLearnMode() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFA78BFA), checkedTrackColor = Color(0xFFA78BFA).copy(alpha = 0.3f)))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("EFFECT MAPPINGS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                listOf("Distortion", "Amp Sim", "EQ", "Chorus", "Noise Gate", "Compressor", "Delay", "Reverb").forEachIndexed { idx, name ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TPrimary)
                        Text("CC#$idx", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TSecondary)
                    }
                    if (idx < 7) Divider(color = Color(0xFF1E1E2A), thickness = 0.5.dp)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── BLE Screen ──
@Composable
private fun BleScreen(vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Bluetooth, null, tint = if (vm.bleConnected) Color(0xFF22D3EE) else TMuted, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text("BLE FOOT CONTROLLER", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TPrimary, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(if (vm.bleConnected) "Connected to ${vm.bleDeviceName}" else "Not connected", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (vm.bleConnected) Color(0xFF22C55E) else TSecondary)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.updateBleScanning(true) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("SCAN", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                    OutlinedButton(onClick = { vm.setBleConnected(false) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("DISCONNECT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (vm.bleScanning) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("NEARBY DEVICES", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Scanning...", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TMuted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = S1.copy(alpha = 0.5f)), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("COMPATIBLE DEVICES", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Text("• AirTurn BT-200/500", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TMuted)
                Text("• PageFlip Firefly", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TMuted)
                Text("• iRig BlueTurn", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TMuted)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ── Tools Screen (fallback for direct nav) ──
@Composable
private fun ToolsScreen(vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("USE THE MORE BUTTON IN THE BOTTOM NAV", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TSecondary)
    }
}
