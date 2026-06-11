#include "ampsim.h"
#include <cmath>
#include <algorithm>

AmpSim::AmpSim() {}

void AmpSim::process(const float* input, float* output, int32_t numFrames, int32_t numChannels) {
    float preGain = 0.1f + gain_ * 2.0f;
    float postGain = master_ * 1.5f;
    float toneLP = 0.1f + tone_ * 0.8f;

    for (int i = 0; i < numFrames * numChannels; ++i) {
        float x = input[i] * preGain;

        // Pre-amp: asymmetrical clipping (tube-like)
        float y;
        if (x > 0) {
            y = x / (1.0f + x * 0.5f);
        } else {
            y = x / (1.0f - x * 0.3f);
        }

        // Power-amp: gentle compression + asymmetrical
        y = y * (1.0f - std::abs(y) * 0.3f);

        // Power supply sag simulation
        y *= (1.0f - std::abs(y) * 0.1f);

        // Output transformer low-pass
        y = lastOutput + toneLP * (y - lastOutput);
        lastOutput = y;

        output[i] = y * postGain;
    }
}

void AmpSim::setParameter(int id, float value) {
    value = std::clamp(value, 0.0f, 1.0f);
    switch (id) {
        case GAIN:   gain_ = value; break;
        case TONE:   tone_ = value; break;
        case MASTER: master_ = value; break;
    }
}

float AmpSim::getParameter(int id) {
    switch (id) {
        case GAIN: return gain_;
        case TONE: return tone_;
        case MASTER: return master_;
    }
    return 0;
}

void AmpSim::reset() { lastOutput = 0; }
