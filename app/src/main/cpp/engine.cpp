#include "engine.h"
#include "effects/distortion.h"
#include "effects/delay.h"
#include "effects/reverb.h"
#include "effects/eq.h"
#include "effects/ampsim.h"
#include "effects/chorus.h"
#include <oboe/Oboe.h>
#include <algorithm>
#include <cstring>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "GuitarixEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Effect indices
enum EffectIndex {
    FX_DISTORTION = 0,
    FX_AMP_SIM,
    FX_EQ,
    FX_CHORUS,
    FX_DELAY,
    FX_REVERB,
    FX_COUNT
};

AudioEngine::AudioEngine()
    : currentPreset_(0) {
    effects_.resize(FX_COUNT);
    enabled_.resize(FX_COUNT, false);
    initEffects();
}

AudioEngine::~AudioEngine() {
    stop();
}

void AudioEngine::initEffects() {
    effects_[FX_DISTORTION] = std::make_unique<Distortion>();
    effects_[FX_AMP_SIM]    = std::make_unique<AmpSim>();
    effects_[FX_EQ]         = std::make_unique<EQ>();
    effects_[FX_CHORUS]     = std::make_unique<Chorus>();
    effects_[FX_DELAY]      = std::make_unique<Delay>();
    effects_[FX_REVERB]     = std::make_unique<Reverb>();

    // Default: amp sim on, rest off
    enabled_[FX_AMP_SIM] = true;
}

bool AudioEngine::start() {
    if (stream_) return true;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(oboe::ChannelCount::Mono);
    builder.setSampleRate(48000);
    builder.setFramesPerDataCallback(256);
    builder.setDataCallback(this);
    builder.setErrorCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGI("Failed to open input stream: %s", oboe::convertToText(result));
        // Fallback: open output-only stream for stand-alone processing
        builder.setDirection(oboe::Direction::Output);
        result = builder.openStream(stream_);
        if (result != oboe::Result::OK) {
            LOGI("Failed to open output stream: %s", oboe::convertToText(result));
            return false;
        }
    }

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGI("Failed to start stream: %s", oboe::convertToText(result));
        return false;
    }

    LOGI("Audio stream started: %d channels, %d rate",
         stream_->getChannelCount(), stream_->getSampleRate());
    return true;
}

void AudioEngine::stop() {
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames) {

    int32_t numChannels = audioStream->getChannelCount();
    float *buffer = static_cast<float*>(audioData);

    // Zero-fill for output-only mode (no real input)
    if (audioStream->getDirection() == oboe::Direction::Output) {
        memset(buffer, 0, numFrames * numChannels * sizeof(float));
    }

    // Apply effects
    std::lock_guard<std::mutex> lock(mutex_);
    buildSignalChain(buffer, numFrames, numChannels);

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGI("Audio stream error: %s", oboe::convertToText(error));
}

void AudioEngine::buildSignalChain(float* buffer, int32_t numFrames, int32_t numChannels) {
    for (int i = 0; i < FX_COUNT; ++i) {
        if (enabled_[i] && effects_[i]) {
            effects_[i]->process(buffer, buffer, numFrames, numChannels);
        }
    }
}

void AudioEngine::setEffectEnabled(int index, bool enabled) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (index >= 0 && index < FX_COUNT) {
        enabled_[index] = enabled;
        if (!enabled && effects_[index]) {
            effects_[index]->reset();
        }
    }
}

bool AudioEngine::isEffectEnabled(int index) {
    if (index >= 0 && index < FX_COUNT) return enabled_[index];
    return false;
}

void AudioEngine::setEffectParameter(int index, int paramId, float value) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (index >= 0 && index < FX_COUNT && effects_[index]) {
        effects_[index]->setParameter(paramId, value);
    }
}

float AudioEngine::getEffectParameter(int index, int paramId) {
    if (index >= 0 && index < FX_COUNT && effects_[index]) {
        return effects_[index]->getParameter(paramId);
    }
    return 0.0f;
}

