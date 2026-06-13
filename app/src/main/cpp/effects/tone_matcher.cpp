#include "tone_matcher.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <vector>

static float hammingWindow(int n, int N) {
    return 0.54f - 0.46f * cosf(2.0f * M_PI * n / N);
}

static void computeFFT(float* real, float* imag, int n) {
    // Simplified FFT - for production use a proper FFT library
    // This is a placeholder implementation
    // In a real app, use a proper FFT implementation

    // For now, use a basic DFT for demonstration
    for (int k = 0; k < n / 2; ++k) {
        float sumReal = 0.0f;
        float sumImag = 0.0f;
        for (int t = 0; t < n; ++t) {
            float angle = -2.0f * M_PI * t * k / n;
            sumReal += real[t] * cosf(angle);
            sumImag += real[t] * sinf(angle);
        }
        real[k] = sumReal;
        imag[k] = sumImag;
    }
}

ToneMatcher::ToneMatcher()
    : hasSample_(false), hasProfile_(false), sampleData_(), real_(FFT_SIZE), imag_(FFT_SIZE),
      window_(FFT_SIZE), spectrum_(NUM_BINS), features_(), profile_() {
    for (int i = 0; i < FFT_SIZE; ++i) {
        window_[i] = hammingWindow(i, FFT_SIZE);
    }
    for (int i = 0; i < NUM_BINS; ++i) spectrum_[i] = 0.0f;
    clearSample();
}

ToneMatcher::~ToneMatcher() = default;

void ToneMatcher::loadSample(const float* data, int32_t n) {
    clearSample();
    hasSample_ = true;

    // Store sample data (resample to match FFT_SIZE if needed)
    int samplesToStore = std::min(n, (int)sampleData_.size());
    if (samplesToStore > 0) {
        // Take first N samples
        sampleData_.resize(samplesToStore);
        for (int i = 0; i < samplesToStore; ++i) sampleData_[i] = data[i];

        // Apply window function for FFT
        for (int i = 0; i < samplesToStore; ++i) {
            if (i < FFT_SIZE) real_[i] = sampleData_[i] * window_[i];
            else break;
        }

        // Zero-pad if needed
        if (samplesToStore < FFT_SIZE) {
            for (int i = samplesToStore; i < FFT_SIZE; ++i) real_[i] = 0.0f;
        }

        analyze();
    }
}

void ToneMatcher::clearSample() {
    hasSample_ = false;
    hasProfile_ = false;
    sampleData_.clear();
    memset(real_, 0, FFT_SIZE * sizeof(float));
    memset(imag_, 0, FFT_SIZE * sizeof(float));
    memset(spectrum_, 0, NUM_BINS * sizeof(float));
    memset(&features_, 0, sizeof(features_));
}

void ToneMatcher::analyze() {
    if (!hasSample_) return;

    // Compute FFT
    computeFFT(real_, imag_, FFT_SIZE);

    // Compute spectrum magnitude
    for (int i = 0; i < NUM_BINS; ++i) {
        float re = real_[i];
        float im = imag_[i];
        spectrum_[i] = sqrtf(re * re + im * im);
    }

    // Extract features
    extractSpectralFeatures();
    extractHarmonicFeatures();
    extractDynamicFeatures();

    // Build profile
    buildProfile();
    hasProfile_ = true;
}

void ToneMatcher::extractSpectralFeatures() {
    // Compute spectral centroid
    float weightedSum = 0.0f;
    float totalEnergy = 0.0f;
    for (int i = 1; i < NUM_BINS; ++i) {
        float binFreq = 44100.0f * i / (2 * FFT_SIZE);  // Approximate frequency
        float energy = spectrum_[i];
        weightedSum += binFreq * energy;
        totalEnergy += energy;
    }
    features_.centroid = (totalEnergy > 0.0f) ? (weightedSum / totalEnergy) / 44100.0f : 0.5f;

    // Spectral rolloff (frequency where 85% of energy is contained)
    float cumulative = 0.0f;
    float threshold = 0.85f * totalEnergy;
    for (int i = 0; i < NUM_BINS; ++i) {
        cumulative += spectrum_[i];
        if (cumulative >= threshold) {
            features_.rolloff = static_cast<float>(i) / NUM_BINS;
            break;
        }
    }

    // Band energies
    float bandSize = NUM_BINS / 7.0f;
    for (int b = 0; b < 7; ++b) {
        float start = b * bandSize;
        float end = (b + 1) * bandSize;
        float bandEnergy = 0.0f;
        for (int i = static_cast<int>(start); i < std::min(static_cast<int>(end), NUM_BINS); ++i) {
            bandEnergy += spectrum_[i];
        }
        features_.bandEnergy[b] = bandEnergy / totalEnergy;
    }
}

void ToneMatcher::extractHarmonicFeatures() {
    // Simple harmonic ratio calculation
    float fundamentalBand = spectrum_[NUM_BINS / 8];  // Approx fundamental
    float harmonicSum = fundamentalBand;

    for (int h = 2; h < 5; ++h) {  // Check harmonics 2-4
        int harmonicBin = (NUM_BINS / 8) * h;
        if (harmonicBin < NUM_BINS) harmonicSum += spectrum_[harmonicBin];
    }

    features_.thd = (harmonicSum > 0.0f) ? fundamentalBand / harmonicSum : 0.5f;

    // Odd/even ratio
    float oddSum = 0.0f, evenSum = 0.0f;
    for (int i = 0; i < NUM_BINS; ++i) {
        if (i % 2 == 0) evenSum += spectrum_[i];
        else oddSum += spectrum_[i];
    }
    features_.oddEvenRatio = (evenSum + oddSum > 0.0f) ? evenSum / (evenSum + oddSum) : 0.5f;
}

