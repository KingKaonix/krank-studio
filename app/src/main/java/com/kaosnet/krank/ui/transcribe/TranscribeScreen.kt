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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.kaosnet.krank.MainViewModel
import com.kaosnet.krank.TabNoteData
import com.kaosnet.krank.WavLoader
import com.kaosnet.krank.MicRecorder
import kotlinx.coroutines.launch

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
private val STRING_NAMES = listOf("e", "B", "G", "D", "A", "E")

@Composable
fun TranscribeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var fileName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val recorderRef = remember { mutableListOf<MicRecorder>() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            loading = true; errorMsg = ""
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

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
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

                // Load from file - MP3/AAC/OGG/WAV
                OutlinedButton(onClick = {
                    filePickerLauncher.launch(arrayOf("audio/*", "audio/mpeg", "audio/aac", "audio/ogg", "audio/wav", "audio/x-wav"))
                }, enabled = !loading && !isRecording,
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan, containerColor = Color(0xFF22D3EE).copy(alpha = 0.05f))) {
                    Text("LOAD MP3 / AAC / OGG / WAV",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(10.dp))

                // Record from mic
                OutlinedButton(onClick = {
                    if (isRecording) {
                        recorderRef.firstOrNull()?.stop(); isRecording = false
                    } else {
                        scope.launch {
                            isRecording = true; errorMsg = ""
                            val recorder = MicRecorder()
                            recorderRef.clear(); recorderRef.add(recorder)
                            val result = recorder.startRecording()
                            if (result != null) {
                                fileName = "Mic recording (${result.sampleRate}Hz)"
                                runCatching {
                                    if (vm.polyphonicEnabled) vm.transcribePolyphonic(result.samples, result.sampleRate)
                                    else vm.transcribeAudio(result.samples, result.sampleRate)
                                }
                            } else { errorMsg = "Recording failed" }
                            isRecording = false
                        }
                    }
                }, enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isRecording) Color(0xFFFF6B6B) else Green,
                        containerColor = (if (isRecording) Color(0xFFFF6B6B) else Green).copy(alpha = 0.05f))) {
                    Text(if (isRecording) "STOP RECORDING" else "RECORD FROM MIC",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
                }

                if (loading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = Cyan, trackColor = S2)
                }
                if (errorMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg, fontSize = 11.sp, color = Color(0xFFFF6B6B), fontFamily = FontFamily.Monospace)
                }
                if (fileName.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(fileName, fontSize = 10.sp, color = CyanDim, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        if (vm.transcribeHasResult || vm.polyphonicHasResult) {
            // Tablature display
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("TABLATURE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TSecondary, letterSpacing = 1.5.sp)
                        Text("${vm.transcribeNumMeasures} measures \u00B7 ${vm.transcribeNotes.size} notes",
                            fontSize = 10.sp, color = Cyan, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(12.dp))
                    TabView(vm.transcribeNotes)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Info card
        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = S1.copy(alpha = 0.5f)), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("ABOUT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 8.dp))
                Text("1. Load an MP3/AAC/OGG/WAV file or record from your microphone", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                Text("2. System detects notes and maps them to fret positions", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                if (vm.polyphonicEnabled) {
                    Text("3. Polyphonic mode - attempts multi-note/chord detection", fontSize = 11.sp, color = Color(0xFFA78BFA), fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                } else {
                    Text("3. Best results with monophonic guitar or bass parts", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
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
