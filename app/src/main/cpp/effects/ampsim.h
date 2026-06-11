#ifndef GUITARIX_AMPSIM_H
#define GUITARIX_AMPSIM_H

#include "effect.h"

class AmpSim : public Effect {
public:
    enum Params { GAIN = 0, TONE, MASTER };

    AmpSim();
    void process(const float* input, float* output, int32_t numFrames, int32_t numChannels) override;
    void setParameter(int id, float value) override;
    float getParameter(int id) override;
    void reset() override;

private:
    float gain_ = 0.5f;
    float tone_ = 0.5f;
    float master_ = 0.7f;

    // Power-amp simulation: asymmetric clipping
    float lastOutput = 0;
};

#endif