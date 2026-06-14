#include "looper.h"
#include <algorithm>
#include <cstring>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "Looper", __VA_ARGS__)

Looper::Looper()
    : mode_(MODE_STOPPED), loopLength_(0), writePos_(0), readPos_(0), sampleRate_(48000) {
    loopBuffer_.resize(MAX_LOOP_SAMPLES, 0.0f);
    overdubBuffer_.resize(MAX_LOOP_SAMPLES, 0.0f);
}

void Looper::process(const float* input, float* output, int32_t numFrames, int32_t numChannels) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (mode_ == MODE_STOPPED || numChannels == 0) return;

    for (int i = 0; i < numFrames; ++i) {
        float inSample = 0.0f;
        for (int c = 0; c < numChannels; ++c) {
            inSample += input[i * numChannels + c];
        }
        inSample /= numChannels;

        float outSample = 0.0f;

        if (mode_ == MODE_RECORDING) {
            // Record input to loop
            loopBuffer_[writePos_] = inSample;
            loopLength_ = std::max(loopLength_, writePos_ + 1);
            outSample = inSample; // Thru during recording
            writePos_++;
            if (writePos_ >= MAX_LOOP_SAMPLES) {
                // Loop memory full, switch to playing
                mode_ = MODE_PLAYING;
                readPos_ = 0;
                loopLength_ = MAX_LOOP_SAMPLES;
            }
        } else if (mode_ == MODE_PLAYING) {
            // Play back loop
            if (loopLength_ > 0) {
                outSample = loopBuffer_[readPos_ % loopLength_];
                readPos_++;
            }
        } else if (mode_ == MODE_OVERDUB) {
            // Play back loop + mix input
            if (loopLength_ > 0) {
                float loopSample = loopBuffer_[readPos_ % loopLength_];
                // Mix new input on top (overdub)
                loopBuffer_[writePos_ % loopLength_] = loopSample + inSample;
                outSample = loopSample + inSample;
                readPos_++;
                writePos_++;
            }
        }

        for (int c = 0; c < numChannels; ++c) {
            output[i * numChannels + c] += outSample;
        }
    }
}

void Looper::setMode(int mode) {
    std::lock_guard<std::mutex> lock(mutex_);
    mode_ = mode;
    if (mode == MODE_RECORDING) {
        // Reset write position for new recording
        writePos_ = 0;
        readPos_ = 0;
        loopLength_ = 0;
        std::fill(loopBuffer_.begin(), loopBuffer_.end(), 0.0f);
    } else if (mode == MODE_PLAYING) {
        readPos_ = 0;
    }
}

void Looper::toggleRecord() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (mode_ == MODE_STOPPED || mode_ == MODE_PLAYING) {
        // Start recording (or overdub if playing)
        if (mode_ == MODE_PLAYING && loopLength_ > 0) {
            mode_ = MODE_OVERDUB;
            writePos_ = readPos_; // Continue from current position
        } else {
            mode_ = MODE_RECORDING;
            writePos_ = 0;
            readPos_ = 0;
            loopLength_ = 0;
            std::fill(loopBuffer_.begin(), loopBuffer_.end(), 0.0f);
        }
    } else if (mode_ == MODE_RECORDING || mode_ == MODE_OVERDUB) {
        // Stop and switch to playback
        mode_ = MODE_PLAYING;
        readPos_ = 0;
    }
}

void Looper::undoOverdub() {
    std::lock_guard<std::mutex> lock(mutex_);
    // Reload from the original loop buffer (before overdub)
    // Simple approach: just continue playing what's there
    // A proper undo would store previous state
    LOGI("Undo overdub");
}

void Looper::clearLoop() {
    std::lock_guard<std::mutex> lock(mutex_);
    std::fill(loopBuffer_.begin(), loopBuffer_.end(), 0.0f);
    loopLength_ = 0;
    writePos_ = 0;
    readPos_ = 0;
    mode_ = MODE_STOPPED;
}

void Looper::setParameter(int id, float value) {
    if (id == PARAM_MODE) {
        setMode((int)(value * 3.0f + 0.5f));
    }
}

float Looper::getParameter(int id) {
    if (id == PARAM_MODE) return mode_ / 3.0f;
    return 0.0f;
}

void Looper::reset() {
    clearLoop();
}
