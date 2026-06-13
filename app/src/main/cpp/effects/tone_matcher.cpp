#include "tone_matcher.h"
#include <cmath>
#include <algorithm>
#include <cstring>
#include <vector>

#define _USE_MATH_DEFINES
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

static float hammingWindow(int n, int N) {
    return 0.54f - 0.46f * cosf(2.0f * (float)M_PI * n / N);
}

ToneMatcher::ToneMatcher()
    : hasSample_(false), hasProfile_(false), sampleData_(), real_(FFT_SIZE), imag_(FFT_SIZE),
      window_(FFT_SIZE), spectrum_(NUM_BINS), features_({0}), profile_() {
    for (int i = 0; i < FFT_SIZE; ++i) window_[i] = hammingWindow(i, FFT_SIZE);
    for (int i = 0; i < NUM_BINS; ++i) spectrum_[i] = 0.0f;
    clearSample();
}

ToneMatcher::~ToneMatcher() = default;

void ToneMatcher::loadSample(const float* data, int32_t n) {
    clearSample();
    hasSample_ = true;
    if (!data || n <= 0) return;

    int samplesToStore = std::min((int)n, FFT_SIZE);
    sampleData_.resize(samplesToStore);
    for (int i = 0; i < samplesToStore; ++i) sampleData_[i] = data[i];
    for (int i = 0; i < samplesToStore; ++i) real_[i] = sampleData_[i] * window_[i];
    for (int i = samplesToStore; i < FFT_SIZE; ++i) real_[i] = 0.0f;
    analyze();
}

void ToneMatcher::clearSample() {
    hasSample_ = false; hasProfile_ = false; sampleData_.clear();
    memset(real_, 0, FFT_SIZE * sizeof(float));
    memset(imag_, 0, FFT_SIZE * sizeof(float));
    memset(spectrum_, 0, NUM_BINS * sizeof(float));
    memset(&features_, 0, sizeof(features_));
}

static void simpleDFT(float* real, float* imag, int n) {
    for (int k = 0; k < n / 2; ++k) {
        float sumReal = 0, sumImag = 0;
        for (int t = 0; t < n; ++t) {
            float angle = -2.0f * (float)M_PI * t * k / n;
            sumReal += real[t] * cosf(angle);
            sumImag += real[t] * sinf(angle);
        }
        real[k] = sumReal; imag[k] = sumImag;
    }
}

void ToneMatcher::analyze() {
    if (!hasSample_) return;
    simpleDFT(real_, imag_, FFT_SIZE);
    for (int i = 0; i < NUM_BINS; ++i)
        spectrum_[i] = sqrtf(real_[i] * real_[i] + imag_[i] * imag_[i]);
    extractSpectralFeatures();
    extractHarmonicFeatures();
    extractDynamicFeatures();
    buildProfile();
    hasProfile_ = true;
}

void ToneMatcher::extractSpectralFeatures() {
    float weightedSum = 0, totalEnergy = 0;
    for (int i = 1; i < NUM_BINS; ++i) {
        float e = spectrum_[i];
        weightedSum += (44100.0f * i / FFT_SIZE) * e; totalEnergy += e;
    }
    features_.centroid = (totalEnergy > 0) ? (weightedSum / totalEnergy) / 44100.0f : 0.5f;
    float cumulative = 0, threshold = 0.85f * totalEnergy;
    for (int i = 0; i < NUM_BINS; ++i) {
        cumulative += spectrum_[i];
        if (cumulative >= threshold) { features_.rolloff = (float)i / NUM_BINS; break; }
    }
    float bandSize = NUM_BINS / 7.0f;
    for (int b = 0; b < 7; ++b) {
        int start = (int)(b * bandSize), end = std::min((int)((b+1) * bandSize), NUM_BINS);
        float bandEnergy = 0;
        for (int i = start; i < end; ++i) bandEnergy += spectrum_[i];
        features_.bandEnergy[b] = totalEnergy > 0 ? bandEnergy / totalEnergy : 0;
    }
}

void ToneMatcher::extractHarmonicFeatures() {
    float fund = spectrum_[NUM_BINS / 8];
    features_.thd = fund > 0 ? fund / (fund + spectrum_[NUM_BINS / 4]) : 0.5f;
    float oddSum = 0, evenSum = 0;
    for (int i = 0; i < NUM_BINS; ++i) { if (i % 2 == 0) evenSum += spectrum_[i]; else oddSum += spectrum_[i]; }
    features_.oddEvenRatio = (evenSum + oddSum > 0) ? evenSum / (evenSum + oddSum) : 0.5f;
}

void ToneMatcher::extractDynamicFeatures() {
    float rms = 0, peak = 0;
    for (int i = 0; i < (int)sampleData_.size(); ++i) { rms += sampleData_[i]*sampleData_[i]; peak = std::max(peak, fabs(sampleData_[i])); }
    rms = sqrtf(rms / std::max(1, (int)sampleData_.size()));
    features_.rmsLevel = std::min(rms * 2.0f, 1.0f);
    features_.crestFactor = (rms > 0) ? peak / rms : 1.0f;
    float envSum = 0;
    for (int i = 0; i < (int)sampleData_.size(); ++i) envSum += fabs(sampleData_[i]);
    float avgEnv = envSum / std::max(1, (int)sampleData_.size());
    float varSum = 0;
    for (int i = 0; i < (int)sampleData_.size(); ++i) { float d = fabs(sampleData_[i]) - avgEnv; varSum += d*d; }
    features_.envelopeVariation = (avgEnv > 0) ? sqrtf(varSum / std::max(1, (int)sampleData_.size())) / avgEnv : 0;
}

void ToneMatcher::buildProfile() {
    profile_.bassEnergy = features_.bandEnergy[0];
    profile_.midEnergy = (features_.bandEnergy[2] + features_.bandEnergy[3]) / 2.0f;
    profile_.trebleEnergy = features_.bandEnergy[6];
    profile_.driveAmount = features_.thd * 0.5f;
    profile_.sustain = features_.rmsLevel;
    profile_.compression = (1.0f - features_.crestFactor) * 0.5f;
    profile_.reverbDecay = features_.rolloff;
    profile_.delayAmount = (1.0f - features_.oddEvenRatio) * 0.5f;
    profile_.brightness = features_.centroid;
    profile_.warmth = 1.0f - features_.centroid;
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
