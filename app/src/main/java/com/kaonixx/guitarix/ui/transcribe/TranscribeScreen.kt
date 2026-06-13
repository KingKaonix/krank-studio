package com.kaonixx.guitarix.ui.transcribe

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.kaonixx.guitarix.MainViewModel
import com.kaonixx.guitarix.TabNoteData
import com.kaonixx.guitarix.WavLoader

private val Bg         = Color(0xFF0A0A0E)
private val S1         = Color(0xFF1A1A22)
private val Cyan       = Color(0xFF22D3EE)
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
                    vm.transcribeAudio(monoData, result.sampleRate)
                } else errorMsg = "Could not read audio file"
            } catch (e: Exception) { errorMsg = "Error: ${e.message}" }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Text("TRANSCRIBE", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TPrimary, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text("Generate tablature from audio", fontSize = 14.sp, color = TSecondary)
        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♪", fontSize = 36.sp, color = Cyan)
                Spacer(Modifier.height(12.dp))
                Text("Load Audio File", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TPrimary)
                Spacer(Modifier.height(12.dp))
                if (fileName.isNotEmpty()) { Text("File: $fileName", fontSize = 12.sp, color = Cyan); Spacer(Modifier.height(8.dp)) }
                if (errorMsg.isNotEmpty()) { Text(errorMsg, fontSize = 12.sp, color = Color(0xFFFF6B6B)); Spacer(Modifier.height(8.dp)) }
                Button(onClick = { filePickerLauncher.launch(arrayOf("audio/wav", "audio/x-wav")) },
                    enabled = !loading, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg),
                    shape = RoundedCornerShape(12.dp)) {
                    Text(if (loading) "PROCESSING..." else "SELECT AUDIO FILE", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        if (vm.transcribeHasResult) {
            Text("${vm.transcribeNumMeasures} measures, ${vm.transcribeNotes.size} notes", fontSize = 14.sp, color = Cyan, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            TabView(vm.transcribeNotes)
        }

        Spacer(Modifier.height(30.dp))

        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = S1.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)) {
            Text("Load a WAV file with guitar audio. The app detects notes and maps them to fret positions. Best for solo guitar/bass parts. ML source separation coming soon.",
                fontSize = 12.sp, color = TSecondary, modifier = Modifier.padding(16.dp), lineHeight = 18.sp)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TabView(notes: List<TabNoteData>) {
    if (notes.isEmpty()) { Text("No notes detected", fontSize = 14.sp, color = TSecondary); return }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("TABLATURE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TPrimary, modifier = Modifier.padding(bottom = 12.dp))
        val displayNotes = notes.take(60)

        // Draw 6-string tab
        for (stringIdx in 0..5) {
            val matchingNotes = displayNotes.filter { it.stringNum == stringIdx }
            Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(STRING_NAMES[stringIdx], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Cyan, modifier = Modifier.width(16.dp))
                // Simple tab line
                Canvas(Modifier.fillMaxSize()) {
                    val h = size.height
                    drawLine(color = TSecondary.copy(alpha = 0.3f), start = Offset(0f, h/2), end = Offset(size.width, h/2), strokeWidth = 1f)
                    for (note in matchingNotes) {
                        val x = (note.startTime * 50f).coerceIn(2f, size.width - 10f)
                        drawCircle(color = Cyan, radius = 3f, center = Offset(x, h/2))
                    }
                }
            }
        }
    }
}
