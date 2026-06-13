#include "tuner.h"
#include <cmath>
#include <algorithm>
#include <cstring>

// ── Note names ──
static const char* NOTE_NAMES[] = {
    "C","C#","D","D#","E","F","F#","G","G#","A","A#","B"
};

// ── Tuning base frequencies (E2, A2, D3, G3, B3, E4) ──
static constexpr float BASE_TUNING[6] = {82.41f, 110.0f, 146.83f, 196.0f, 246.94f, 329.63f};

Tuner::Tuner()
    : frequency_(0.0f), cents_(0.0f), referenceFreq_(440.0f), smoothedFreq_(0.0f), smoothingFactor_(0.9f),
      noteIndex_(-1), octave_(0), currentTuning_(TUNING_STANDARD), noteDetected_(false) {
    for (int i = 0; i < NUM_STRINGS; ++i) stringTunings_[i] = BASE_TUNING[i];
    reset();
}

Tuner::~Tuner() = default;

float Tuner::process(const float* input, int32_t numFrames, int32_t numChannels) {
    if (!input || numFrames <= 0) return 0.0f;

    // Simplified: take first channel, assume mono guitar input
    const float* channel = input;
    if (numChannels > 1) {
        // Average channels if stereo
        float sum = 0.0f;
        for (int i = 0; i < numFrames; ++i) sum += input[i];
        channel = &input[0];  // Use first channel for now (simplified)
    }

    // YIN algorithm - find fundamental frequency
    frequency_ = yinPitchDetection(channel, numFrames);

    // Apply smoothing
    smoothedFreq_ = smoothedFreq_ * smoothingFactor_ + frequency_ * (1.0f - smoothingFactor_);
    if (smoothedFreq_ < 20.0f) smoothedFreq_ = 0.0f;  // Below audible range

    // Convert to note
    if (smoothedFreq_ > 0.0f) {
        freqToNote(smoothedFreq_);
        noteDetected_ = (noteIndex_ >= 0);
    } else {
        noteIndex_ = -1;
        noteDetected_ = false;
    }

    return smoothedFreq_;
}

// Simple YIN implementation (frequency in Hz)
float Tuner::yinPitchDetection(const float* signal, int32_t numFrames) {
    int32_t windowSize = std::min(numFrames, YIN_MAX_FRAMES);
    float* autocorr = new float[windowSize / 2];
    memset(autocorr, 0, (windowSize / 2) * sizeof(float));

    // Compute auto-correlation
    for (int tau = 0; tau < windowSize / 2; ++tau) {
        float sum = 0.0f;
        for (int i = 0; i < windowSize - tau; ++i) {
            float diff = signal[i] - signal[i + tau];
            sum += diff * diff;
        }
        autocorr[tau] = sum;
    }

    // Find first minimum where autocorr[tau] < threshold * autocorr[1]
    float threshold = YIN_THRESHOLD;
    int candidate = 1;
    while (candidate < windowSize / 4) {
        if (autocorr[candidate] < threshold * autocorr[1]) break;
        candidate++;
    }

    // Parabolic interpolation around the minimum
    int tauPrime = candidate;
    if (tauPrime > 1 && tauPrime < windowSize / 4 - 1) {
        float a = autocorr[tauPrime - 1];
        float b = autocorr[tauPrime];
        float c = autocorr[tauPrime + 1];
        float parabolic = tauPrime + 0.5f * (a - c) / ((a - 2*b) + c);
        if (parabolic > 0) {
            float freq = 48000.0f / parabolic;  // 48kHz sample rate
            delete[] autocorr;
            return freq;
        }
    }

    delete[] autocorr;
    return 0.0f;
}

// Convert frequency to note name, index, octave
void Tuner::freqToNote(float freq) {
    if (freq <= 0.0f) {
        noteIndex_ = -1;
        octave_ = 0;
        cents_ = 0.0f;
        return;
    }

    // Calculate semitone offset from A4 reference
    float semitonesFromA4 = 12.0f * log2(freq / referenceFreq_);
    int noteIndex = static_cast<int>(round(semitonesFromA4));
    int octave = 4 + (noteIndex / 12);
    noteIndex = (noteIndex % 12 + 12) % 12;

    // Convert back to frequency for current tuning
    float baseNoteFreq = referenceFreq_ * powf(2.0f, static_cast<float>(noteIndex - 9) / 12.0f);

    // Calculate cents deviation
    cents_ = 100.0f * logf(freq / baseNoteFreq);

    noteIndex_ = noteIndex;
    octave_ = octave;
}

