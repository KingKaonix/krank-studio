package com.kaosnet.krank

class KrankEngine {
    companion object {
        init { System.loadLibrary("krank_engine") }

        const val FX_DISTORTION = 0
        const val FX_AMP_SIM    = 1
        const val FX_EQ         = 2
        const val FX_CHORUS     = 3
        const val FX_NOISE_GATE = 4
        const val FX_COMPRESSOR = 5
        const val FX_DELAY      = 6
        const val FX_REVERB     = 7
        const val FX_TUNER      = 8
        const val FX_TONE_MATCHER = 9

        // Distortion params
        const val PARAM_DRIVE = 0
        const val PARAM_TONE  = 1
        const val PARAM_LEVEL = 2

        // AmpSim params
        const val PARAM_GAIN   = 0
        const val PARAM_TONE2  = 1
        const val PARAM_MASTER = 2

        // EQ params
        const val PARAM_BASS   = 0
        const val PARAM_MID    = 1
        const val PARAM_TREBLE = 2

        // Noise Gate params
        const val PARAM_THRESHOLD2 = 0
        const val PARAM_ATTACK2 = 1
        const val PARAM_RELEASE2 = 2

        // Compressor params
        const val PARAM_THRESHOLD3 = 0
        const val PARAM_RATIO = 1
        const val PARAM_ATTACK3 = 2
        const val PARAM_RELEASE3 = 3

        // Chorus params
        const val PARAM_RATE  = 0
        const val PARAM_DEPTH = 1
        const val PARAM_MIX   = 2

        // Delay params
        const val PARAM_MIX2      = 0
        const val PARAM_FEEDBACK  = 1
        const val PARAM_DELAY_MS  = 2

        // Reverb params
        const val PARAM_ROOM_SIZE = 0
        const val PARAM_MIX3      = 1

        val effectNames = listOf("Distortion", "Amp Sim", "EQ", "Chorus", "Noise Gate", "Compressor", "Delay", "Reverb", "Tuner", "Tone Matcher")
        val presetNames = listOf("Clean", "Crunch", "Lead", "Metal", "Ambient")
    }

    private var nativePtr: Long = 0
    init { nativePtr = nativeCreate() }
    protected fun finalize() { if (nativePtr != 0L) nativeDestroy(nativePtr) }

    fun start(): Boolean = nativeStart(nativePtr)
    fun stop() = nativeStop(nativePtr)

    fun setEffectEnabled(index: Int, enabled: Boolean) = nativeSetEffectEnabled(nativePtr, index, enabled)
    fun isEffectEnabled(index: Int): Boolean = nativeIsEffectEnabled(nativePtr, index)

    fun setEffectParameter(index: Int, paramId: Int, value: Float) = nativeSetEffectParameter(nativePtr, index, paramId, value)
    fun getEffectParameter(index: Int, paramId: Int): Float = nativeGetEffectParameter(nativePtr, index, paramId)

    fun loadPreset(preset: Int) = nativeLoadPreset(nativePtr, preset)

    // Tuner
    fun loadAudioForTuner(data: FloatArray, numFrames: Int, numChannels: Int) = nativeLoadAudioForTuner(nativePtr, data, numFrames, numChannels)
    fun getTunerFrequency(): Float = nativeGetTunerFrequency(nativePtr)
    fun getTunerNoteIndex(): Int = nativeGetTunerNoteIndex(nativePtr)
    fun getTunerOctave(): Int = nativeGetTunerOctave(nativePtr)
    fun getTunerCents(): Float = nativeGetTunerCents(nativePtr)
    fun isTunerNoteDetected(): Boolean = nativeIsTunerNoteDetected(nativePtr)
    fun getTunerCurrentTuningName(): String = nativeGetTunerCurrentTuningName(nativePtr)
    fun getTunerNoteName(index: Int): String = nativeGetTunerNoteName(nativePtr, index)
    fun setTunerMuteDry(mute: Boolean) = nativeSetTunerMuteDry(nativePtr, mute)
    fun isTunerMuteDry(): Boolean = nativeIsTunerMuteDry(nativePtr)
    fun getInputPeakLevel(): Float = nativeGetInputPeakLevel(nativePtr)

