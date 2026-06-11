#ifndef GUITARIX_CHORUS_H
#define GUITARIX_CHORUS_H

#include "effect.h"
#include <vector>

class Chorus : public Effect {
public:
    enum Params { RATE = 0, DEPTH, MIX };

    Chorus();
    void process(const float* input, float* output, int32_t numFrames, int32_t numChannels) override;
    void setParameter(int id, float value) override;
    float getParameter(int id) override;
    void reset() override;

private:
    float rate_ = 0.5f;
    float depth_ = 0.3f;
    float mix_ = 0.3f;
    int sampleRate_ = 48000;
    float phase_ = 0;

    std::vector<float> buffer_;
    int writePos_ = 0;
};

#endif