void AudioEngine::loadPreset(int preset) {
    std::lock_guard<std::mutex> lock(mutex_);
    currentPreset_ = preset;

    for (auto& effect : effects_) {
        if (effect) effect->reset();
    }

    switch (preset) {
        case 0: // Clean
            enabled_[FX_DISTORTION] = false;
            enabled_[FX_AMP_SIM] = true;
            enabled_[FX_EQ] = false;
            enabled_[FX_CHORUS] = false;
            enabled_[FX_DELAY] = true;
            enabled_[FX_REVERB] = true;
            effects_[FX_DELAY]->setParameter(0, 0.25f);
            effects_[FX_REVERB]->setParameter(0, 0.3f);
            effects_[FX_REVERB]->setParameter(1, 0.2f);
            effects_[FX_DELAY]->setParameter(1, 0.3f);
            effects_[FX_DELAY]->setParameter(2, 400.0f);
            effects_[FX_AMP_SIM]->setParameter(0, 0.3f);
            effects_[FX_AMP_SIM]->setParameter(1, 0.6f);
            break;

        case 1: // Crunch
            enabled_[FX_DISTORTION] = true;
            enabled_[FX_AMP_SIM] = true;
            enabled_[FX_EQ] = true;
            enabled_[FX_CHORUS] = false;
            enabled_[FX_DELAY] = false;
            enabled_[FX_REVERB] = true;
            effects_[FX_DISTORTION]->setParameter(0, 0.3f);
            effects_[FX_DISTORTION]->setParameter(1, 0.5f);
            effects_[FX_DISTORTION]->setParameter(2, 0.7f);
            effects_[FX_AMP_SIM]->setParameter(0, 0.5f);
            effects_[FX_AMP_SIM]->setParameter(1, 0.7f);
            effects_[FX_AMP_SIM]->setParameter(2, 0.5f);
            effects_[FX_EQ]->setParameter(0, 0.5f);
            effects_[FX_EQ]->setParameter(1, 0.4f);
            effects_[FX_EQ]->setParameter(2, 0.6f);
            effects_[FX_REVERB]->setParameter(0, 0.2f);
            effects_[FX_REVERB]->setParameter(1, 0.15f);
            break;

        case 2: // Lead
            enabled_[FX_DISTORTION] = true;
            enabled_[FX_AMP_SIM] = true;
            enabled_[FX_EQ] = true;
            enabled_[FX_CHORUS] = false;
            enabled_[FX_DELAY] = true;
            enabled_[FX_REVERB] = true;
            effects_[FX_DISTORTION]->setParameter(0, 0.7f);
            effects_[FX_DISTORTION]->setParameter(1, 0.6f);
            effects_[FX_DISTORTION]->setParameter(2, 0.85f);
            effects_[FX_AMP_SIM]->setParameter(0, 0.7f);
            effects_[FX_AMP_SIM]->setParameter(1, 0.5f);
            effects_[FX_EQ]->setParameter(0, 0.4f);
            effects_[FX_EQ]->setParameter(1, 0.3f);
            effects_[FX_EQ]->setParameter(2, 0.7f);
            effects_[FX_DELAY]->setParameter(0, 0.35f);
            effects_[FX_DELAY]->setParameter(1, 0.4f);
            effects_[FX_DELAY]->setParameter(2, 450.0f);
            effects_[FX_REVERB]->setParameter(0, 0.3f);
            effects_[FX_REVERB]->setParameter(1, 0.25f);
            break;

        case 3: // Metal
            enabled_[FX_DISTORTION] = true;
            enabled_[FX_AMP_SIM] = true;
            enabled_[FX_EQ] = true;
            enabled_[FX_CHORUS] = false;
            enabled_[FX_DELAY] = false;
            enabled_[FX_REVERB] = false;
            effects_[FX_DISTORTION]->setParameter(0, 0.95f);
            effects_[FX_DISTORTION]->setParameter(1, 0.4f);
            effects_[FX_DISTORTION]->setParameter(2, 0.9f);
            effects_[FX_AMP_SIM]->setParameter(0, 1.0f);
            effects_[FX_AMP_SIM]->setParameter(1, 0.4f);
            effects_[FX_EQ]->setParameter(0, 0.7f);
            effects_[FX_EQ]->setParameter(1, 0.2f);
            effects_[FX_EQ]->setParameter(2, 0.6f);
            break;

        case 4: // Ambient
            enabled_[FX_DISTORTION] = false;
            enabled_[FX_AMP_SIM] = true;
            enabled_[FX_EQ] = true;
            enabled_[FX_CHORUS] = true;
            enabled_[FX_DELAY] = true;
            enabled_[FX_REVERB] = true;
            effects_[FX_CHORUS]->setParameter(0, 0.5f);
            effects_[FX_CHORUS]->setParameter(1, 0.4f);
            effects_[FX_CHORUS]->setParameter(2, 0.4f);
            effects_[FX_DELAY]->setParameter(0, 0.4f);
            effects_[FX_DELAY]->setParameter(1, 0.3f);
            effects_[FX_DELAY]->setParameter(2, 600.0f);
            effects_[FX_REVERB]->setParameter(0, 0.8f);
            effects_[FX_REVERB]->setParameter(1, 0.5f);
            effects_[FX_AMP_SIM]->setParameter(0, 0.3f);
            effects_[FX_AMP_SIM]->setParameter(1, 0.8f);
            effects_[FX_EQ]->setParameter(0, 0.3f);
            effects_[FX_EQ]->setParameter(1, 0.5f);
            effects_[FX_EQ]->setParameter(2, 0.6f);
            break;
    }
}
