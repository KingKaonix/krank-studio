#ifndef KRANK_TUNER_H
#define KRANK_TUNER_H
#include <cstdint>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <vector>
class Tuner {
public:
    enum TuningPreset{TUNING_STANDARD=0,TUNING_DROP_D,TUNING_DROP_C,TUNING_OPEN_D,TUNING_OPEN_G,TUNING_OPEN_E,TUNING_DADGAD,TUNING_HALF_STEP_DOWN,TUNING_FULL_STEP_DOWN,TUNING_DROP_B,TUNING_OPEN_A,TUNING_CUSTOM,TUNING_COUNT};
    static constexpr int NUM_STRINGS=6;static constexpr float DEFAULT_REF_FREQ=440.0f;
    Tuner();~Tuner();
    float process(const float*,int32_t);float getFrequency()const{return frequency_;}
    int getNoteIndex()const{return noteIndex_;}int getOctave()const{return octave_;}
    float getCents()const{return cents_;}bool isNoteDetected()const{return noteDetected_;}
    void setReferenceFreq(float);float getReferenceFreq()const{return referenceFreq_;}
    void setTuningPreset(int);int getCurrentTuningPreset()const{return currentTuning_;}
    const char*getTuningName()const;void getStringTunings(float*)const;
    int getClosestString()const;float getStringCents()const;void setCustomTuning(const float*);void reset();
    static const char*noteName(int);
private:
    float yinPitchDetection(const float*,int32_t);void freqToNote(float);
    float frequency_,cents_,referenceFreq_,smoothedFreq_,smoothingFactor_;int noteIndex_,octave_,currentTuning_;bool noteDetected_;
    float stringTunings_[NUM_STRINGS];static constexpr int YIN_MAX_FRAMES=2048;static constexpr float YIN_THRESHOLD=0.15f;
};
#endif
