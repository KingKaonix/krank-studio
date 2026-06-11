#include "distortion.h"
#include <cmath>
#include <algorithm>

Distortion::Distortion() : prevOutput_(0.0f) {}

void Distortion::process(const float* input, float* output, int32_t numFrames, int32_t numChannels) {
    float driveAmt = 1.0f + drive_ * 30.0f;  // 1 - 31x gain
    float makeup = level_ * 2.0f;

    for (int i = 0; i < numFrames * numChannels; ++i) {
        float x = input[i] * driveAmt;

        // Asymmetric soft-clip (tube-like)
        float y;
        if (x > 0) {
            y = 1.0f - expf(-x);
        } else {
            y = -1.0f + expf(x);
        }

        // Apply low-pass tone control (simple one-pole)
        y = prevOutput_ + (tone_ * (y - prevOutput_));
        prevOutput_ = y;

        output[i] = y * makeup;
    }
}

void Distortion::setParameter(int id, float value) {
    value = std::clamp(value, 0.0f, 1.0f);
    switch (id) {
        case DRIVE: drive_ = value; break;
        case TONE:  tone_ = value;  break;
        case LEVEL: level_ = value; break;
    }
}

float Distortion::getParameter(int id) {
    switch (id) {
        case DRIVE: return drive_;
        case TONE:  return tone_;
        case LEVEL: return level_;
    }
    return 0;
}

void Distortion::reset() { prevOutput_ = 0; }
