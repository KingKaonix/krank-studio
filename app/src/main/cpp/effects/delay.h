#ifndef GUITARIX_DELAY_H
#define GUITARIX_DELAY_H

#include "effect.h"
#include <vector>

class Delay : public Effect {
public:
    enum Params { MIX = 0, FEEDBACK, DELAY_MS };

    Delay();
    void process(const float* input, float* output, int32_t numFrames, int32_t numChannels) override;
    void setParameter(int id, float value) override;
    float getParameter(int id) override;
    void reset() override;

private:
    float mix_ = 0.3f;
    float feedback_ = 0.3f;
    float delayMs_ = 400.0f;
    int sampleRate_ = 48000;

    std::vector<float> buffer_;
    int writePos_ = 0;
    int delaySamples_ = 0;

    void updateDelaySamples();
};

#endif