void ToneMatcher::extractDynamicFeatures() {
    // RMS level
    float rms = 0.0f;
    for (int i = 0; i < sampleData_.size(); ++i) {
        rms += sampleData_[i] * sampleData_[i];
    }
    rms = sqrtf(rms / sampleData_.size());
    features_.rmsLevel = std::min(rms * 2.0f, 1.0f);  // Normalize

    // Crest factor (peak / RMS)
    float peak = 0.0f;
    for (int i = 0; i < sampleData_.size(); ++i) {
        peak = std::max(peak, fabsf(sampleData_[i]));
    }
    features_.crestFactor = (rms > 0.0f) ? peak / rms : 1.0f;

    // Envelope variation (simple sliding window variance)
    float envelopeSum = 0.0f;
    for (int i = 0; i < sampleData_.size(); ++i) envelopeSum += fabsf(sampleData_[i]);
    float avgEnvelope = envelopeSum / sampleData_.size();
    float varianceSum = 0.0f;
    for (int i = 0; i < sampleData_.size(); ++i) {
        float dev = fabsf(sampleData_[i]) - avgEnvelope;
        varianceSum += dev * dev;
    }
    features_.envelopeVariation = (avgEnvelope > 0.0f) ? sqrtf(varianceSum / sampleData_.size()) / avgEnvelope : 0.0f;
}

void ToneMatcher::buildProfile() {
    // Map spectral features to effect parameters (simplified)
    // This is where we create recommendations based on the analyzed tone

    // Bass: influenced by low frequency content
    profile_.bassEnergy = features_.bandEnergy[0];

    // Mids: middle frequency content
    profile_.midEnergy = (features_.bandEnergy[2] + features_.bandEnergy[3]) / 2.0f;

    // Treble: high frequency content
    profile_.trebleEnergy = features_.bandEnergy[6];

    // Drive amount: influenced by harmonic content and crest factor
    profile_.driveAmount = features_.thd * 0.5f;

    // Sustain: influenced by envelope and RMS
    profile_.sustain = features_.rmsLevel;

    // Compression: influenced by crest factor
    profile_.compression = (1.0f - features_.crestFactor) * 0.5f;

    // Reverb decay: influenced by spectral rolloff
    profile_.reverbDecay = features_.rolloff;

    // Delay amount: influenced by odd/even ratio
    profile_.delayAmount = (1.0f - features_.oddEvenRatio) * 0.5f;

    // Brightness: influenced by centroid
    profile_.brightness = features_.centroid;

    // Warmth: opposite of brightness
    profile_.warmth = 1.0f - features_.centroid;

    // Gain: influenced by RMS level
    profile_.gain = features_.rmsLevel * 0.8f;
}

float ToneMatcher::getRecommendedDistortionDrive() const { return hasProfile_ ? profile_.driveAmount : 0.5f; }
float ToneMatcher::getRecommendedDistortionTone() const { return hasProfile_ ? (0.5f + profile_.driveAmount * 0.5f) : 0.5f; }
float ToneMatcher::getRecommendedDistortionLevel() const { return hasProfile_ ? profile_.gain : 0.5f; }

float ToneMatcher::getRecommendedAmpSimGain() const { return hasProfile_ ? profile_.gain * 0.7f : 0.5f; }
float ToneMatcher::getRecommendedAmpSimTone() const { return hasProfile_ ? profile_.brightness * 2.0f - 1.0f : 0.5f; }
float ToneMatcher::getRecommendedAmpSimMaster() const { return hasProfile_ ? profile_.sustain : 0.5f; }

float ToneMatcher::getRecommendedEqBass() const { return hasProfile_ ? profile_.bassEnergy : 0.5f; }
float ToneMatcher::getRecommendedEqMid() const { return hasProfile_ ? profile_.midEnergy : 0.5f; }
float ToneMatcher::getRecommendedEqTreble() const { return hasProfile_ ? profile_.trebleEnergy : 0.5f; }

float ToneMatcher::getRecommendedChorusRate() const { return hasProfile_ ? profile_.driveAmount * 0.5f + 0.2f : 0.5f; }
float ToneMatcher::getRecommendedChorusDepth() const { return hasProfile_ ? profile_.sustain * 0.3f : 0.3f; }
float ToneMatcher::getRecommendedChorusMix() const { return hasProfile_ ? profile_.delayAmount : 0.3f; }

float ToneMatcher::getRecommendedDelayMix() const { return hasProfile_ ? profile_.delayAmount : 0.3f; }
float ToneMatcher::getRecommendedDelayFeedback() const { return hasProfile_ ? profile_.compression * 0.5f : 0.3f; }
float ToneMatcher::getRecommendedDelayTime() const { return hasProfile_ ? profile_.reverbDecay * 1000.0f : 400.0f; }

float ToneMatcher::getRecommendedReverbSize() const { return hasProfile_ ? profile_.reverbDecay : 0.3f; }
float ToneMatcher::getRecommendedReverbMix() const { return hasProfile_ ? profile_.warmth * 0.5f : 0.2f; }
