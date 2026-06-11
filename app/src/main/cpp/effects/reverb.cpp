#include "reverb.h"
#include <cmath>
#include <algorithm>
#include <cstring>

Reverb::Reverb() {
    // Comb filter delay lengths (prime-ish numbers for nice diffusion)
    int combDelays[] = { 1116, 1188, 1277, 1356 };
    for (int i = 0; i < 4; ++i) {
        comb_[i].buffer.resize(combDelays[i], 0.0f);
    }
    // Allpass filter delay lengths
    int allpassDelays[] = { 556, 441 };
    for (int i = 0; i < 2; ++i) {
        allpass_[i].buffer.resize(allpassDelays[i], 0.0f);
    }
    updateFilters();
}

void Reverb::updateFilters() {
    float fb = 0.6f + roomSize_ * 0.3f;
    for (int i = 0; i < 4; ++i) comb_[i].feedback = fb;
    float apFb = 0.5f;
    for (int i = 0; i < 2; ++i) allpass_[i].feedback = apFb;
}

void Reverb::process(const float* input, float* output, int32_t numFrames, int32_t numChannels) {
    for (int i = 0; i < numFrames; ++i) {
        for (int ch = 0; ch < numChannels; ++ch) {
            int idx = i * numChannels + ch;
            float dry = input[idx];

            // Comb filters
            float wet = 0;
            for (int c = 0; c < 4; ++c) {
                auto& comb = comb_[c];
                float out = comb.buffer[comb.pos];
                comb.buffer[comb.pos] = dry + out * comb.feedback;
                wet += out;
                comb.pos = (comb.pos + 1) % comb.buffer.size();
            }
            wet *= 0.25f;

            // Allpass filters
            for (int a = 0; a < 2; ++a) {
                auto& ap = allpass_[a];
                float bufOut = ap.buffer[ap.pos];
                ap.buffer[ap.pos] = wet + bufOut * ap.feedback;
                wet = bufOut - wet * ap.feedback;
                ap.pos = (ap.pos + 1) % ap.buffer.size();
            }

            output[idx] = dry * (1.0f - mix_) + wet * mix_;
        }
    }
}

void Reverb::setParameter(int id, float value) {
    value = std::clamp(value, 0.0f, 1.0f);
    switch (id) {
        case ROOM_SIZE: roomSize_ = value; updateFilters(); break;
        case MIX:       mix_ = value; break;
    }
}

float Reverb::getParameter(int id) {
    switch (id) {
        case ROOM_SIZE: return roomSize_;
        case MIX: return mix_;
    }
    return 0;
}

void Reverb::reset() {
    for (int i = 0; i < 4; ++i)
        std::fill(comb_[i].buffer.begin(), comb_[i].buffer.end(), 0.0f);
    for (int i = 0; i < 2; ++i)
        std::fill(allpass_[i].buffer.begin(), allpass_[i].buffer.end(), 0.0f);
}
