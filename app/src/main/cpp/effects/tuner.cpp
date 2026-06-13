#include "tuner.h"
#include <cmath>
#include <algorithm>
#include <cstring>

static const char* NOTE_NAMES[] = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};

static constexpr float BASE_TUNING[6] = {82.41f, 110.0f, 146.83f, 196.0f, 246.94f, 329.63f};

// Semitone offsets from standard for each tuning preset
static constexpr float TUNING_OFFSETS[12][6] = {
    { 0, 0, 0, 0, 0, 0 },              // STANDARD
    { -2, 0, 0, 0, 0, 0 },              // DROP_D
    { -4, -2, 0, 0, 0, 0 },             // DROP_C
    { 0, 0, 0, 0, 0, -2 },              // OPEN_D
    { -2, -2, 0, 0, 0, 0 },             // OPEN_G
    { 0, 0, 0, 0, 0, 0 },               // OPEN_E
    { -2, -2, 0, 0, 0, 0 },             // DADGAD
    { -1, -1, -1, -1, -1, -1 },         // HALF_STEP_DOWN
    { -2, -2, -2, -2, -2, -2 },         // FULL_STEP_DOWN
    { -5, -2, 0, 0, 0, 0 },             // DROP_B
    { 0, 0, 0, 0, 0, 0 },               // OPEN_A
    { 0, 0, 0, 0, 0, 0 },               // CUSTOM
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
    int32_t windowSize = std::min(numFrames, YIN_MAX_FRAMES);
    float diff1 = 0.0f;
    for (int i = 0; i < windowSize - 1; ++i) {
        float d = signal[i] - signal[i + 1];
        diff1 += d * d;
    }
    if (diff1 < 0.00001f) return 0.0f;
    float* autocorr = new float[windowSize / 2];
    memset(autocorr, 0, (windowSize / 2) * sizeof(float));
    for (int tau = 0; tau < windowSize / 2; ++tau) {
        for (int i = 0; i < windowSize - tau; ++i) {
            float diff = signal[i] - signal[i + tau];
            autocorr[tau] += diff * diff;
        }
    }
    int candidate = 1;
    while (candidate < windowSize / 4) {
        if (autocorr[candidate] < YIN_THRESHOLD * autocorr[1]) break;
        candidate++;
    }
    int tauPrime = candidate;
    if (tauPrime > 1 && tauPrime < windowSize / 4 - 1) {
        float a = autocorr[tauPrime - 1], b = autocorr[tauPrime], c = autocorr[tauPrime + 1];
        float denom = a - 2*b + c;
        if (fabsf(denom) > 0.00001f) {
            float parabolic = tauPrime + 0.5f * (a - c) / denom;
            if (parabolic > 0) { delete[] autocorr; return 48000.0f / parabolic; }
        }
    }
    delete[] autocorr;
    return 0.0f;
}

void Tuner::freqToNote(float freq) {
    if (freq <= 0.0f) { noteIndex_ = -1; octave_ = 0; cents_ = 0.0f; return; }
    float semitonesFromA4 = 12.0f * log2f(freq / referenceFreq_);
    int noteIdx = (int)roundf(semitonesFromA4);
    octave_ = 4 + (noteIdx / 12);
    noteIndex_ = ((noteIdx % 12) + 12) % 12;
    float baseNoteFreq = referenceFreq_ * powf(2.0f, (float)(noteIndex_ - 9) / 12.0f);
    if (baseNoteFreq > 0.0f) cents_ = 100.0f * log2f(freq / baseNoteFreq);
    else cents_ = 0.0f;
    if (noteIndex_ < 0) { noteIndex_ = 0; cents_ = 0.0f; }
}

void Tuner::setReferenceFreq(float ref) { referenceFreq_ = ref; }

void Tuner::setTuningPreset(int preset) {
    currentTuning_ = std::clamp(preset, 0, TUNING_COUNT - 1);
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

void Tuner::getStringTunings(float* out) const {
    for (int i = 0; i < NUM_STRINGS; ++i) out[i] = stringTunings_[i];
}

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
    return 100.0f * log2f(smoothedFreq_ / stringTunings_[idx]);
}

void Tuner::reset() { frequency_ = 0.0f; smoothedFreq_ = 0.0f; noteIndex_ = -1; noteDetected_ = false; cents_ = 0.0f; }

const char* Tuner::noteName(int index) {
    if (index < 0 || index >= 12) return "?";
    return NOTE_NAMES[index];
}
