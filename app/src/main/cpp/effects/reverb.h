#ifndef GUITARIX_REVERB_H
#define GUITARIX_REVERB_H

#include "effect.h"
#include <vector>

class Reverb : public Effect {
public:
    enum Params { ROOM_SIZE = 0, MIX };

    Reverb();
    void process(const float* input, float* output, int32_t numFrames, int32_t numChannels) override;
    void setParameter(int id, float value) override;
    float getParameter(int id) override;
    void reset() override;

private:
    float roomSize_ = 0.3f;
    float mix_ = 0.2f;
    int sampleRate_ = 48000;

    // Schroder: 4 comb filters + 2 allpass filters
    struct CombFilter {
        std::vector<float> buffer;
        int pos = 0;
        float feedback = 0;
    };
    struct AllpassFilter {
        std::vector<float> buffer;
        int pos = 0;
        float feedback = 0;
    };

    CombFilter comb_[4];
    AllpassFilter allpass_[2];

    void updateFilters();
};

#endif