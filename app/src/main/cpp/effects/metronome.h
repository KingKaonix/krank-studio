#ifndef KRANK_METRONOME_H
#define KRANK_METRONOME_H

#include "effect.h"
#include <cmath>
#include <cstdint>

class Metronome : public Effect {
public:
    enum { PARAM_BPM = 0, PARAM_VOLUME = 1, PARAM_COUNT = 2 };

    Metronome();
    void process(const float* input, float* output, int32_t numFrames, int32_t numChannels) override;
    void setParameter(int id, float value) override;
    float getParameter(int id) override;
    void reset() override;
    int getParameterCount() const override { return PARAM_COUNT; }

    bool isEnabled() const { return enabled_; }
    void setEnabled(bool e) { enabled_ = e; reset(); }
    void tapTempo();
    float getBpm() const { return bpm_; }

private:
    bool enabled_;
    float bpm_;
    float volume_;
    int sampleRate_;
    int samplesPerBeat_;
    int phase_;
    float clickPhase_; // 0-1 where the click occurs (e.g., 0 = downbeat)
    float lastTapTime_;
    float tapHistory_[4];
    int tapCount_;

    int getSamplesPerBeat() const;
};

#endif
