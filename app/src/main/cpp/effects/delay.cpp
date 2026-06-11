#include "delay.h"
#include <cmath>
#include <algorithm>
#include <cstring>

Delay::Delay() {
    buffer_.resize(sampleRate_ * 2, 0.0f);  // 2 second max delay
    updateDelaySamples();
}

void Delay::updateDelaySamples() {
    delaySamples_ = static_cast<int>(delayMs_ * sampleRate_ / 1000.0f);
    delaySamples_ = std::clamp(delaySamples_, 100, sampleRate_ * 2);
}

void Delay::process(const float* input, float* output, int32_t numFrames, int32_t numChannels) {
    for (int i = 0; i < numFrames; ++i) {
        int readPos = writePos_ - delaySamples_;
        if (readPos < 0) readPos += buffer_.size();

        for (int ch = 0; ch < numChannels; ++ch) {
            int idx = i * numChannels + ch;
            float wet = buffer_[readPos];
            float dry = input[idx];
            buffer_[writePos_] = dry + wet * feedback_;
            output[idx] = dry * (1.0f - mix_) + wet * mix_;
        }

        writePos_ = (writePos_ + 1) % buffer_.size();
    }
}

void Delay::setParameter(int id, float value) {
    switch (id) {
        case MIX:       mix_ = std::clamp(value, 0.0f, 1.0f); break;
        case FEEDBACK:  feedback_ = std::clamp(value, 0.0f, 0.95f); break;
        case DELAY_MS:  delayMs_ = std::clamp(value, 10.0f, 2000.0f); updateDelaySamples(); break;
    }
}

float Delay::getParameter(int id) {
    switch (id) {
        case MIX: return mix_;
        case FEEDBACK: return feedback_;
        case DELAY_MS: return delayMs_;
    }
    return 0;
}

void Delay::reset() {
    std::fill(buffer_.begin(), buffer_.end(), 0.0f);
    writePos_ = 0;
}
