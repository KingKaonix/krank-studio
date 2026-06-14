package com.kaosnet.krank.ui.transcribe

import android.app.Activity
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
import com.kaosnet.krank.SystemAudioCapture
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
    var isCapturing by remember { mutableStateOf(false) }
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

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            scope.launch {
                isCapturing = true; errorMsg = ""
                val capture = SystemAudioCapture(context as Activity)
                capture.setup(result.resultCode, result.data!!)
                val capResult = capture.capture(30000)
                if (capResult != null) {
                    fileName = "Song (${capResult.durationMs / 1000}s)"
                    runCatching {
                        if (vm.polyphonicEnabled) vm.transcribePolyphonic(capResult.samples, capResult.sampleRate)
                        else vm.transcribeAudio(capResult.samples, capResult.sampleRate)
                    }
                } else { errorMsg = "Capture failed - try again" }
                capture.release(); isCapturing = false
            }
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

                OutlinedButton(onClick = {
                    filePickerLauncher.launch(arrayOf("audio/*"))
                }, enabled = !loading && !isRecording && !isCapturing,
                    modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan, containerColor = Cyan.copy(alpha = 0.05f))) {
                    Text(if (loading) "LOADING..." else "LOAD AUDIO FILE", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
                }
                if (fileName.isNotEmpty()) {
                    Text(fileName, fontSize = 9.sp, color = TSecondary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))

                // Mic recording - indefinite, user-stopped
                OutlinedButton(onClick = {
                    if (isRecording) {
                        recorderRef.firstOrNull()?.stop()
                    } else {
                        isRecording = true; errorMsg = ""
                        val recorder = MicRecorder()
                        recorderRef.clear()
                        recorderRef.add(recorder)
                        scope.launch {
                            val result = recorder.startRecording()
                            if (result != null) {
                                fileName = "Mic Recording"
                                runCatching {
                                    if (vm.polyphonicEnabled) vm.transcribePolyphonic(result.samples, result.sampleRate)
                                    else vm.transcribeAudio(result.samples, result.sampleRate)
                                }
                            } else {
                                if (errorMsg.isEmpty()) errorMsg = "Recording failed"
                            }
                            isRecording = false
                            recorderRef.clear()
                        }
                    }
                }, enabled = !loading && !isCapturing,
                    modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isRecording) Color(0xFFFF6B6B) else Color(0xFFFF6B6B),
                        containerColor = if (isRecording) Color(0xFFFF6B6B).copy(alpha = 0.15f) else Color(0xFFFF6B6B).copy(alpha = 0.05f))
                ) {
                    Text(if (isRecording) "■ STOP RECORDING" else "RECORD FROM MIC",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))

                // Capture from device
                OutlinedButton(onClick = {
                    val intent = SystemAudioCapture(context as Activity).createCaptureIntent()
                    if (intent != null) {
                        captureLauncher.launch(intent)
                    } else {
                        errorMsg = "Screen capture not available on this device"
                    }
                }, enabled = !loading && !isRecording && !isCapturing,
                    modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE), containerColor = Color(0xFF22D3EE).copy(alpha = 0.05f))) {
                    Text(if (isCapturing) "CAPTURING..." else "CAPTURE FROM DEVICE (30s)",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        if (vm.transcribeHasResult || vm.polyphonicHasResult) {
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("TABLATURE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TSecondary, letterSpacing = 1.5.sp)
                        Text("${vm.transcribeNumMeasures} measures · ${vm.transcribeNotes.size} notes",
                            fontSize = 10.sp, color = Cyan, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(12.dp))
                    TabView(vm.transcribeNotes)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = S1.copy(alpha = 0.5f)), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("ABOUT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 8.dp))
                Text("• Load an audio file (MP3/AAC/OGG/WAV), record from mic, or capture a song playing on your device (30s)", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                Text("• System detects notes and maps them to fret positions", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                if (vm.polyphonicEnabled) {
                    Text("• Polyphonic mode enabled - attempts multi-note/chord detection", fontSize = 11.sp, color = Color(0xFFA78BFA), fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                } else {
                    Text("• Best results with monophonic guitar or bass parts", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("* Enable Polyphonic Mode in Tools for ML-based multi-instrument transcription", fontSize = 10.sp, color = TMuted, fontFamily = FontFamily.Monospace, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TabView(notes: List<TabNoteData>) {
    if (notes.isEmpty()) { Text("No notes detected", fontSize = 12.sp, color = TMuted, fontFamily = FontFamily.Monospace); return }

    val displayNotes = notes.take(60)
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
