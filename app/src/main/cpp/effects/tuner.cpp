#include "tuner.h"
#include <cmath>
#include <algorithm>
#include <cstring>

static const char* NOTE_NAMES[] = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
static constexpr float BASE_TUNING[6] = {82.41f, 110.0f, 146.83f, 196.0f, 246.94f, 329.63f};

// Portable log2f for NDK compatibility
static inline float log2f_p(float x) { return logf(x) / logf(2.0f); }
static inline float roundf_p(float x) { return floorf(x + 0.5f); }

static constexpr float TUNING_OFFSETS[12][6] = {
    { 0, 0, 0, 0, 0, 0 }, { -2, 0, 0, 0, 0, 0 }, { -4, -2, 0, 0, 0, 0 },
    { 0, 0, 0, 0, 0, -2 }, { -2, -2, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0 },
    { -2, -2, 0, 0, 0, 0 }, { -1, -1, -1, -1, -1, -1 }, { -2, -2, -2, -2, -2, -2 },
    { -5, -2, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0 },
};

Tuner::Tuner(): frequency_(0.0f), cents_(0.0f), referenceFreq_(440.0f), smoothedFreq_(0.0f),
    smoothingFactor_(0.9f), noteIndex_(-1), octave_(0), currentTuning_(TUNING_STANDARD), noteDetected_(false) {
    for (int i = 0; i < NUM_STRINGS; ++i) stringTunings_[i] = BASE_TUNING[i];
    reset();
}

Tuner::~Tuner() = default;

float Tuner::process(const float* input, int32_t numFrames) {
    if (!input || numFrames <= 0) return 0.0f;
    frequency_ = yinPitchDetection(input, numFrames);
    smoothedFreq_ = smoothedFreq_ * smoothingFactor_ + frequency_ * (1.0f - smoothingFactor_);
    if (smoothedFreq_ < 20.0f) smoothedFreq_ = 0.0f;
    if (smoothedFreq_ > 0.0f) { freqToNote(smoothedFreq_); noteDetected_ = true; }
    else { noteIndex_ = -1; noteDetected_ = false; }
    return smoothedFreq_;
}

float Tuner::yinPitchDetection(const float* signal, int32_t numFrames) {
    int32_t windowSize = (numFrames > YIN_MAX_FRAMES) ? YIN_MAX_FRAMES : numFrames;
    if (windowSize < 4) return 0.0f;
    float diff1 = 0.0f;
    for (int i = 0; i < windowSize - 1; ++i) { float d = signal[i] - signal[i + 1]; diff1 += d * d; }
    if (diff1 < 1e-10f) return 0.0f;
    int half = windowSize / 2;
    float* autocorr = new float[half];
    for (int i = 0; i < half; ++i) autocorr[i] = 0.0f;
    for (int tau = 0; tau < half; ++tau)
        for (int i = 0; i < windowSize - tau; ++i)
            { float d = signal[i] - signal[i + tau]; autocorr[tau] += d * d; }
    int candidate = 1;
    while (candidate < half / 2) {
        if (autocorr[candidate] < YIN_THRESHOLD * autocorr[1]) break;
        candidate++;
    }
    if (candidate > 1 && candidate < half / 2 - 1) {
        float a = autocorr[candidate-1], b = autocorr[candidate], c = autocorr[candidate+1];
        float denom = a - 2.0f*b + c;
        if (fabsf(denom) > 1e-10f) {
            float tau = (float)candidate + 0.5f * (a - c) / denom;
            if (tau > 0.0f) { delete[] autocorr; return 48000.0f / tau; }
        }
    }
    delete[] autocorr; return 0.0f;
}

void Tuner::freqToNote(float freq) {
    if (freq <= 0.0f) { noteIndex_ = -1; octave_ = 0; cents_ = 0.0f; return; }
    float semitonesFromA4 = 12.0f * log2f_p(freq / referenceFreq_);
    int noteIdx = (int)roundf_p(semitonesFromA4);
    octave_ = 4 + (noteIdx / 12);
    noteIndex_ = ((noteIdx % 12) + 12) % 12;
    float baseNoteFreq = referenceFreq_ * powf(2.0f, (float)(noteIndex_ - 9) / 12.0f);
    cents_ = (baseNoteFreq > 0.0f) ? 100.0f * log2f_p(freq / baseNoteFreq) : 0.0f;
}

void Tuner::setReferenceFreq(float ref) { referenceFreq_ = ref; }

void Tuner::setTuningPreset(int preset) {
    currentTuning_ = (preset < 0) ? 0 : (preset >= TUNING_COUNT) ? TUNING_COUNT - 1 : preset;
    const float* offsets = TUNING_OFFSETS[currentTuning_];
    for (int i = 0; i < NUM_STRINGS; ++i)
        stringTunings_[i] = BASE_TUNING[i] * powf(2.0f, offsets[i] / 12.0f);
}

int Tuner::getCurrentTuningPreset() const { return currentTuning_; }
const char* Tuner::getTuningName() const {
    static const char* names[] = {"Standard", "Drop D", "Drop C", "Open D", "Open G", "Open E",
        "DADGAD", "Half-Step Down", "Full-Step Down", "Drop B", "Open A", "Custom"};
    return names[currentTuning_];
}
void Tuner::getStringTunings(float* out) const { for (int i = 0; i < NUM_STRINGS; ++i) out[i] = stringTunings_[i]; }

int Tuner::getClosestString() const {
    if (smoothedFreq_ <= 0.0f) return -1;
    float bestDist = 1e9f; int bestIdx = -1;
    for (int i = 0; i < NUM_STRINGS; ++i) {
        float dist = fabsf(smoothedFreq_ - stringTunings_[i]);
        if (dist < bestDist) { bestDist = dist; bestIdx = i; }
    }
    return bestIdx;
}

float Tuner::getStringCents() const {
    int idx = getClosestString();
    if (idx < 0 || stringTunings_[idx] <= 0.0f) return 0.0f;
    return 100.0f * log2f_p(smoothedFreq_ / stringTunings_[idx]);
}

void Tuner::reset() { frequency_ = 0.0f; smoothedFreq_ = 0.0f; noteIndex_ = -1; noteDetected_ = false; cents_ = 0.0f; }

const char* Tuner::noteName(int index) {
    if (index < 0 || index >= 12) return "?";
    return NOTE_NAMES[index];
}
