#ifndef GUITARIX_TRANSCRIBER_H
#define GUITARIX_TRANSCRIBER_H

#include <cstdint>
#include <vector>
#include <cmath>
#include <cstring>
#include "tablature.h"

class Transcriber {
public:
    Transcriber();
    ~Transcriber() = default;

    // Process entire audio buffer and generate tab
    bool transcribe(const float* audio, int32_t numSamples, int32_t sampleRate);
    bool transcribeSeparated(const float* guitarAudio, int32_t numSamples, int32_t sampleRate);

    // Get results
    const TabTrack& getTabTrack() const { return track_; }
    bool hasResult() const { return hasResult_; }

    // Progress
    float getProgress() const { return progress_; }

    // Source separation (placeholder for ML model)
    bool hasSeparationModel() const { return false; }
    bool loadSeparationModel(const char* modelPath);

private:
    static constexpr int YIN_WINDOW = 2048;
    static constexpr int HOP_SIZE = 512;
    static constexpr float YIN_THRESHOLD = 0.15f;
    static constexpr float MIN_FREQ = 60.0f;  // low B ~ 30Hz, but guitar ~ 82Hz
    static constexpr float MAX_FREQ = 1200.0f;

    TabTrack track_;
    bool hasResult_ = false;
    float progress_ = 0.0f;

    float yinPitch(const float* signal, int n);
    void updateTrack(const float* audio, int32_t numSamples, int32_t sampleRate);
};

#endif
