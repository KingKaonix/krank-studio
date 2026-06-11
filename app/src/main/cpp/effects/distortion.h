#ifndef GUITARIX_DISTORTION_H
#define GUITARIX_DISTORTION_H

#include "effect.h"

class Distortion : public Effect {
public:
    enum Params { DRIVE = 0, TONE, LEVEL };

    Distortion();
    void process(const float* input, float* output, int32_t numFrames, int32_t numChannels) override;
    void setParameter(int id, float value) override;
    float getParameter(int id) override;
    void reset() override;

private:
    float drive_ = 0.5f;
    float tone_ = 0.5f;
    float level_ = 0.7f;
    float prevOutput_ = 0.0f;
};

#endif