    // Monitoring
    fun setMonitoringEnabled(enabled: Boolean) = nativeSetMonitoringEnabled(nativePtr, enabled)
    fun isMonitoringEnabled(): Boolean = nativeIsMonitoringEnabled(nativePtr)

    // Recording
    fun startRecording(filePath: String) = nativeStartRecording(nativePtr, filePath)
    fun stopRecording() = nativeStopRecording(nativePtr)
    fun isRecording(): Boolean = nativeIsRecording(nativePtr)
    fun loadImpulseResponse(path: String): Boolean = nativeLoadImpulseResponse(nativePtr, path)

    // Transcription
    fun transcribeAudio(data: FloatArray, numSamples: Int, sampleRate: Int): Boolean = nativeTranscribeAudio(nativePtr, data, numSamples, sampleRate)
    fun hasTranscription(): Boolean = nativeHasTranscription(nativePtr)
    fun getNumMeasures(): Int = nativeGetNumMeasures(nativePtr)
    fun getTranscriptionProgress(): Float = nativeGetTranscriptionProgress(nativePtr)
    fun getTabData(outStrings: IntArray, outFrets: IntArray, outTimes: FloatArray, outDurations: FloatArray, maxNotes: Int) =
        nativeGetTabData(nativePtr, outStrings, outFrets, outTimes, outDurations, maxNotes)

    // Polyphonic transcription
    fun transcribePolyphonic(data: FloatArray, numSamples: Int, sampleRate: Int): Boolean = nativeTranscribePolyphonic(nativePtr, data, numSamples, sampleRate)
    fun hasPolyphonicResult(): Boolean = nativeHasPolyphonicResult(nativePtr)
    fun getPolyphonicNoteCount(): Int = nativeGetPolyphonicNoteCount(nativePtr)

    // Tone matcher
    fun loadAudioForToneMatcher(data: FloatArray, numFrames: Int, numChannels: Int) = nativeLoadAudioForToneMatcher(nativePtr, data, numFrames, numChannels)
    fun hasToneMatcherProfile(): Boolean = nativeHasToneMatcherProfile(nativePtr)
    fun getRecommendedDistortionDrive(): Float = nativeGetRecommendedDistortionDrive(nativePtr)
    fun getRecommendedDistortionTone(): Float = nativeGetRecommendedDistortionTone(nativePtr)
    fun getRecommendedDistortionLevel(): Float = nativeGetRecommendedDistortionLevel(nativePtr)
    fun getRecommendedAmpSimGain(): Float = nativeGetRecommendedAmpSimGain(nativePtr)
    fun getRecommendedAmpSimTone(): Float = nativeGetRecommendedAmpSimTone(nativePtr)
    fun getRecommendedAmpSimMaster(): Float = nativeGetRecommendedAmpSimMaster(nativePtr)
    fun getRecommendedEqBass(): Float = nativeGetRecommendedEqBass(nativePtr)
    fun getRecommendedEqMid(): Float = nativeGetRecommendedEqMid(nativePtr)
    fun getRecommendedEqTreble(): Float = nativeGetRecommendedEqTreble(nativePtr)
    fun getRecommendedChorusRate(): Float = nativeGetRecommendedChorusRate(nativePtr)
    fun getRecommendedChorusDepth(): Float = nativeGetRecommendedChorusDepth(nativePtr)
    fun getRecommendedChorusMix(): Float = nativeGetRecommendedChorusMix(nativePtr)
    fun getRecommendedDelayMix(): Float = nativeGetRecommendedDelayMix(nativePtr)
    fun getRecommendedDelayFeedback(): Float = nativeGetRecommendedDelayFeedback(nativePtr)
    fun getRecommendedDelayTime(): Float = nativeGetRecommendedDelayTime(nativePtr)
    fun getRecommendedReverbSize(): Float = nativeGetRecommendedReverbSize(nativePtr)
    fun getRecommendedReverbMix(): Float = nativeGetRecommendedReverbMix(nativePtr)

    // Metronome
    fun setMetronomeEnabled(enabled: Boolean) = nativeSetMetronomeEnabled(nativePtr, enabled)
    fun isMetronomeEnabled(): Boolean = nativeIsMetronomeEnabled(nativePtr)
    fun setMetronomeBpm(bpm: Float) = nativeSetMetronomeBpm(nativePtr, bpm)
    fun getMetronomeBpm(): Float = nativeGetMetronomeBpm(nativePtr)
    fun setMetronomeVolume(vol: Float) = nativeSetMetronomeVolume(nativePtr, vol)
    fun getMetronomeVolume(): Float = nativeGetMetronomeVolume(nativePtr)
    fun tapTempo() = nativeTapTempo(nativePtr)
    fun setMetronomeActive(active: Boolean) = nativeSetMetronomeActive(nativePtr, active)

