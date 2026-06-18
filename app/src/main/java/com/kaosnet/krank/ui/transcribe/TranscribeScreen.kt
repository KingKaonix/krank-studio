package com.kaosnet.krank.ui.transcribe

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.kaosnet.krank.MainViewModel
import com.kaosnet.krank.TabNoteData
import com.kaosnet.krank.WavLoader
import com.kaosnet.krank.MicRecorder
import kotlinx.coroutines.launch

private val Bg         = com.kaosnet.krank.ui.KrankColors.Bg
private val S1         = com.kaosnet.krank.ui.KrankColors.SurfaceCard
private val S2         = com.kaosnet.krank.ui.KrankColors.Surface
private val S3         = Color(0xFF2A2A36)
private val Border     = com.kaosnet.krank.ui.KrankColors.BorderBold
private val Cyan       = com.kaosnet.krank.ui.KrankColors.Cyan
private val CyanDim    = com.kaosnet.krank.ui.KrankColors.CyanDim
private val Green      = com.kaosnet.krank.ui.KrankColors.Green
private val Red500     = Color(0xFFEF4444)
private val Yellow     = Color(0xFFF59E0B)
private val TPrimary   = com.kaosnet.krank.ui.KrankColors.Primary
private val TSecondary = com.kaosnet.krank.ui.KrankColors.Secondary
private val TMuted     = com.kaosnet.krank.ui.KrankColors.Muted
private val STRING_NAMES = listOf("e", "B", "G", "D", "A", "E")

