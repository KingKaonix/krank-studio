package com.kaonixx.guitarix

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.kaonixx.guitarix.GuitarEngine.Companion.FX_AMP_SIM
import com.kaonixx.guitarix.GuitarEngine.Companion.FX_CHORUS
import com.kaonixx.guitarix.GuitarEngine.Companion.FX_DELAY
import com.kaonixx.guitarix.GuitarEngine.Companion.FX_DISTORTION
import com.kaonixx.guitarix.GuitarEngine.Companion.FX_EQ
import com.kaonixx.guitarix.GuitarEngine.Companion.FX_REVERB

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val engine = GuitarEngine()

    // --- Observed state ---
    var isRunning by mutableStateOf(false); private set
    var currentPresetIndex by mutableIntStateOf(0); private set

    // Per-effect enable state
    var distortionOn by mutableStateOf(false); private set
    var ampSimOn by mutableStateOf(true); private set
    var eqOn by mutableStateOf(false); private set
    var chorusOn by mutableStateOf(false); private set
    var delayOn by mutableStateOf(true); private set
    var reverbOn by mutableStateOf(true); private set

    // Distortion params
    var distortionDrive by mutableFloatStateOf(0.5f); private set
    var distortionTone by mutableFloatStateOf(0.5f); private set
    var distortionLevel by mutableFloatStateOf(0.7f); private set

    var ampSimGain by mutableFloatStateOf(0.5f); private set
    var ampSimTone by mutableFloatStateOf(0.5f); private set
    var ampSimMaster by mutableFloatStateOf(0.7f); private set

    var eqBass by mutableFloatStateOf(0.5f); private set
    var eqMid by mutableFloatStateOf(0.5f); private set
    var eqTreble by mutableFloatStateOf(0.5f); private set

    var chorusRate by mutableFloatStateOf(0.5f); private set
    var chorusDepth by mutableFloatStateOf(0.3f); private set
    var chorusMix by mutableFloatStateOf(0.3f); private set

    var delayMix by mutableFloatStateOf(0.3f); private set
    var delayFeedback by mutableFloatStateOf(0.3f); private set
    var delayMs by mutableFloatStateOf(400f); private set

    var reverbRoomSize by mutableFloatStateOf(0.3f); private set
    var reverbMix by mutableFloatStateOf(0.2f); private set

    val presetNames = GuitarEngine.presetNames
    val effectNames = GuitarEngine.effectNames

    fun toggleEngine() {
        if (isRunning) { engine.stop(); isRunning = false }
        else { isRunning = engine.start() }
    }

    fun loadPreset(index: Int) {
        currentPresetIndex = index
        engine.loadPreset(index)
        syncParamsFromPreset(index)
    }

    private fun syncParamsFromPreset(index: Int) {
        distortionOn  = engine.isEffectEnabled(FX_DISTORTION)
        ampSimOn      = engine.isEffectEnabled(FX_AMP_SIM)
        eqOn          = engine.isEffectEnabled(FX_EQ)
        chorusOn      = engine.isEffectEnabled(FX_CHORUS)
        delayOn       = engine.isEffectEnabled(FX_DELAY)
        reverbOn      = engine.isEffectEnabled(FX_REVERB)

        distortionDrive = engine.getEffectParameter(FX_DISTORTION, GuitarEngine.PARAM_DRIVE)
        distortionTone  = engine.getEffectParameter(FX_DISTORTION, GuitarEngine.PARAM_TONE)
        distortionLevel = engine.getEffectParameter(FX_DISTORTION, GuitarEngine.PARAM_LEVEL)

        ampSimGain   = engine.getEffectParameter(FX_AMP_SIM, GuitarEngine.PARAM_GAIN)
        ampSimTone   = engine.getEffectParameter(FX_AMP_SIM, GuitarEngine.PARAM_TONE2)
        ampSimMaster = engine.getEffectParameter(FX_AMP_SIM, GuitarEngine.PARAM_MASTER)

        eqBass   = engine.getEffectParameter(FX_EQ, GuitarEngine.PARAM_BASS)
        eqMid    = engine.getEffectParameter(FX_EQ, GuitarEngine.PARAM_MID)
        eqTreble = engine.getEffectParameter(FX_EQ, GuitarEngine.PARAM_TREBLE)

        chorusRate  = engine.getEffectParameter(FX_CHORUS, GuitarEngine.PARAM_RATE)
        chorusDepth = engine.getEffectParameter(FX_CHORUS, GuitarEngine.PARAM_DEPTH)
        chorusMix   = engine.getEffectParameter(FX_CHORUS, GuitarEngine.PARAM_MIX)

        delayMix     = engine.getEffectParameter(FX_DELAY, GuitarEngine.PARAM_MIX2)
        delayFeedback = engine.getEffectParameter(FX_DELAY, GuitarEngine.PARAM_FEEDBACK)
        delayMs      = engine.getEffectParameter(FX_DELAY, GuitarEngine.PARAM_DELAY_MS)

        reverbRoomSize = engine.getEffectParameter(FX_REVERB, GuitarEngine.PARAM_ROOM_SIZE)
        reverbMix      = engine.getEffectParameter(FX_REVERB, GuitarEngine.PARAM_MIX3)
    }

    fun toggleDistortion() { distortionOn = !distortionOn; engine.setEffectEnabled(FX_DISTORTION, distortionOn) }
    fun toggleAmpSim() { ampSimOn = !ampSimOn; engine.setEffectEnabled(FX_AMP_SIM, ampSimOn) }
    fun toggleEQ() { eqOn = !eqOn; engine.setEffectEnabled(FX_EQ, eqOn) }
    fun toggleChorus() { chorusOn = !chorusOn; engine.setEffectEnabled(FX_CHORUS, chorusOn) }
    fun toggleDelay() { delayOn = !delayOn; engine.setEffectEnabled(FX_DELAY, delayOn) }
    fun toggleReverb() { reverbOn = !reverbOn; engine.setEffectEnabled(FX_REVERB, reverbOn) }

    fun updateDistortionDrive(v: Float) { distortionDrive = v; engine.setEffectParameter(FX_DISTORTION, GuitarEngine.PARAM_DRIVE, v) }
    fun updateDistortionTone(v: Float) { distortionTone = v; engine.setEffectParameter(FX_DISTORTION, GuitarEngine.PARAM_TONE, v) }
    fun updateDistortionLevel(v: Float) { distortionLevel = v; engine.setEffectParameter(FX_DISTORTION, GuitarEngine.PARAM_LEVEL, v) }

    fun updateAmpSimGain(v: Float) { ampSimGain = v; engine.setEffectParameter(FX_AMP_SIM, GuitarEngine.PARAM_GAIN, v) }
    fun updateAmpSimTone(v: Float) { ampSimTone = v; engine.setEffectParameter(FX_AMP_SIM, GuitarEngine.PARAM_TONE2, v) }
    fun updateAmpSimMaster(v: Float) { ampSimMaster = v; engine.setEffectParameter(FX_AMP_SIM, GuitarEngine.PARAM_MASTER, v) }

    fun updateEqBass(v: Float) { eqBass = v; engine.setEffectParameter(FX_EQ, GuitarEngine.PARAM_BASS, v) }
    fun updateEqMid(v: Float) { eqMid = v; engine.setEffectParameter(FX_EQ, GuitarEngine.PARAM_MID, v) }
    fun updateEqTreble(v: Float) { eqTreble = v; engine.setEffectParameter(FX_EQ, GuitarEngine.PARAM_TREBLE, v) }

    fun updateChorusRate(v: Float) { chorusRate = v; engine.setEffectParameter(FX_CHORUS, GuitarEngine.PARAM_RATE, v) }
    fun updateChorusDepth(v: Float) { chorusDepth = v; engine.setEffectParameter(FX_CHORUS, GuitarEngine.PARAM_DEPTH, v) }
    fun updateChorusMix(v: Float) { chorusMix = v; engine.setEffectParameter(FX_CHORUS, GuitarEngine.PARAM_MIX, v) }

    fun updateDelayMix(v: Float) { delayMix = v; engine.setEffectParameter(FX_DELAY, GuitarEngine.PARAM_MIX2, v) }
    fun updateDelayFeedback(v: Float) { delayFeedback = v; engine.setEffectParameter(FX_DELAY, GuitarEngine.PARAM_FEEDBACK, v) }
    fun updateDelayMs(v: Float) { delayMs = v; engine.setEffectParameter(FX_DELAY, GuitarEngine.PARAM_DELAY_MS, v) }

    fun updateReverbRoomSize(v: Float) { reverbRoomSize = v; engine.setEffectParameter(FX_REVERB, GuitarEngine.PARAM_ROOM_SIZE, v) }
    fun updateReverbMix(v: Float) { reverbMix = v; engine.setEffectParameter(FX_REVERB, GuitarEngine.PARAM_MIX3, v) }

    override fun onCleared() {
        if (isRunning) engine.stop()
        super.onCleared()
    }
}
