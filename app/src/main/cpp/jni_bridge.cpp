#include <jni.h>
#include "engine.h"
#include <vector>

static AudioEngine* getEngine(jlong ptr) {
    return reinterpret_cast<AudioEngine*>(static_cast<intptr_t>(ptr));
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeCreate(JNIEnv*, jobject) {
    auto* engine = new AudioEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeDestroy(JNIEnv*, jobject, jlong ptr) {
    delete getEngine(ptr);
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeStart(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeStop(JNIEnv*, jobject, jlong ptr) {
    getEngine(ptr)->stop();
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetEffectEnabled(JNIEnv*, jobject,
    jlong ptr, jint index, jboolean enabled) {
    getEngine(ptr)->setEffectEnabled(index, enabled);
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeIsEffectEnabled(JNIEnv*, jobject,
    jlong ptr, jint index) {
    return getEngine(ptr)->isEffectEnabled(index) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetEffectParameter(JNIEnv*, jobject,
    jlong ptr, jint index, jint paramId, jfloat value) {
    getEngine(ptr)->setEffectParameter(index, paramId, value);
}

JNIEXPORT jfloat JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetEffectParameter(JNIEnv*, jobject,
    jlong ptr, jint index, jint paramId) {
    return getEngine(ptr)->getEffectParameter(index, paramId);
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeLoadPreset(JNIEnv*, jobject,
    jlong ptr, jint preset) {
    getEngine(ptr)->loadPreset(preset);
}

// Tuner JNI
JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeLoadAudioForTuner(JNIEnv* env, jobject,
    jlong ptr, jfloatArray data, jint numFrames, jint numChannels) {
    auto* engine = getEngine(ptr);
    if (!engine || !data) return;
    jfloat* arr = env->GetFloatArrayElements(data, nullptr);
    std::vector<float> buffer(arr, arr + env->GetArrayLength(data));
    env->ReleaseFloatArrayElements(data, arr, JNI_ABORT);
    engine->loadAudioForTuner(buffer.data(), numFrames, numChannels);
}

JNIEXPORT jfloat JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetTunerFrequency(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getTunerFrequency();
}

JNIEXPORT jint JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetTunerNoteIndex(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getTunerNoteIndex();
}

JNIEXPORT jint JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetTunerOctave(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getTunerOctave();
}

JNIEXPORT jfloat JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetTunerCents(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getTunerCents();
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeIsTunerNoteDetected(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->isTunerNoteDetected() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetTunerCurrentTuningName(JNIEnv* env, jobject, jlong ptr) {
    return env->NewStringUTF(getEngine(ptr)->getTunerCurrentTuningName());
}

JNIEXPORT jstring JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetTunerNoteName(JNIEnv* env, jobject, jlong ptr, jint index) {
    return env->NewStringUTF(getEngine(ptr)->getTunerNoteName(index));
}

// Tuner mute dry
JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetTunerMuteDry(JNIEnv*, jobject, jlong ptr, jboolean mute) {
    getEngine(ptr)->setTunerMuteDry(mute);
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeIsTunerMuteDry(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->isTunerMuteDry() ? JNI_TRUE : JNI_FALSE;
}

// Monitoring
JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetMonitoringEnabled(JNIEnv*, jobject, jlong ptr, jboolean enabled) {
    getEngine(ptr)->setMonitoringEnabled(enabled);
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeIsMonitoringEnabled(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->isMonitoringEnabled() ? JNI_TRUE : JNI_FALSE;
}

// Tone matcher JNI
JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeLoadAudioForToneMatcher(JNIEnv* env, jobject,
    jlong ptr, jfloatArray data, jint numFrames, jint numChannels) {
    auto* engine = getEngine(ptr);
    if (!engine || !data) return;
    jfloat* arr = env->GetFloatArrayElements(data, nullptr);
    std::vector<float> buffer(arr, arr + env->GetArrayLength(data));
    env->ReleaseFloatArrayElements(data, arr, JNI_ABORT);
    engine->loadAudioForToneMatcher(buffer.data(), numFrames, numChannels);
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeHasToneMatcherProfile(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->hasToneMatcherProfile() ? JNI_TRUE : JNI_FALSE;
}

#define TONE_MATCHER_GETTER(name) \
JNIEXPORT jfloat JNICALL \
Java_com_kaosnet_krank_KrankEngine_nativeGetRecommended##name(JNIEnv*, jobject, jlong ptr) { \
    return getEngine(ptr)->getRecommended##name(); \
}

TONE_MATCHER_GETTER(DistortionDrive)
TONE_MATCHER_GETTER(DistortionTone)
TONE_MATCHER_GETTER(DistortionLevel)
TONE_MATCHER_GETTER(AmpSimGain)
TONE_MATCHER_GETTER(AmpSimTone)
TONE_MATCHER_GETTER(AmpSimMaster)
TONE_MATCHER_GETTER(EqBass)
TONE_MATCHER_GETTER(EqMid)
TONE_MATCHER_GETTER(EqTreble)
TONE_MATCHER_GETTER(ChorusRate)
TONE_MATCHER_GETTER(ChorusDepth)
TONE_MATCHER_GETTER(ChorusMix)
TONE_MATCHER_GETTER(DelayMix)
TONE_MATCHER_GETTER(DelayFeedback)
TONE_MATCHER_GETTER(DelayTime)
TONE_MATCHER_GETTER(ReverbSize)
TONE_MATCHER_GETTER(ReverbMix)

// Recording
JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeStartRecording(JNIEnv* env, jobject,
    jlong ptr, jstring filePath) {
    auto* engine = getEngine(ptr);
    if (!engine) return;
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    engine->startRecording(path);
    env->ReleaseStringUTFChars(filePath, path);
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeStopRecording(JNIEnv*, jobject, jlong ptr) {
    getEngine(ptr)->stopRecording();
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeIsRecording(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->isRecording() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeLoadImpulseResponse(JNIEnv* env, jobject,
    jlong ptr, jstring path) {
    auto* engine = getEngine(ptr);
    if (!engine) return JNI_FALSE;
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    bool result = engine->loadImpulseResponse(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return result ? JNI_TRUE : JNI_FALSE;
}

// Transcription JNI
JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeTranscribeAudio(JNIEnv* env, jobject,
    jlong ptr, jfloatArray data, jint numSamples, jint sampleRate) {
    auto* engine = getEngine(ptr);
    if (!engine || !data) return JNI_FALSE;
    jfloat* arr = env->GetFloatArrayElements(data, nullptr);
    bool result = engine->transcribeAudio(arr, numSamples, sampleRate);
    env->ReleaseFloatArrayElements(data, arr, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeHasTranscription(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->hasTranscription() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetNumMeasures(JNIEnv*, jobject, jlong ptr) {
    return (jint)getEngine(ptr)->getNumMeasures();
}

JNIEXPORT jfloat JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetTranscriptionProgress(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getTranscriptionProgress();
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetTabData(JNIEnv* env, jobject,
    jlong ptr, jintArray outStrings, jintArray outFrets,
    jfloatArray outTimes, jfloatArray outDurations, jint maxNotes) {
    auto* engine = getEngine(ptr);
    if (!engine) return;
    jint* strings = env->GetIntArrayElements(outStrings, nullptr);
    jint* frets = env->GetIntArrayElements(outFrets, nullptr);
    jfloat* times = env->GetFloatArrayElements(outTimes, nullptr);
    jfloat* durations = env->GetFloatArrayElements(outDurations, nullptr);
    engine->getTabData(strings, frets, times, durations, maxNotes);
    env->ReleaseIntArrayElements(outStrings, strings, 0);
    env->ReleaseIntArrayElements(outFrets, frets, 0);
    env->ReleaseFloatArrayElements(outTimes, times, 0);
    env->ReleaseFloatArrayElements(outDurations, durations, 0);
}

// Polyphonic transcription
JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeTranscribePolyphonic(JNIEnv* env, jobject,
    jlong ptr, jfloatArray data, jint numSamples, jint sampleRate) {
    auto* engine = getEngine(ptr);
    if (!engine || !data) return JNI_FALSE;
    jfloat* arr = env->GetFloatArrayElements(data, nullptr);
    bool result = engine->transcribePolyphonic(arr, numSamples, sampleRate);
    env->ReleaseFloatArrayElements(data, arr, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeHasPolyphonicResult(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->hasPolyphonicResult() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetPolyphonicNoteCount(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getPolyphonicNoteCount();
}

// Metronome JNI
JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetMetronomeEnabled(JNIEnv*, jobject, jlong ptr, jboolean enabled) {
    getEngine(ptr)->setMetronomeEnabled(enabled);
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeIsMetronomeEnabled(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->isMetronomeEnabled() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetMetronomeBpm(JNIEnv*, jobject, jlong ptr, jfloat bpm) {
    getEngine(ptr)->setMetronomeBpm(bpm);
}

JNIEXPORT jfloat JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetMetronomeBpm(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getMetronomeBpm();
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetMetronomeVolume(JNIEnv*, jobject, jlong ptr, jfloat vol) {
    getEngine(ptr)->setMetronomeVolume(vol);
}

JNIEXPORT jfloat JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetMetronomeVolume(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getMetronomeVolume();
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeTapTempo(JNIEnv*, jobject, jlong ptr) {
    getEngine(ptr)->tapTempo();
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetMetronomeActive(JNIEnv*, jobject, jlong ptr, jboolean active) {
    getEngine(ptr)->setMetronomeActive(active);
}

// Looper JNI
JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetLooperMode(JNIEnv*, jobject, jlong ptr, jint mode) {
    getEngine(ptr)->setLooperMode(mode);
}

JNIEXPORT jint JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetLooperMode(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getLooperMode();
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeLooperToggleRecord(JNIEnv*, jobject, jlong ptr) {
    getEngine(ptr)->looperToggleRecord();
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeLooperUndoOverdub(JNIEnv*, jobject, jlong ptr) {
    getEngine(ptr)->looperUndoOverdub();
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeLooperClear(JNIEnv*, jobject, jlong ptr) {
    getEngine(ptr)->looperClear();
}

JNIEXPORT jint JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetLooperLoopLength(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getLooperLoopLength();
}

JNIEXPORT jfloat JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetLooperLoopDuration(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->getLooperLoopDuration();
}

// Preset serialization JNI
JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSavePresetToFile(JNIEnv* env, jobject,
    jlong ptr, jstring path, jint presetIndex) {
    auto* engine = getEngine(ptr);
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    bool result = engine->savePresetToFile(cpath, presetIndex);
    env->ReleaseStringUTFChars(path, cpath);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeLoadPresetFromFile(JNIEnv* env, jobject,
    jlong ptr, jstring path) {
    auto* engine = getEngine(ptr);
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    bool result = engine->loadPresetFromFile(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return result ? JNI_TRUE : JNI_FALSE;
}

// MIDI JNI
JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetMidiCcMapping(JNIEnv*, jobject,
    jlong ptr, jint cc, jint effectIndex, jint paramId) {
    getEngine(ptr)->setMidiCcMapping(cc, effectIndex, paramId);
}

JNIEXPORT jint JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetMidiCcEffect(JNIEnv*, jobject, jlong ptr, jint cc) {
    return getEngine(ptr)->getMidiCcEffect(cc);
}

JNIEXPORT jint JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeGetMidiCcParam(JNIEnv*, jobject, jlong ptr, jint cc) {
    return getEngine(ptr)->getMidiCcParam(cc);
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeHandleMidiMessage(JNIEnv*, jobject,
    jlong ptr, jint status, jint data1, jint data2) {
    getEngine(ptr)->handleMidiMessage(status, data1, data2);
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetMidiLearnMode(JNIEnv*, jobject, jlong ptr, jboolean enabled) {
    getEngine(ptr)->setMidiLearnMode(enabled);
}

JNIEXPORT void JNICALL
Java_com_kaosnet_krank_KrankEngine_nativeSetMidiLearnTarget(JNIEnv*, jobject,
    jlong ptr, jint effectIdx, jint paramId) {
    getEngine(ptr)->setMidiLearnTarget(effectIdx, paramId);
}

} // extern "C"
