#ifndef KRANK_EQ_H
#define KRANK_EQ_H

#include "effect.h"

class EQ : public Effect {
public:
    enum Params { BASS = 0, MID, TREBLE };

    EQ();
    void process(const float* input, float* output, int32_t numFrames, int32_t numChannels) override;
    void setParameter(int id, float value) override;
    float getParameter(int id) override;
    void reset() override;

private:
    float bassGain_ = 0.5f;
    float midGain_ = 0.5f;
    float trebleGain_ = 0.5f;

    // Biquad states per channel
    struct BiquadState {
        float x1 = 0, x2 = 0, y1 = 0, y2 = 0;
    } low_, peak_, high_;

    float b0, b1, b2, a1, a2;  // coefficients (shared across all bands on init)
};

#endif