#ifndef KRANK_ENGINE_H
#define KRANK_ENGINE_H

#include <oboe/Oboe.h>
#include <memory>
#include <vector>
#include <mutex>
#include "effects/effect.h"
#include "effects/tuner.h"
#include "effects/tone_matcher.h"
#include "effects/transcriber.h"
#include "effects/metronome.h"
#include "effects/looper.h"

class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine();

    bool start();
    void stop();

    // Effect chain control
    void setEffectEnabled(int index, bool enabled);
    bool isEffectEnabled(int index);
    void setEffectParameter(int index, int paramId, float value);
    float getEffectParameter(int index, int paramId);

    // Preset management
    void loadPreset(int preset);
    int currentPreset() const { return currentPreset_; }

    // Tuner interface
    void loadAudioForTuner(const float* data, int32_t numFrames, int32_t numChannels);
    float getTunerFrequency() const;
    int getTunerNoteIndex() const;
    int getTunerOctave() const;
    float getTunerCents() const;
    bool isTunerNoteDetected() const;
    const char* getTunerCurrentTuningName() const;
    const char* getTunerNoteName(int index) const;
    void setTunerMuteDry(bool mute);
    bool isTunerMuteDry() const { return tunerMuteDry_; }
    float getInputPeakLevel() const { return inputPeakDecay_; }

    // Audio monitoring
    void setMonitoringEnabled(bool enabled);
    bool isMonitoringEnabled() const { return monitoringEnabled_; }

    // Recording
    bool startRecording(const char* path);
    void stopRecording();
    bool isRecording() const { return recording_; }

    // Impulse response loader (cab simulation)
    bool loadImpulseResponse(const char* path);

    // Transcription
    bool transcribeAudio(const float* data, int32_t numSamples, int32_t sampleRate);
    bool hasTranscription() const;
    int getNumMeasures() const;
    float getTranscriptionProgress() const;
    void getTabData(int* outStrings, int* outFrets, float* outTimes, float* outDurations, int maxNotes);

    // Polyphonic transcription (ML source separation)
    bool transcribePolyphonic(const float* data, int32_t numSamples, int32_t sampleRate);
    void setPolyphonicTranscriptionEnabled(bool enabled) { polyphonicEnabled_ = enabled; }
    bool isPolyphonicTranscriptionEnabled() const { return polyphonicEnabled_; }
    bool hasPolyphonicResult() const;
    int getPolyphonicNoteCount() const;
    void getPolyphonicTabData(int* outStrings, int* outFrets, float* outTimes, float* outDurations, int maxNotes);

    // Tone matcher interface
    void loadAudioForToneMatcher(const float* data, int32_t numFrames, int32_t numChannels);
    bool hasToneMatcherProfile() const;
    float getRecommendedDistortionDrive() const;
    float getRecommendedDistortionTone() const;
    float getRecommendedDistortionLevel() const;
    float getRecommendedAmpSimGain() const;
    float getRecommendedAmpSimTone() const;
    float getRecommendedAmpSimMaster() const;
    float getRecommendedEqBass() const;
    float getRecommendedEqMid() const;
    float getRecommendedEqTreble() const;
    float getRecommendedChorusRate() const;
    float getRecommendedChorusDepth() const;
    float getRecommendedChorusMix() const;
    float getRecommendedDelayMix() const;
    float getRecommendedDelayFeedback() const;
    float getRecommendedDelayTime() const;
    float getRecommendedReverbSize() const;
    float getRecommendedReverbMix() const;

    // Metronome
    void setMetronomeEnabled(bool enabled);
    bool isMetronomeEnabled() const;
    void setMetronomeBpm(float bpm);
    float getMetronomeBpm() const;
    void setMetronomeVolume(float vol);
    float getMetronomeVolume() const;
    void tapTempo();
    void setMetronomeActive(bool active);

    // Looper
    void setLooperMode(int mode);
    int getLooperMode() const;
    void looperToggleRecord();
    void looperUndoOverdub();
    void looperClear();
    int getLooperLoopLength() const;
    float getLooperLoopDuration() const;

    // Preset serialization
    bool savePresetToFile(const char* path, int presetIndex);
    bool loadPresetFromFile(const char* path);

    // MIDI
    void setMidiCcMapping(int cc, int effectIndex, int paramId);
    int getMidiCcEffect(int cc) const;
    int getMidiCcParam(int cc) const;
    void handleMidiMessage(int status, int data1, int data2);
    void setMidiLearnMode(bool enabled) { midiLearnMode_ = enabled; }
    bool isMidiLearnMode() const { return midiLearnMode_; }
    int getMidiLearnEffect() const { return midiLearnEffect_; }
    int getMidiLearnParam() const { return midiLearnParam_; }
    void setMidiLearnTarget(int effectIdx, int paramId);

    // oboe callbacks
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override;
    void processInput(float* buffer, int32_t numFrames);
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> inputStream_;
    FILE* recordingFile_ = nullptr;
    bool recording_ = false;
    int recordingSampleRate_ = 48000;

    // Monitoring
    bool monitoringEnabled_ = false;

    // Polyphonic transcription
    bool polyphonicEnabled_ = false;
    std::unique_ptr<Transcriber> polyphonicTranscriber_;

    // Tuner mute
    bool tunerMuteDry_ = false;
    float inputPeak_ = 0.0f;
    float inputPeakDecay_ = 0.0f;

    // Convolution reverb / IR
    std::vector<float> irData_;
    std::vector<float> convBuffer_;
    int irLength_ = 0;
    bool irLoaded_ = false;

    std::shared_ptr<oboe::AudioStream> outputStream_;
    std::vector<float> ringBuffer_;
    int ringBufferWritePos_ = 0;
    int ringBufferReadPos_ = 0;
    std::vector<std::unique_ptr<Effect>> effects_;
    std::vector<bool> enabled_;
    int currentPreset_ = 0;

    // New components
    std::unique_ptr<Metronome> metronome_;
    std::unique_ptr<Looper> looper_;
    std::unique_ptr<Tuner> tuner_;
    std::unique_ptr<ToneMatcher> toneMatcher_;
    std::unique_ptr<Transcriber> transcriber_;

    std::mutex mutex_;

    // MIDI state
    bool midiLearnMode_ = false;
    int midiLearnEffect_ = 0;
    int midiLearnParam_ = 0;
    // CC -> (effectIndex, paramId) mapping
    int midiCcEffect_[128];
    int midiCcParam_[128];

    void initEffects();
    void buildSignalChain(float* buffer, int32_t numFrames, int32_t numChannels);
    static constexpr int RING_BUFFER_CAPACITY = 16384;
};

class InputCallback : public oboe::AudioStreamDataCallback {
public:
    explicit InputCallback(AudioEngine* engine) : engine_(engine) {}
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream, void* audioData, int32_t numFrames) override {
        engine_->processInput(static_cast<float*>(audioData), numFrames);
        return oboe::DataCallbackResult::Continue;
    }
private:
    AudioEngine* engine_;
};

#endif
