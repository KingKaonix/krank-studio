#include <jni.h>
#include "engine.h"

static AudioEngine* getEngine(jlong ptr) {
    return reinterpret_cast<AudioEngine*>(static_cast<intptr_t>(ptr));
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_kaonixx_guitarix_GuitarEngine_nativeCreate(JNIEnv*, jobject) {
    auto* engine = new AudioEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_kaonixx_guitarix_GuitarEngine_nativeDestroy(JNIEnv*, jobject, jlong ptr) {
    delete getEngine(ptr);
}

JNIEXPORT jboolean JNICALL
Java_com_kaonixx_guitarix_GuitarEngine_nativeStart(JNIEnv*, jobject, jlong ptr) {
    return getEngine(ptr)->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_kaonixx_guitarix_GuitarEngine_nativeStop(JNIEnv*, jobject, jlong ptr) {
    getEngine(ptr)->stop();
}

JNIEXPORT void JNICALL
Java_com_kaonixx_guitarix_GuitarEngine_nativeSetEffectEnabled(JNIEnv*, jobject,
    jlong ptr, jint index, jboolean enabled) {
    getEngine(ptr)->setEffectEnabled(index, enabled);
}

JNIEXPORT jboolean JNICALL
Java_com_kaonixx_guitarix_GuitarEngine_nativeIsEffectEnabled(JNIEnv*, jobject,
    jlong ptr, jint index) {
    return getEngine(ptr)->isEffectEnabled(index) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_kaonixx_guitarix_GuitarEngine_nativeSetEffectParameter(JNIEnv*, jobject,
    jlong ptr, jint index, jint paramId, jfloat value) {
    getEngine(ptr)->setEffectParameter(index, paramId, value);
}

JNIEXPORT jfloat JNICALL
Java_com_kaonixx_guitarix_GuitarEngine_nativeGetEffectParameter(JNIEnv*, jobject,
    jlong ptr, jint index, jint paramId) {
    return getEngine(ptr)->getEffectParameter(index, paramId);
}

JNIEXPORT void JNICALL
Java_com_kaonixx_guitarix_GuitarEngine_nativeLoadPreset(JNIEnv*, jobject,
    jlong ptr, jint preset) {
    getEngine(ptr)->loadPreset(preset);
}

}  // extern "C"
