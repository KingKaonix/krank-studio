#include "chorus.h"
#include <cmath>
#include <algorithm>
#include <cstring>

Chorus::Chorus() {
    buffer_.resize(sampleRate_ / 2, 0.0f);  // 500ms
}

void Chorus::process(const float* input, float* output, int32_t numFrames, int32_t numChannels) {
    float lfoRate = 0.5f + rate_ * 3.0f;  // 0.5 - 3.5 Hz
    float modDepth = 0.005f + depth_ * 0.015f;  // 5-20ms in fractional seconds
    float phaseInc = lfoRate / sampleRate_;

    for (int i = 0; i < numFrames; ++i) {
        phase_ += phaseInc;
        if (phase_ >= 1.0f) phase_ -= 1.0f;

        // LFO: triangle wave
        float lfo = std::abs(phase_ * 4.0f - 2.0f) - 1.0f;
        int modSamples = static_cast<int>(lfo * modDepth * sampleRate_);
        int readPos = writePos_ - std::abs(modSamples) - 1;
        if (readPos < 0) readPos += buffer_.size();

        for (int ch = 0; ch < numChannels; ++ch) {
            int idx = i * numChannels + ch;
            float dry = input[idx];
            float wet = buffer_[readPos];
            buffer_[writePos_] = dry;
            output[idx] = dry * (1.0f - mix_) + wet * mix_;
        }

        writePos_ = (writePos_ + 1) % buffer_.size();
    }
}

void Chorus::setParameter(int id, float value) {
    value = std::clamp(value, 0.0f, 1.0f);
    switch (id) {
        case RATE:  rate_ = value; break;
        case DEPTH: depth_ = value; break;
        case MIX:   mix_ = value; break;
    }
}

float Chorus::getParameter(int id) {
    switch (id) {
        case RATE: return rate_;
        case DEPTH: return depth_;
        case MIX: return mix_;
    }
    return 0;
}

void Chorus::reset() {
    std::fill(buffer_.begin(), buffer_.end(), 0.0f);
    writePos_ = 0;
    phase_ = 0;
}