    // Looper
    fun setLooperMode(mode: Int) = nativeSetLooperMode(nativePtr, mode)
    fun getLooperMode(): Int = nativeGetLooperMode(nativePtr)
    fun looperToggleRecord() = nativeLooperToggleRecord(nativePtr)
    fun looperUndoOverdub() = nativeLooperUndoOverdub(nativePtr)
    fun looperClear() = nativeLooperClear(nativePtr)
    fun getLooperLoopLength(): Int = nativeGetLooperLoopLength(nativePtr)
    fun getLooperLoopDuration(): Float = nativeGetLooperLoopDuration(nativePtr)

    // Preset serialization
    fun savePresetToFile(path: String, presetIndex: Int): Boolean = nativeSavePresetToFile(nativePtr, path, presetIndex)
    fun loadPresetFromFile(path: String): Boolean = nativeLoadPresetFromFile(nativePtr, path)

    // MIDI
    fun setMidiCcMapping(cc: Int, effectIndex: Int, paramId: Int) = nativeSetMidiCcMapping(nativePtr, cc, effectIndex, paramId)
    fun getMidiCcEffect(cc: Int): Int = nativeGetMidiCcEffect(nativePtr, cc)
    fun getMidiCcParam(cc: Int): Int = nativeGetMidiCcParam(nativePtr, cc)
    fun handleMidiMessage(status: Int, data1: Int, data2: Int) = nativeHandleMidiMessage(nativePtr, status, data1, data2)
    fun setMidiLearnMode(enabled: Boolean) = nativeSetMidiLearnMode(nativePtr, enabled)
    fun setMidiLearnTarget(effectIdx: Int, paramId: Int) = nativeSetMidiLearnTarget(nativePtr, effectIdx, paramId)

    // --- Native methods ---
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(ptr: Long)
    private external fun nativeStart(ptr: Long): Boolean
    private external fun nativeStop(ptr: Long)
    private external fun nativeSetEffectEnabled(ptr: Long, index: Int, enabled: Boolean)
    private external fun nativeIsEffectEnabled(ptr: Long, index: Int): Boolean
    private external fun nativeSetEffectParameter(ptr: Long, index: Int, paramId: Int, value: Float)
    private external fun nativeGetEffectParameter(ptr: Long, index: Int, paramId: Int): Float
    private external fun nativeLoadPreset(ptr: Long, preset: Int)

    // Tuner
    private external fun nativeLoadAudioForTuner(ptr: Long, data: FloatArray, numFrames: Int, numChannels: Int)
    private external fun nativeGetTunerFrequency(ptr: Long): Float
    private external fun nativeGetTunerNoteIndex(ptr: Long): Int
    private external fun nativeGetTunerOctave(ptr: Long): Int
    private external fun nativeGetTunerCents(ptr: Long): Float
    private external fun nativeIsTunerNoteDetected(ptr: Long): Boolean
    private external fun nativeGetTunerCurrentTuningName(ptr: Long): String
    private external fun nativeGetTunerNoteName(ptr: Long, index: Int): String
    private external fun nativeSetTunerMuteDry(ptr: Long, mute: Boolean)
    private external fun nativeIsTunerMuteDry(ptr: Long): Boolean

    // Monitoring
    private external fun nativeSetMonitoringEnabled(ptr: Long, enabled: Boolean)
    private external fun nativeIsMonitoringEnabled(ptr: Long): Boolean

    // Recording
    private external fun nativeStartRecording(ptr: Long, filePath: String)
    private external fun nativeStopRecording(ptr: Long)
    private external fun nativeIsRecording(ptr: Long): Boolean
    private external fun nativeLoadImpulseResponse(ptr: Long, path: String): Boolean

