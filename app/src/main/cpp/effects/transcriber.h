#ifndef KRANK_TRANSCRIBER_H
#define KRANK_TRANSCRIBER_H

#include <cstdint>
#include <vector>
#include <cmath>
#include <cstring>
#include <algorithm>
#include "tablature.h"

class Transcriber {
public:
    Transcriber();
    ~Transcriber() = default;

    bool transcribe(const float* audio, int32_t numSamples, int32_t sampleRate);
    bool transcribeSeparated(const float* guitarAudio, int32_t numSamples, int32_t sampleRate);

    const TabTrack& getTabTrack() const { return track_; }
    bool hasResult() const { return hasResult_; }
    float getProgress() const { return progress_; }

    bool hasSeparationModel() const { return false; }
    bool loadSeparationModel(const char* modelPath);

private:
    static constexpr int YIN_WINDOW = 1024;
    static constexpr int HOP_SIZE = 256;
    static constexpr float YIN_THRESHOLD = 0.20f;
    static constexpr float MIN_FREQ = 75.0f;
    static constexpr float MAX_FREQ = 1000.0f;

    TabTrack track_;
    bool hasResult_ = false;
    float progress_ = 0.0f;

    float yinPitch(const float* signal, int n);
    void updateTrack(const float* audio, int32_t numSamples, int32_t sampleRate);
};

#endif
