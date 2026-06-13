package com.kaonixx.guitarix.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaonixx.guitarix.MainViewModel

// ── Tuner Screen ──
@Composable
fun TunerScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0E))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        
        // Title
        Text(
            "CHROMATIC TUNER",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF1F1F5),
            letterSpacing = 2.sp
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Tuning preset selector
        TuningPresetSelector(vm)
        
        Spacer(Modifier.height(30.dp))
        
        // Tuner display
        TunerDisplay(vm)
        
        Spacer(Modifier.height(30.dp))
        
        // Tuning info
        TuningInfo(vm)
        
        Spacer(Modifier.height(40.dp))
        
        // Instructions
        Instructions()
    }
}

// ── Tuning Preset Selector ──
@Composable
private fun TuningPresetSelector(vm: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Tuning Preset",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF8888A0),
            modifier = Modifier.align(Alignment.Start)
        )
        
        Spacer(Modifier.height(8.dp))
        
        // Simple dropdown for tuning presets
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(
                onClick = { expanded.value = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A1A22),
                    contentColor = Color(0xFFF1F1F5)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        vm.engine.getTunerCurrentTuningName(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = androidx.compose.material.icons.filled.ArrowDropDown,
                        contentDescription = "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            DropdownMenu(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Standard", "Drop D", "Drop C", "Open D", "Open G", "DADGAD",
                       "Half-Step Down", "Full-Step Down", "Drop B", "Open A", "Custom")
                    .forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name, fontSize = 14.sp) },
                            onClick = {
                                vm.setTunerTuning(index)
                                expanded.value = false
                            }
                        )
                    }
            }
        }
    }
}

// ── Tuner Display ──
@Composable
private fun TunerDisplay(vm: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main frequency display
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A22))
                .border(2.dp, Color(0xFF2A2A3A), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (vm.isTunerNoteDetected) {
                    Text(
                        "${vm.getTunerNoteIndex()}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF22D3EE)
                    )
                    Text(
                        vm.getTunerNoteName(vm.getTunerNoteIndex()),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF1F1F5)
                    )
                    Text(
                        "${vm.getTunerOctave()} octave",
                        fontSize = 16.sp,
                        color = Color(0xFF8888A0)
                    )
                } else {
                    Text(
                        "--",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8888A0)
                    )
                    Text(
                        "NO SIGNAL",
                        fontSize = 16.sp,
                        color = Color(0xFF8888A0)
                    )
                }
                
                Spacer(Modifier.height(10.dp))
                
                Text(
                    if (vm.isTunerNoteDetected) {
                        "${vm.tunerFrequency:.1f} Hz"
                    } else {
                        "--- Hz"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF1F1F5)
                )
            }
        }
        
        Spacer(Modifier.height(20.dp))
        
        // Tuning accuracy
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TuningAccuracyCard("CENT", "${vm.tunerCents:+.0f}¢", Color(0xFF22D3EE))
            TuningAccuracyCard("STRING", "${vm.engine.getClosestString() + 1}", Color(0xFFF1F1F5))
        }
    }
}

// ── Tuning Accuracy Card ──
@Composable
private fun TuningAccuracyCard(title: String, value: String, color: Color) {
    Card(
        modifier = Modifier.width(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A22)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                fontSize = 12.sp,
                color = Color(0xFF8888A0),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// ── Tuning Information ──
@Composable
private fun TuningInfo(vm: MainViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
    ) {
        Text(
            "Current String Tunings:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFF1F1F5),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Display each string tuning
        for (i in 0 until 6) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "String ${i + 1}:",
                    fontSize = 13.sp,
                    color = Color(0xFF8888A0)
                )
                Text(
                    "-- Hz",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF1F1F5)
                )
            }
        }
    }
}

// ── Instructions ──
@Composable
private fun Instructions() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        
        Text(
            "How to Use:",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF1F1F5),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        InstructionStep("1", "Play a note on your guitar", "The tuner will detect the pitch")
        InstructionStep("2", "Select your tuning preset", "Choose standard or custom tuning")
        InstructionStep("3", "Adjust for accuracy", "Fine-tune if needed")
        
        Spacer(Modifier.height(20.dp))
        
        Text(
            "* Requires good microphone input for accurate tuning",
            fontSize = 11.sp,
            color = Color(0xFF8888A0),
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
private fun InstructionStep(step: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22D3EE)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                step,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0A0A0E)
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF1F1F5)
            )
            Text(
                description,
                fontSize = 12.sp,
                color = Color(0xFF8888A0)
            )
        }
    }
}
