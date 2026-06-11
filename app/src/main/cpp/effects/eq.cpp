#include "eq.h"
#include <cmath>
#include <algorithm>

EQ::EQ() {}

void EQ::process(const float* input, float* output, int32_t numFrames, int32_t numChannels) {
    // Simple three-band shelving/peak EQ
    // Not computing proper biquads per frame for simplicity —
    // using a pragmatic 3-band tilt approach
    float bass = (bassGain_ - 0.5f) * 2.0f;    // -1 to +1
    float mid  = (midGain_ - 0.5f) * 2.0f;
    float treble = (trebleGain_ - 0.5f) * 2.0f;

    // Static one-pole HP/LP for band splitting
    static float lpState[2] = {0, 0};  // low-pass for bass
    static float bpState[2] = {0, 0};  // band-pass for mid
    static float hpState[2] = {0, 0};  // high-pass for treble

    float lpAlpha = 0.001f;  // ~80Hz @ 48kHz
    float bpAlpha = 0.01f;   // ~800Hz @ 48kHz
    float hpAlpha = 0.1f;    // ~5kHz @ 48kHz

    for (int i = 0; i < numFrames; ++i) {
        for (int ch = 0; ch < numChannels; ++ch) {
            int idx = i * numChannels + ch;
            float x = input[idx];

            // Simple band split (one-pole)
            lpState[ch] += lpAlpha * (x - lpState[ch]);

            float hpInput = x - lpState[ch];
            hpState[ch] += hpAlpha * (hpInput - hpState[ch]);

            float bpInput = hpInput - hpState[ch];
            bpState[ch] += bpAlpha * (bpInput - bpState[ch]);

            float bassBand = lpState[ch];
            float midBand = bpState[ch];
            float trebleBand = hpState[ch];

            // Apply gains with makeup
            float out = bassBand * (1.0f + bass * 0.8f)
                      + midBand * (1.0f + mid * 0.8f)
                      + trebleBand * (1.0f + treble * 0.8f);

            output[idx] = out;
        }
    }
}

void EQ::setParameter(int id, float value) {
    value = std::clamp(value, 0.0f, 1.0f);
    switch (id) {
        case BASS:   bassGain_ = value; break;
        case MID:    midGain_ = value; break;
        case TREBLE: trebleGain_ = value; break;
    }
}

float EQ::getParameter(int id) {
    switch (id) {
        case BASS: return bassGain_;
        case MID: return midGain_;
        case TREBLE: return trebleGain_;
    }
    return 0;
}

void EQ::reset() {}