void Tuner::setReferenceFreq(float ref) { referenceFreq_ = ref; }
float Tuner::getReferenceFreq() const { return referenceFreq_; }

void Tuner::setTuningPreset(int preset) {
    currentTuning_ = std::clamp(preset, 0, TUNING_COUNT - 1);
    // Set string tunings based on preset (simplified)
    switch (currentTuning_) {
        case TUNING_DROP_D:  for (int i = 0; i < 6; ++i) stringTunings_[i] = BASE_TUNING[i] * powf(2.0f, TUNING_OFFSETS[i] / 12.0f); break;
        case TUNING_DROP_C:  for (int i = 0; i < 6; ++i) stringTunings_[i] = BASE_TUNING[i] * powf(2.0f, TUNING_OFFSETS[i] / 12.0f); break;
        case TUNING_OPEN_D:  for (int i = 0; i < 6; ++i) stringTunings_[i] = BASE_TUNING[i] * powf(2.0f, TUNING_OFFSETS[i] / 12.0f); break;
        case TUNING_OPEN_G:  for (int i = 0; i < 6; ++i) stringTunings_[i] = BASE_TUNING[i] * powf(2.0f, TUNING_OFFSETS[i] / 12.0f); break;
        case TUNING_DADGAD:  for (int i = 0; i < 6; ++i) stringTunings_[i] = BASE_TUNING[i] * powf(2.0f, TUNING_OFFSETS[i] / 12.0f); break;
        case TUNING_HALF_STEP_DOWN:  for (int i = 0; i < 6; ++i) stringTunings_[i] = BASE_TUNING[i] * powf(2.0f, TUNING_OFFSETS[i] / 12.0f); break;
        case TUNING_FULL_STEP_DOWN:   for (int i = 0; i < 6; ++i) stringTunings_[i] = BASE_TUNING[i] * powf(2.0f, TUNING_OFFSETS[i] / 12.0f); break;
        case TUNING_DROP_B:  for (int i = 0; i < 6; ++i) stringTunings_[i] = BASE_TUNING[i] * powf(2.0f, TUNING_OFFSETS[i] / 12.0f); break;
        case TUNING_OPEN_A:  for (int i = 0; i < 6; ++i) stringTunings_[i] = BASE_TUNING[i] * powf(2.0f, TUNING_OFFSETS[i] / 12.0f); break;
        default:             for (int i = 0; i < 6; ++i) stringTunings_[i] = BASE_TUNING[i]; break;
    }
}

int Tuner::getCurrentTuningPreset() const { return currentTuning_; }
const char* Tuner::getTuningName() const {
    static const char* names[] = {"Standard", "Drop D", "Drop C", "Open D", "Open G", "Open E", "DADGAD",
                                   "Half-Step Down", "Full-Step Down", "Drop B", "Open A", "Custom"};
    return names[currentTuning_];
}

void Tuner::getStringTunings(float* out) const {
    for (int i = 0; i < NUM_STRINGS; ++i) out[i] = stringTunings_[i];
}

int Tuner::getClosestString() const {
    if (smoothedFreq_ <= 0.0f) return -1;
    float bestDist = 999999.0f;
    int bestIdx = -1;
    for (int i = 0; i < NUM_STRINGS; ++i) {
        float dist = fabsf(smoothedFreq_ - stringTunings_[i]);
        if (dist < bestDist) {
            bestDist = dist;
            bestIdx = i;
        }
    }
    return bestIdx;
}

float Tuner::getStringCents() const {
    int stringIdx = getClosestString();
    if (stringIdx < 0) return 0.0f;
    float targetFreq = stringTunings_[stringIdx];
    if (targetFreq <= 0.0f) return 0.0f;
    return 100.0f * logf(smoothedFreq_ / targetFreq);
}

void Tuner::reset() {
    frequency_ = 0.0f;
    smoothedFreq_ = 0.0f;
    noteIndex_ = -1;
    noteDetected_ = false;
    cents_ = 0.0f;
}

const char* Tuner::noteName(int index) {
    if (index < 0 || index >= NOTE_NAMES_COUNT) return "?";
    return NOTE_NAMES[index];
}
