#ifndef GUITARIX_TONE_MATCHER_H
#define GUITARIX_TONE_MATCHER_H
#include <cstdint>
#include <cmath>
#include <vector>
#include <cstring>
#include <algorithm>
struct ToneProfile {
    float bassEnergy=0.5f,midEnergy=0.5f,trebleEnergy=0.5f;
    float driveAmount=0.0f,oddEvenRatio=0.5f,sustain=0.5f,compression=0.3f;
    float reverbDecay=0.2f,delayAmount=0.0f,brightness=0.5f,warmth=0.5f,gain=0.5f;
};
class ToneMatcher {
public:
    ToneMatcher();~ToneMatcher();
    void loadSample(const float*d,int32_t n);void clearSample();
    bool hasSample()const{return hasSample_;}
    void analyze();bool hasProfile()const{return hasProfile_;}
    const ToneProfile&getProfile()const{return profile_;}
    float getRecommendedDistortionDrive()const;float getRecommendedDistortionTone()const;float getRecommendedDistortionLevel()const;
    float getRecommendedAmpSimGain()const;float getRecommendedAmpSimTone()const;float getRecommendedAmpSimMaster()const;
    float getRecommendedEqBass()const;float getRecommendedEqMid()const;float getRecommendedEqTreble()const;
    float getRecommendedChorusRate()const;float getRecommendedChorusDepth()const;float getRecommendedChorusMix()const;
    float getRecommendedDelayMix()const;float getRecommendedDelayFeedback()const;float getRecommendedDelayTime()const;
    float getRecommendedReverbSize()const;float getRecommendedReverbMix()const;
    static constexpr int FFT_SIZE=4096,NUM_BINS=FFT_SIZE/2;
    const float*getSpectrum()const{return spectrum_;}int getSpectrumSize()const{return NUM_BINS;}
private:
    void computeFFT(float*,float*,int);void computeSpectrum(const float*,int32_t);
    void extractSpectralFeatures();void extractHarmonicFeatures();void extractDynamicFeatures();void buildProfile();
    std::vector<float>sampleData_;bool hasSample_=false,hasProfile_=false;ToneProfile profile_;
    float real_[FFT_SIZE],imag_[FFT_SIZE],window_[FFT_SIZE],spectrum_[NUM_BINS];
    struct{float bandEnergy[7];float centroid=0.5f,rolloff=0.5f,thd=0.0f,oddEvenRatio=0.5f,rmsLevel=0.0f,crestFactor=0.0f,envelopeVariation=0.0f;}features_;
};
#endif