@Composable
fun TranscribeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val recorderRef = remember { mutableListOf<MicRecorder>() }

    // File picker for loading audio
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            loading = true; errorMsg = ""; exportMessage = ""
            try {
                val result = WavLoader.load(context, uri)
                if (result != null) {
                    val monoData = if (result.numChannels == 1) result.samples
                    else FloatArray(result.samples.size / result.numChannels) { i ->
                        var s = 0f; for (c in 0 until result.numChannels) s += result.samples[i * result.numChannels + c]
                        s / result.numChannels
                    }
                    fileName = uri.lastPathSegment ?: "Loaded"
                    runCatching {
                        if (vm.polyphonicEnabled) vm.transcribePolyphonic(monoData, result.sampleRate)
                        else vm.transcribeAudio(monoData, result.sampleRate)
                    }
                } else errorMsg = "Could not read audio file"
            } catch (e: Exception) { errorMsg = "Error: ${e.message}" }
            loading = false
        }
    }

    // Export launchers
    val midiExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/midi")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val tempFile = java.io.File(context.cacheDir, "export_temp.mid")
                val exported = vm.engine.exportTabToMidi(tempFile.absolutePath)
                if (exported) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        tempFile.inputStream().use { `in` -> `in`.copyTo(out) }
                    }
                    exportMessage = "Exported MIDI successfully"
                } else exportMessage = "Export failed - no transcription data"
                tempFile.delete()
            } catch (e: Exception) { exportMessage = "Export error: ${e.message}" }
        }
    }

    val abcExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val tempFile = java.io.File(context.cacheDir, "export_temp.abc")
                val exported = vm.engine.exportTabToAbc(tempFile.absolutePath)
                if (exported) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        tempFile.inputStream().use { `in` -> `in`.copyTo(out) }
                    }
                    exportMessage = "Exported ABC notation successfully"
                } else exportMessage = "Export failed - no transcription data"
                tempFile.delete()
            } catch (e: Exception) { exportMessage = "Export error: ${e.message}" }
        }
    }

    val musicXmlExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/xml")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val tempFile = java.io.File(context.cacheDir, "export_temp.xml")
                val exported = vm.engine.exportTabToMusicXml(tempFile.absolutePath)
                if (exported) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        tempFile.inputStream().use { `in` -> `in`.copyTo(out) }
                    }
                    exportMessage = "Exported MusicXML successfully"
                } else exportMessage = "Export failed - no transcription data"
                tempFile.delete()
            } catch (e: Exception) { exportMessage = "Export error: ${e.message}" }
        }
    }

    val gp5ExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val tempFile = java.io.File(context.cacheDir, "export_temp.gp5")
                vm.exportTabGp5(tempFile.absolutePath)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { `in` -> `in`.copyTo(out) }
                }
                tempFile.delete()
            } catch (e: Exception) { exportMessage = "Export error: ${e.message}" }
        }
    }


    Column(
        Modifier.fillMaxSize().background(Bg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scrollable content
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Title bar
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).background(S1).border(1.dp, Border, RoundedCornerShape(8.dp)).padding(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("TAB TRANSCRIPTION", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TSecondary, letterSpacing = 2.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (vm.transcribeHasResult || vm.polyphonicHasResult) Green else TMuted))
                        if (vm.polyphonicEnabled) {
                            Spacer(Modifier.width(4.dp))
                            Text("ML", fontSize = 8.sp, color = Color(0xFFA78BFA), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Input section
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("AUDIO SOURCE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 12.dp))

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                    ) {
                        Icon(Icons.Filled.LibraryMusic, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("LOAD MP3 / AAC / OGG / WAV", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            if (!isRecording) {
                                scope.launch {
                                    isRecording = true; errorMsg = ""; exportMessage = ""
                                    val recorder = MicRecorder()
                                    recorderRef.add(recorder)
                                    val result = recorder.startRecording()
                                    isRecording = false
                                    if (result != null) {
                                        fileName = "Mic Recording"
                                        try {
                                            if (vm.polyphonicEnabled) vm.transcribePolyphonic(result.samples, result.sampleRate)
                                            else vm.transcribeAudio(result.samples, result.sampleRate)
                                        } catch (e: Exception) { errorMsg = "Transcription error: ${e.message}" }
                                    } else { errorMsg = "Recording failed" }
                                }
                            } else {
                                recorderRef.firstOrNull()?.stop()
                                isRecording = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isRecording) Red500 else Green),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isRecording) Red500.copy(alpha = 0.5f) else Border)
                    ) {
                        Icon(if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isRecording) "STOP RECORDING" else "RECORD FROM MICROPHONE",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                    }

                    if (loading) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = Cyan, trackColor = S2)
                    }
                    if (errorMsg.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(errorMsg, fontSize = 11.sp, color = Red500, fontFamily = FontFamily.Monospace)
                    }
                    if (fileName.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(fileName, fontSize = 10.sp, color = CyanDim, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (exportMessage.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(exportMessage, fontSize = 10.sp, color = Cyan, fontFamily = FontFamily.Monospace)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("IMPORT TAB", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 8.dp))
                var tabImportMessage by androidx.compose.runtime.remember { mutableStateOf("") }
                val tabPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        vm.importTabFile(context, uri)
                        tabImportMessage = "Imported: ${uri.lastPathSegment}"
                    }
                }
                OutlinedButton(
                    onClick = { tabPickerLauncher.launch(arrayOf("audio/midi", "audio/x-midi", "application/xml", "text/xml", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA78BFA)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("OPEN TAB (MIDI / XML / GP5)", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontFamily = FontFamily.Monospace)
                }
                if (tabImportMessage.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(tabImportMessage, fontSize = 10.sp, color = Color(0xFFA78BFA), fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(Modifier.height(20.dp))

            // Tablature card with playback controls
            if (vm.transcribeHasResult || vm.polyphonicHasResult) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("TABLATURE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TSecondary, letterSpacing = 1.5.sp)
                            Text("${vm.transcribeNumMeasures} measures \u00B7 ${vm.transcribeNotes.size} notes",
                                fontSize = 10.sp, color = Cyan, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(12.dp))
                        TabView(vm.transcribeNotes)

                        Spacer(Modifier.height(16.dp))

                        // ── PLAYBACK CONTROLS ──
                        val state = vm.playbackState
                        Text("PLAYBACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 8.dp))

                        // Progress bar
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(state.currentTimeMs), fontSize = 9.sp, color = TMuted, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = state.progress,
                                onValueChange = { vm.seekTranscription((it * state.totalDurationMs).toLong()) },
                                colors = SliderDefaults.colors(
                                    thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = S3
                                ),
                                modifier = Modifier.weight(1f).height(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(formatTime(state.totalDurationMs), fontSize = 9.sp, color = TMuted, fontFamily = FontFamily.Monospace)
                        }

                        // Transport buttons
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            // Stop
                            OutlinedButton(
                                onClick = { vm.stopTranscription() },
                                modifier = Modifier.size(44.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Red500.copy(alpha = 0.3f)),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(16.dp))

                            // Play/Pause
                            FilledIconButton(
                                onClick = {
                                    if (state.isPlaying) vm.pauseTranscription()
                                    else vm.playTranscription()
                                },
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Cyan, contentColor = Color(0xFF0A0A0E))
                            ) {
                                Icon(
                                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    null, modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))

                            // Export menu
                            Box {
                                var showExport by remember { mutableStateOf(false) }
                                OutlinedButton(
                                    onClick = { showExport = true },
                                    modifier = Modifier.size(44.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Green),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Green.copy(alpha = 0.3f)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(16.dp))
                                }
                                DropdownMenu(
                                    expanded = showExport,
                                    onDismissRequest = { showExport = false },
                                    modifier = Modifier.background(S1).border(1.dp, Border, RoundedCornerShape(8.dp))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Export as MIDI", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TPrimary) },
                                        onClick = { showExport = false; midiExportLauncher.launch("krank_transcription.mid") },
                                        leadingIcon = { Icon(Icons.Filled.MusicNote, null, tint = Cyan, modifier = Modifier.size(16.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export as GP5 (Guitar Pro)", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TPrimary) },
                                        onClick = { showExport = false; gp5ExportLauncher.launch("krank_transcription.gp5") },
                                        leadingIcon = { Icon(Icons.Filled.LibraryMusic, null, tint = Yellow, modifier = Modifier.size(16.dp)) }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Export as ABC", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TPrimary) },
                                        onClick = { showExport = false; abcExportLauncher.launch("krank_transcription.abc") },
                                        leadingIcon = { Icon(Icons.Filled.Code, null, tint = Green, modifier = Modifier.size(16.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export as MusicXML", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TPrimary) },
                                        onClick = { showExport = false; musicXmlExportLauncher.launch("krank_transcription.xml") },
                                        leadingIcon = { Icon(Icons.Filled.Description, null, tint = Cyan, modifier = Modifier.size(16.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Info card
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = S1.copy(alpha = 0.5f)), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("ABOUT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Text("1. Load an MP3/AAC/OGG/WAV file or record from your microphone", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                    Text("2. System detects notes and maps them to fret positions", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                    Text("3. Or import an existing MIDI (.mid) or MusicXML (.xml) tab file", fontSize = 11.sp, color = Color(0xFFA78BFA), fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                    if (vm.polyphonicEnabled) {
                        Text("3. Polyphonic mode - attempts multi-note/chord detection", fontSize = 11.sp, color = Color(0xFFA78BFA), fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                    } else {
                        Text("3. Best results with monophonic guitar or bass parts", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("4. Export as MIDI or ABC, or play back the generated tab", fontSize = 11.sp, color = Cyan.copy(alpha = 0.8f), fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TabView(notes: List<TabNoteData>) {
    if (notes.isEmpty()) {
        Text("Processing... check transcription below", fontSize = 12.sp, color = TMuted, fontFamily = FontFamily.Monospace)
        return
    }
    val displayNotes = notes.take(100)
    val maxTime = displayNotes.maxOfOrNull { it.startTime + it.duration } ?: 1f

    for (stringIdx in 0..5) {
        val matchingNotes = displayNotes.filter { it.stringNum == stringIdx }
        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(20.dp).height(20.dp).clip(RoundedCornerShape(3.dp)).background(S2), contentAlignment = Alignment.Center) {
                Text(STRING_NAMES.getOrElse(stringIdx) { "?" }, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyanDim, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(4.dp))
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height; val w = size.width; val lineY = h / 2f
                drawLine(color = TSecondary.copy(alpha = 0.2f), start = Offset(0f, lineY), end = Offset(w, lineY), strokeWidth = 1f)
                for (note in matchingNotes) {
                    val x = ((note.startTime / maxTime) * w).coerceIn(2f, w - 14f)
                    drawCircle(color = Cyan.copy(alpha = 0.8f), radius = 7.dp.toPx(), center = Offset(x, lineY))
                    drawCircle(color = Cyan.copy(alpha = 0.3f), radius = 9.dp.toPx(), center = Offset(x, lineY))
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
