package com.kaonixx.guitarix

class GuitarEngine {
    companion object {
        init { System.loadLibrary("guitarix_engine") }

        // Effect indices — must match engine.h enum EffectIndex
        const val FX_DISTORTION = 0
        const val FX_AMP_SIM    = 1
        const val FX_EQ         = 2
        const val FX_CHORUS     = 3
        const val FX_DELAY      = 4
        const val FX_REVERB     = 5

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

        val effectNames = listOf("Distortion", "Amp Sim", "EQ", "Chorus", "Delay", "Reverb")
        val presetNames = listOf("Clean", "Crunch", "Lead", "Metal", "Ambient")
    }

    private var nativePtr: Long = 0

    init { nativePtr = nativeCreate() }

    protected fun finalize() {
        if (nativePtr != 0L) nativeDestroy(nativePtr)
    }

    fun start(): Boolean = nativeStart(nativePtr)
    fun stop() = nativeStop(nativePtr)

    fun setEffectEnabled(index: Int, enabled: Boolean) =
        nativeSetEffectEnabled(nativePtr, index, enabled)
    fun isEffectEnabled(index: Int): Boolean =
        nativeIsEffectEnabled(nativePtr, index)

    fun setEffectParameter(index: Int, paramId: Int, value: Float) =
        nativeSetEffectParameter(nativePtr, index, paramId, value)
    fun getEffectParameter(index: Int, paramId: Int): Float =
        nativeGetEffectParameter(nativePtr, index, paramId)

    fun loadPreset(preset: Int) = nativeLoadPreset(nativePtr, preset)

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(ptr: Long)
    private external fun nativeStart(ptr: Long): Boolean
    private external fun nativeStop(ptr: Long)
    private external fun nativeSetEffectEnabled(ptr: Long, index: Int, enabled: Boolean)
    private external fun nativeIsEffectEnabled(ptr: Long, index: Int): Boolean
    private external fun nativeSetEffectParameter(ptr: Long, index: Int, paramId: Int, value: Float)
    private external fun nativeGetEffectParameter(ptr: Long, index: Int, paramId: Int): Float
    private external fun nativeLoadPreset(ptr: Long, preset: Int)
}