    // Transcription
    private external fun nativeTranscribeAudio(ptr: Long, data: FloatArray, numSamples: Int, sampleRate: Int): Boolean
    private external fun nativeHasTranscription(ptr: Long): Boolean
    private external fun nativeGetNumMeasures(ptr: Long): Int
    private external fun nativeGetTranscriptionProgress(ptr: Long): Float
    private external fun nativeGetTabData(ptr: Long, outStrings: IntArray, outFrets: IntArray, outTimes: FloatArray, outDurations: FloatArray, maxNotes: Int)

    // Polyphonic transcription
    private external fun nativeTranscribePolyphonic(ptr: Long, data: FloatArray, numSamples: Int, sampleRate: Int): Boolean
    private external fun nativeHasPolyphonicResult(ptr: Long): Boolean
    private external fun nativeGetPolyphonicNoteCount(ptr: Long): Int

    // Tone matcher
    private external fun nativeLoadAudioForToneMatcher(ptr: Long, data: FloatArray, numFrames: Int, numChannels: Int)
    private external fun nativeHasToneMatcherProfile(ptr: Long): Boolean
    private external fun nativeGetRecommendedDistortionDrive(ptr: Long): Float
    private external fun nativeGetRecommendedDistortionTone(ptr: Long): Float
    private external fun nativeGetRecommendedDistortionLevel(ptr: Long): Float
    private external fun nativeGetRecommendedAmpSimGain(ptr: Long): Float
    private external fun nativeGetRecommendedAmpSimTone(ptr: Long): Float
    private external fun nativeGetRecommendedAmpSimMaster(ptr: Long): Float
    private external fun nativeGetRecommendedEqBass(ptr: Long): Float
    private external fun nativeGetRecommendedEqMid(ptr: Long): Float
    private external fun nativeGetRecommendedEqTreble(ptr: Long): Float
    private external fun nativeGetRecommendedChorusRate(ptr: Long): Float
    private external fun nativeGetRecommendedChorusDepth(ptr: Long): Float
    private external fun nativeGetRecommendedChorusMix(ptr: Long): Float
    private external fun nativeGetRecommendedDelayMix(ptr: Long): Float
    private external fun nativeGetRecommendedDelayFeedback(ptr: Long): Float
    private external fun nativeGetRecommendedDelayTime(ptr: Long): Float
    private external fun nativeGetRecommendedReverbSize(ptr: Long): Float
    private external fun nativeGetRecommendedReverbMix(ptr: Long): Float

    // Metronome
    private external fun nativeSetMetronomeEnabled(ptr: Long, enabled: Boolean)
    private external fun nativeIsMetronomeEnabled(ptr: Long): Boolean
    private external fun nativeSetMetronomeBpm(ptr: Long, bpm: Float)
    private external fun nativeGetMetronomeBpm(ptr: Long): Float
    private external fun nativeSetMetronomeVolume(ptr: Long, vol: Float)
    private external fun nativeGetMetronomeVolume(ptr: Long): Float
    private external fun nativeTapTempo(ptr: Long)
    private external fun nativeSetMetronomeActive(ptr: Long, active: Boolean)

    // Looper
    private external fun nativeSetLooperMode(ptr: Long, mode: Int)
    private external fun nativeGetLooperMode(ptr: Long): Int
    private external fun nativeLooperToggleRecord(ptr: Long)
    private external fun nativeLooperUndoOverdub(ptr: Long)
    private external fun nativeLooperClear(ptr: Long)
    private external fun nativeGetLooperLoopLength(ptr: Long): Int
    private external fun nativeGetLooperLoopDuration(ptr: Long): Float

    // Preset serialization
    private external fun nativeSavePresetToFile(ptr: Long, path: String, presetIndex: Int): Boolean
    private external fun nativeLoadPresetFromFile(ptr: Long, path: String): Boolean

    // MIDI
    private external fun nativeSetMidiCcMapping(ptr: Long, cc: Int, effectIndex: Int, paramId: Int)
    private external fun nativeGetMidiCcEffect(ptr: Long, cc: Int): Int
    private external fun nativeGetMidiCcParam(ptr: Long, cc: Int): Int
    private external fun nativeHandleMidiMessage(ptr: Long, status: Int, data1: Int, data2: Int)
    private external fun nativeSetMidiLearnMode(ptr: Long, enabled: Boolean)
    private external fun nativeSetMidiLearnTarget(ptr: Long, effectIdx: Int, paramId: Int)
}
