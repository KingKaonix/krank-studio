package com.kaosnet.krank.ui.tone_matcher

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.kaosnet.krank.MainViewModel
import com.kaosnet.krank.WavLoader
import com.kaosnet.krank.ui.effectColor
import kotlinx.coroutines.launch
import com.kaosnet.krank.MicRecorder

data class ToneRecommendation(val effectName: String, val value: Float, val paramName: String)

private val Bg = Color(0xFF0A0A0E)
private val S1 = Color(0xFF1A1A22)
private val S2 = Color(0xFF22222E)
private val Border = Color(0xFF2A2A3A)
private val Cyan = Color(0xFF22D3EE)
private val TPrimary = Color(0xFFF1F1F5)
private val TSecondary = Color(0xFF8888A0)
private val TMuted = Color(0xFF555570)

@Composable
fun ToneMatcherScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).background(S1).border(1.dp, Border, RoundedCornerShape(8.dp)).padding(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("TONE MATCHER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TSecondary, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (vm.toneMatcherHasProfile) Color(0xFF22C55E) else TMuted))
            }
        }
        Spacer(Modifier.height(16.dp))
        SampleLoaderSection(vm)
        Spacer(Modifier.height(24.dp))
        if (vm.toneMatcherHasProfile) {
            ToneMatcherResults(vm)
            Spacer(Modifier.height(24.dp))
        }
        Instructions()
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SampleLoaderSection(vm: MainViewModel) {
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
                    val numFrames = minOf(result.samples.size, 4096)
                    val monoData = if (result.numChannels == 1) {
                        result.samples.take(numFrames).toFloatArray()
                    } else {
                        FloatArray(numFrames / result.numChannels) { i ->
                            var sum = 0f
                            for (c in 0 until result.numChannels) sum += result.samples[i * result.numChannels + c]
                            sum / result.numChannels
                        }
                    }
                    vm.loadAudioForToneMatcher(monoData, monoData.size, 1)
                    vm.updateToneMatcherRecommendations()
                    fileName = uri.lastPathSegment ?: "Loaded"
                } else errorMsg = "Invalid or unsupported audio"
            } catch (e: Exception) { errorMsg = "Error: ${e.message}" }
            loading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = S1),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("LOAD REFERENCE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 12.dp))

                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("audio/*")) },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan, containerColor = Cyan.copy(alpha = 0.05f))
                ) {
                    Text(if (loading) "ANALYZING..." else "LOAD AUDIO FILE", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
                }
                if (fileName.isNotEmpty()) {
                    Text(fileName, fontSize = 9.sp, color = TSecondary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                }

                if (errorMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg, fontSize = 10.sp, color = Color(0xFFFF6B6B), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun ToneMatcherResults(vm: MainViewModel) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("RECOMMENDED SETTINGS", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TSecondary, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 12.dp))

        val recommendations = mutableListOf<ToneRecommendation>().apply {
            add(ToneRecommendation("Distortion", vm.toneMatcherRecommendedDistortionDrive, "Drive"))
            add(ToneRecommendation("Distortion", vm.toneMatcherRecommendedDistortionTone, "Tone"))
            add(ToneRecommendation("Distortion", vm.toneMatcherRecommendedDistortionLevel, "Level"))
            add(ToneRecommendation("Amp Sim", vm.toneMatcherRecommendedAmpSimGain, "Gain"))
            add(ToneRecommendation("Amp Sim", vm.toneMatcherRecommendedAmpSimTone, "Tone"))
            add(ToneRecommendation("Amp Sim", vm.toneMatcherRecommendedAmpSimMaster, "Master"))
            add(ToneRecommendation("EQ", vm.toneMatcherRecommendedEqBass, "Bass"))
            add(ToneRecommendation("EQ", vm.toneMatcherRecommendedEqMid, "Mid"))
            add(ToneRecommendation("EQ", vm.toneMatcherRecommendedEqTreble, "Treble"))
            add(ToneRecommendation("Chorus", vm.toneMatcherRecommendedChorusRate, "Rate"))
            add(ToneRecommendation("Chorus", vm.toneMatcherRecommendedChorusDepth, "Depth"))
            add(ToneRecommendation("Chorus", vm.toneMatcherRecommendedChorusMix, "Mix"))
            add(ToneRecommendation("Delay", vm.toneMatcherRecommendedDelayMix, "Mix"))
            add(ToneRecommendation("Delay", vm.toneMatcherRecommendedDelayFeedback, "Feedback"))
            add(ToneRecommendation("Delay", vm.toneMatcherRecommendedDelayTime, "Time"))
            add(ToneRecommendation("Reverb", vm.toneMatcherRecommendedReverbSize, "Size"))
            add(ToneRecommendation("Reverb", vm.toneMatcherRecommendedReverbMix, "Mix"))
        }

        val columns = 3
        val rows = (recommendations.size + columns - 1) / columns
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < recommendations.size) {
                        val (effectName, value, paramName) = recommendations[index]
                        RecommendationCard(effectName = effectName, paramName = paramName, value = value, color = effectColor(effectName), modifier = Modifier.weight(1f))
                    } else { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RecommendationCard(effectName: String, paramName: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = S1), shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Text(effectName.take(1), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(6.dp))
            Text(paramName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TSecondary, letterSpacing = 0.5.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
            Text("${(value * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun Instructions() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = S1.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("HOW IT WORKS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TSecondary, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
                Text("1. Load an audio file (MP3/AAC/OGG/WAV) with your target guitar tone", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                Text("2. System analyzes pitch, harmonics, and texture", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                Text("3. Get optimal effect settings recommended", fontSize = 11.sp, color = TSecondary, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text("* Analyzes spectral content to auto-tune effects for matching your target tone", fontSize = 10.sp, color = TMuted, fontFamily = FontFamily.Monospace, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        }
    }
}
