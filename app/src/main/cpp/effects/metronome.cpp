#include "metronome.h"
#include <cmath>
#include <cstring>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "Metronome", __VA_ARGS__)

Metronome::Metronome()
    : enabled_(false), bpm_(120.0f), volume_(0.5f),
      sampleRate_(48000), samplesPerBeat_(24000), phase_(0),
      clickPhase_(0.0f), lastTapTime_(0.0f), tapCount_(0) {
    for (int i = 0; i < 4; i++) tapHistory_[i] = 0.0f;
    samplesPerBeat_ = getSamplesPerBeat();
}

int Metronome::getSamplesPerBeat() const {
    return (int)(60.0f / bpm_ * sampleRate_ + 0.5f);
}

void Metronome::process(const float* input, float* output, int32_t numFrames, int32_t numChannels) {
    if (!enabled_) return;

    for (int i = 0; i < numFrames; ++i) {
        phase_++;
        if (phase_ >= samplesPerBeat_) {
            phase_ = 0;
        }

        // Generate click: short burst at the start of each beat
        float click = 0.0f;
        int clickLen = (int)(sampleRate_ * 0.01f); // 10ms click
        if (phase_ < clickLen) {
            float env = 1.0f - (float)phase_ / clickLen;
            // 1kHz sine wave with envelope
            click = sinf(2.0f * M_PI * 1000.0f * phase_ / sampleRate_) * env * volume_;
        }

        for (int c = 0; c < numChannels; ++c) {
            int idx = i * numChannels + c;
            // Mix click into output (additive, don't replace input)
            output[idx] = output[idx] + click;
        }
    }
}

void Metronome::setParameter(int id, float value) {
    switch (id) {
        case PARAM_BPM:
            bpm_ = 40.0f + value * 200.0f; // 40-240 BPM
            samplesPerBeat_ = getSamplesPerBeat();
            phase_ = 0;
            break;
        case PARAM_VOLUME:
            volume_ = value;
            break;
    }
}

float Metronome::getParameter(int id) {
    switch (id) {
        case PARAM_BPM: return (bpm_ - 40.0f) / 200.0f;
        case PARAM_VOLUME: return volume_;
        default: return 0.0f;
    }
}

void Metronome::reset() {
    phase_ = 0;
}

void Metronome::tapTempo() {
    float now = 0.0f;
    // Get approximate time using a simple counter
    static unsigned long long tickCounter = 0;
    tickCounter++;
    now = tickCounter / 48000.0f;

    if (tapCount_ > 0) {
        float interval = now - lastTapTime_;
        if (interval > 2.0f) {
            // More than 2 seconds between taps - reset
            tapCount_ = 0;
        } else if (tapCount_ < 4) {
            tapHistory_[tapCount_ - 1] = interval;
        } else {
            // Shift history
            for (int i = 0; i < 3; i++) tapHistory_[i] = tapHistory_[i + 1];
            tapHistory_[3] = interval;
        }
    }
    lastTapTime_ = now;
    tapCount_++;

    // Calculate average BPM
    if (tapCount_ >= 2) {
        int validTaps = (tapCount_ < 4) ? tapCount_ - 1 : 4;
        float sum = 0;
        for (int i = 0; i < validTaps; i++) sum += tapHistory_[i];
        float avgInterval = sum / validTaps;
        if (avgInterval > 0.0f) {
            bpm_ = 60.0f / avgInterval;
            bpm_ = bpm_ < 40.0f ? 40.0f : (bpm_ > 240.0f ? 240.0f : bpm_);
            samplesPerBeat_ = getSamplesPerBeat();
            phase_ = 0;
        }
    }
}
