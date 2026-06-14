#ifndef GUITARIX_LOOPER_H
#define GUITARIX_LOOPER_H

#include "effect.h"
#include <vector>
#include <cstdint>
#include <mutex>

class Looper : public Effect {
public:
    enum { PARAM_MODE = 0, PARAM_COUNT = 1 };
    enum Mode { MODE_STOPPED = 0, MODE_RECORDING = 1, MODE_PLAYING = 2, MODE_OVERDUB = 3 };

    static constexpr int MAX_LOOP_SAMPLES = 60 * 48000; // 60 seconds at 48kHz

    Looper();
    ~Looper() = default;

    void process(const float* input, float* output, int32_t numFrames, int32_t numChannels) override;
    void setParameter(int id, float value) override;
    float getParameter(int id) override;
    void reset() override;
    int getParameterCount() const override { return PARAM_COUNT; }

    int getMode() const { return mode_; }
    void setMode(int mode);
    void toggleRecord();
    void undoOverdub();
    void clearLoop();
    int getLoopLength() const { return loopLength_; }
    float getLoopDuration() const { return loopLength_ / 48000.0f; }
    bool isRecording() const { return mode_ == MODE_RECORDING; }
    bool isPlaying() const { return mode_ == MODE_PLAYING || mode_ == MODE_OVERDUB; }

private:
    int mode_;
    std::vector<float> loopBuffer_;
    int loopLength_;
    int writePos_;
    int readPos_;
    int sampleRate_;
    mutable std::mutex mutex_;
    std::vector<float> overdubBuffer_;
};

#endif
