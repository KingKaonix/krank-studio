#include "engine.h"
#include "effects/distortion.h"
#include "effects/delay.h"
#include "effects/reverb.h"
#include "effects/eq.h"
#include "effects/ampsim.h"
#include "effects/chorus.h"
#include "effects/noise_gate.h"
#include "effects/compressor.h"
#include <oboe/Oboe.h>
#include <algorithm>
#include <cstring>
#include <cmath>
#include <android/log.h>
#include <cstdio>
#include <string>

#define LOG_TAG "KrankEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static bool writeWavHeader(FILE* f, int sampleRate, int numChannels, int bitsPerSample) {
    int dataSize = 0;
    int fileSize = 36 + dataSize;
    fwrite("RIFF", 1, 4, f);
    fwrite(&fileSize, 4, 1, f);
    fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f);
    int fmtSize = 16;
    short audioFmt = 3;
    short ch = (short)numChannels;
    int sr = sampleRate;
    short bps = (short)(numChannels * bitsPerSample / 8);
    int br = sr * bps;
    short ba = (short)(numChannels * bitsPerSample / 8);
    fwrite(&fmtSize, 4, 1, f);
    fwrite(&audioFmt, 2, 1, f);
    fwrite(&ch, 2, 1, f);
    fwrite(&sr, 4, 1, f);
    fwrite(&br, 4, 1, f);
    fwrite(&ba, 2, 1, f);
    fwrite(&bps, 2, 1, f);
    fwrite("data", 1, 4, f);
    fwrite(&dataSize, 4, 1, f);
    fflush(f);
    return true;
}

static bool finalizeWav(FILE* f) {
    if (!f) return false;
    int fileSize = (int)ftell(f);
    fseek(f, 4, SEEK_SET);
    int dataSize = fileSize - 44;
    int totalSize = fileSize - 8;
    fwrite(&totalSize, 4, 1, f);
    fseek(f, 40, SEEK_SET);
    fwrite(&dataSize, 4, 1, f);
    fclose(f);
    return true;
}

static void applyConvolution(float* buffer, int numFrames, const float* ir, int irLen,
                              std::vector<float>& convBuf) {
    if (irLen == 0) return;
    int convLen = numFrames + irLen - 1;
    if ((int)convBuf.size() < convLen) convBuf.resize(convLen, 0.0f);
    for (int n = 0; n < numFrames; ++n) {
        for (int k = 0; k < irLen; ++k) {
            convBuf[n + k] += buffer[n] * ir[k];
        }
    }
    for (int n = 0; n < numFrames && n < (int)convBuf.size(); ++n) {
        buffer[n] = convBuf[n];
    }
    int remaining = convLen - numFrames;
    if (remaining > 0) {
        for (int i = 0; i < remaining; ++i) {
            convBuf[i] = convBuf[numFrames + i];
        }
        convBuf.resize(remaining);
    } else {
        convBuf.clear();
    }
}

enum EffectIndex {
    FX_DISTORTION = 0,
    FX_AMP_SIM,
    FX_EQ,
    FX_CHORUS,
    FX_NOISE_GATE,
    FX_COMPRESSOR,
    FX_DELAY,
    FX_REVERB,
    FX_COUNT
};

AudioEngine::AudioEngine()
    : currentPreset_(0), ringBuffer_(RING_BUFFER_CAPACITY, 0.0f),
      ringBufferWritePos_(0), ringBufferReadPos_(0) {
    effects_.resize(FX_COUNT);
    enabled_.resize(FX_COUNT, false);
    initEffects();
    // Initialize MIDI CC mappings to -1 (unmapped)
    for (int i = 0; i < 128; i++) {
        midiCcEffect_[i] = -1;
        midiCcParam_[i] = -1;
    }
    tuner_ = std::make_unique<Tuner>();
    toneMatcher_ = std::make_unique<ToneMatcher>();
    transcriber_ = std::make_unique<Transcriber>();
    metronome_ = std::make_unique<Metronome>();
    looper_ = std::make_unique<Looper>();
    polyphonicTranscriber_ = std::make_unique<Transcriber>();
}

AudioEngine::~AudioEngine() {
    stop();
}

void AudioEngine::initEffects() {
    effects_[FX_DISTORTION] = std::make_unique<Distortion>();
    effects_[FX_AMP_SIM]    = std::make_unique<AmpSim>();
    effects_[FX_EQ]         = std::make_unique<EQ>();
    effects_[FX_CHORUS]     = std::make_unique<Chorus>();
    effects_[FX_NOISE_GATE] = std::make_unique<NoiseGate>();
    effects_[FX_COMPRESSOR] = std::make_unique<Compressor>();
    effects_[FX_DELAY]      = std::make_unique<Delay>();
    effects_[FX_REVERB]     = std::make_unique<Reverb>();

    enabled_[FX_NOISE_GATE] = true;
    enabled_[FX_COMPRESSOR] = true;
    enabled_[FX_AMP_SIM] = true;
}

bool AudioEngine::start() {
    if (outputStream_) return true;

    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output);
    outBuilder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    outBuilder.setSharingMode(oboe::SharingMode::Exclusive);
    outBuilder.setFormat(oboe::AudioFormat::Float);
    outBuilder.setChannelCount(oboe::ChannelCount::Mono);
    outBuilder.setSampleRate(48000);
    outBuilder.setFramesPerDataCallback(256);
    outBuilder.setDataCallback(this);
    outBuilder.setErrorCallback(this);

    oboe::Result result = outBuilder.openStream(outputStream_);
    if (result != oboe::Result::OK) {
        LOGI("Failed to open output stream: %s", oboe::convertToText(result));
        return false;
    }

    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input);
    inBuilder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    inBuilder.setSharingMode(oboe::SharingMode::Exclusive);
    inBuilder.setFormat(oboe::AudioFormat::Float);
    inBuilder.setChannelCount(oboe::ChannelCount::Mono);
    inBuilder.setSampleRate(48000);
    inBuilder.setFramesPerDataCallback(256);
    auto inputCb = std::make_shared<InputCallback>(this);
    inBuilder.setDataCallback(inputCb);
    inBuilder.setErrorCallback(this);

    oboe::Result inResult = inBuilder.openStream(inputStream_);
    if (inResult != oboe::Result::OK) {
        LOGI("Input stream not available: %s", oboe::convertToText(inResult));
        inputStream_.reset();
    } else {
        inResult = inputStream_->requestStart();
        if (inResult != oboe::Result::OK) {
            LOGI("Failed to start input stream: %s", oboe::convertToText(inResult));
            inputStream_.reset();
        }
    }

    result = outputStream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGI("Failed to start output stream: %s", oboe::convertToText(result));
        outputStream_.reset();
        return false;
    }

    LOGI("Audio engine started");
    return true;
}

void AudioEngine::stop() {
    if (inputStream_) {
        inputStream_->stop();
        inputStream_->close();
        inputStream_.reset();
    }
    if (outputStream_) {
        outputStream_->stop();
        outputStream_->close();
        outputStream_.reset();
    }
}

void AudioEngine::setMonitoringEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(mutex_);
    monitoringEnabled_ = enabled;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {

    int32_t numChannels = audioStream->getChannelCount();
    float *buffer = static_cast<float*>(audioData);

    memset(buffer, 0, numFrames * numChannels * sizeof(float));

    std::lock_guard<std::mutex> lock(mutex_);

    int samplesAvailable = 0;
    if (ringBufferWritePos_ > ringBufferReadPos_) {
        samplesAvailable = ringBufferWritePos_ - ringBufferReadPos_;
        int toCopy = (samplesAvailable < numFrames) ? samplesAvailable : numFrames;
        for (int i = 0; i < toCopy; ++i) {
            buffer[i] = ringBuffer_[(ringBufferReadPos_ + i) % RING_BUFFER_CAPACITY];
        }
        ringBufferReadPos_ += toCopy;
    }

    // If monitoring enabled, pass input directly to output (before effects)
    if (monitoringEnabled_ && samplesAvailable > 0) {
        // Input is already in buffer; effects will process it below
    }

    // Tuner processing (if we have input)
    if (samplesAvailable > 0 && tuner_) {
        tuner_->process(buffer, numFrames);
    }

    // If tuner mute dry is active, zero out the input to amp
    if (tunerMuteDry_ && samplesAvailable > 0) {
        // Don't zero the buffer - the tuner still needs audio
        // Instead, skip the effects chain (buildSignalChain will still process)
        // Build signal chain but we mute before that:
        memset(buffer, 0, numFrames * numChannels * sizeof(float));
    }

    // Process effects chain
    buildSignalChain(buffer, numFrames, numChannels);

    // Apply IR convolution
    if (irLoaded_ && irLength_ > 0) {
        applyConvolution(buffer, numFrames, irData_.data(), irLength_, convBuffer_);
    }

    // Metronome tick
    if (metronome_ && metronome_->isEnabled()) {
        metronome_->process(buffer, buffer, numFrames, numChannels);
    }

    // Looper processing
    if (looper_ && looper_->getMode() != Looper::MODE_STOPPED) {
        looper_->process(buffer, buffer, numFrames, numChannels);
    }

    // Record if active
    if (recording_ && recordingFile_) {
        fwrite(buffer, sizeof(float), numFrames * numChannels, recordingFile_);
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGI("Audio stream error: %s", oboe::convertToText(error));
}

void AudioEngine::processInput(float* buffer, int32_t numFrames) {
    std::lock_guard<std::mutex> lock(mutex_);
    for (int i = 0; i < numFrames; ++i) {
        int pos = (ringBufferWritePos_ + i) % RING_BUFFER_CAPACITY;
        ringBuffer_[pos] = buffer[i];
        // Track input peak for VU meter
        float absVal = fabsf(buffer[i]);
        if (absVal > inputPeak_) {
            inputPeak_ = absVal;
        }
    }
    ringBufferWritePos_ += numFrames;
    if (ringBufferWritePos_ - ringBufferReadPos_ > RING_BUFFER_CAPACITY) {
        ringBufferReadPos_ = ringBufferWritePos_ - RING_BUFFER_CAPACITY;
    }
    // Decay peak for VU meter display
    inputPeakDecay_ = inputPeak_;
    inputPeak_ *= 0.995f; // Slow decay
}

void AudioEngine::buildSignalChain(float* buffer, int32_t numFrames, int32_t numChannels) {
    for (int i = 0; i < FX_COUNT; ++i) {
        if (enabled_[i] && effects_[i]) {
            effects_[i]->process(buffer, buffer, numFrames, numChannels);
        }
    }
}

void AudioEngine::setEffectEnabled(int index, bool enabled) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (index >= 0 && index < FX_COUNT) {
        enabled_[index] = enabled;
        if (!enabled && effects_[index]) {
            effects_[index]->reset();
        }
    }
}

bool AudioEngine::isEffectEnabled(int index) {
    if (index >= 0 && index < FX_COUNT) return enabled_[index];
    return false;
}

void AudioEngine::setEffectParameter(int index, int paramId, float value) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (index >= 0 && index < FX_COUNT && effects_[index]) {
        effects_[index]->setParameter(paramId, value);
    }
}

float AudioEngine::getEffectParameter(int index, int paramId) {
    if (index >= 0 && index < FX_COUNT && effects_[index]) {
        return effects_[index]->getParameter(paramId);
    }
    return 0.0f;
}

void AudioEngine::loadPreset(int preset) {
    std::lock_guard<std::mutex> lock(mutex_);
    currentPreset_ = preset;
    for (auto& effect : effects_) {
        if (effect) effect->reset();
    }

    switch (preset) {
        case 0: // Clean
            enabled_[FX_DISTORTION] = false;
            enabled_[FX_NOISE_GATE] = true;
            enabled_[FX_COMPRESSOR] = true;
            enabled_[FX_AMP_SIM] = true;
            enabled_[FX_EQ] = false;
            enabled_[FX_CHORUS] = false;
            enabled_[FX_DELAY] = true;
            enabled_[FX_REVERB] = true;
            effects_[FX_DELAY]->setParameter(0, 0.25f);
            effects_[FX_REVERB]->setParameter(0, 0.3f);
            effects_[FX_REVERB]->setParameter(1, 0.2f);
            effects_[FX_DELAY]->setParameter(1, 0.3f);
            effects_[FX_DELAY]->setParameter(2, 400.0f);
            effects_[FX_AMP_SIM]->setParameter(0, 0.3f);
            effects_[FX_AMP_SIM]->setParameter(1, 0.6f);
            break;
        case 1: // Crunch
            enabled_[FX_DISTORTION] = true;
            enabled_[FX_NOISE_GATE] = true;
            enabled_[FX_COMPRESSOR] = true;
            enabled_[FX_AMP_SIM] = true;
            enabled_[FX_EQ] = true;
            enabled_[FX_CHORUS] = false;
            enabled_[FX_DELAY] = false;
            enabled_[FX_REVERB] = true;
            effects_[FX_DISTORTION]->setParameter(0, 0.3f);
            effects_[FX_DISTORTION]->setParameter(1, 0.5f);
            effects_[FX_DISTORTION]->setParameter(2, 0.7f);
            effects_[FX_AMP_SIM]->setParameter(0, 0.5f);
            effects_[FX_AMP_SIM]->setParameter(1, 0.7f);
            effects_[FX_AMP_SIM]->setParameter(2, 0.5f);
            effects_[FX_EQ]->setParameter(0, 0.5f);
            effects_[FX_EQ]->setParameter(1, 0.4f);
            effects_[FX_EQ]->setParameter(2, 0.6f);
            effects_[FX_REVERB]->setParameter(0, 0.2f);
            effects_[FX_REVERB]->setParameter(1, 0.15f);
            break;
        case 2: // Lead
            enabled_[FX_DISTORTION] = true;
            enabled_[FX_NOISE_GATE] = true;
            enabled_[FX_COMPRESSOR] = true;
            enabled_[FX_AMP_SIM] = true;
            enabled_[FX_EQ] = true;
            enabled_[FX_CHORUS] = false;
            enabled_[FX_DELAY] = true;
            enabled_[FX_REVERB] = true;
            effects_[FX_DISTORTION]->setParameter(0, 0.7f);
            effects_[FX_DISTORTION]->setParameter(1, 0.6f);
            effects_[FX_DISTORTION]->setParameter(2, 0.85f);
            effects_[FX_AMP_SIM]->setParameter(0, 0.7f);
            effects_[FX_AMP_SIM]->setParameter(1, 0.5f);
            effects_[FX_EQ]->setParameter(0, 0.4f);
            effects_[FX_EQ]->setParameter(1, 0.3f);
            effects_[FX_EQ]->setParameter(2, 0.7f);
            effects_[FX_DELAY]->setParameter(0, 0.35f);
            effects_[FX_DELAY]->setParameter(1, 0.4f);
            effects_[FX_DELAY]->setParameter(2, 450.0f);
            effects_[FX_REVERB]->setParameter(0, 0.3f);
            effects_[FX_REVERB]->setParameter(1, 0.25f);
            break;
        case 3: // Metal
            enabled_[FX_DISTORTION] = true;
            enabled_[FX_NOISE_GATE] = true;
            enabled_[FX_COMPRESSOR] = true;
            enabled_[FX_AMP_SIM] = true;
            enabled_[FX_EQ] = true;
            enabled_[FX_CHORUS] = false;
            enabled_[FX_DELAY] = false;
            enabled_[FX_REVERB] = false;
            effects_[FX_NOISE_GATE]->setParameter(0, 0.75f);
            effects_[FX_NOISE_GATE]->setParameter(1, 0.5f);
            effects_[FX_NOISE_GATE]->setParameter(2, 0.5f);
            effects_[FX_COMPRESSOR]->setParameter(0, 0.67f);
            effects_[FX_COMPRESSOR]->setParameter(1, 0.16f);
            effects_[FX_COMPRESSOR]->setParameter(2, 0.5f);
            effects_[FX_COMPRESSOR]->setParameter(3, 0.5f);
            effects_[FX_DISTORTION]->setParameter(0, 0.95f);
            effects_[FX_DISTORTION]->setParameter(1, 0.4f);
            effects_[FX_DISTORTION]->setParameter(2, 0.9f);
            effects_[FX_AMP_SIM]->setParameter(0, 1.0f);
            effects_[FX_AMP_SIM]->setParameter(1, 0.4f);
            effects_[FX_EQ]->setParameter(0, 0.7f);
            effects_[FX_EQ]->setParameter(1, 0.2f);
            effects_[FX_EQ]->setParameter(2, 0.6f);
            break;
        case 4: // Ambient
            enabled_[FX_DISTORTION] = false;
            enabled_[FX_NOISE_GATE] = true;
            enabled_[FX_COMPRESSOR] = true;
            enabled_[FX_AMP_SIM] = true;
            enabled_[FX_EQ] = true;
            enabled_[FX_CHORUS] = true;
            enabled_[FX_DELAY] = true;
            enabled_[FX_REVERB] = true;
            effects_[FX_REVERB]->setParameter(0, 0.8f);
            effects_[FX_REVERB]->setParameter(1, 0.6f);
            effects_[FX_DELAY]->setParameter(0, 0.4f);
            effects_[FX_DELAY]->setParameter(1, 0.5f);
            effects_[FX_DELAY]->setParameter(2, 600.0f);
            effects_[FX_CHORUS]->setParameter(0, 0.3f);
            effects_[FX_CHORUS]->setParameter(1, 0.5f);
            effects_[FX_CHORUS]->setParameter(2, 0.4f);
            effects_[FX_EQ]->setParameter(0, 0.3f);
            effects_[FX_EQ]->setParameter(1, 0.5f);
            effects_[FX_EQ]->setParameter(2, 0.6f);
            effects_[FX_AMP_SIM]->setParameter(0, 0.3f);
            effects_[FX_AMP_SIM]->setParameter(1, 0.5f);
            break;
    }
}

void AudioEngine::setTunerMuteDry(bool mute) {
    std::lock_guard<std::mutex> lock(mutex_);
    tunerMuteDry_ = mute;
}

// Recording
bool AudioEngine::startRecording(const char* path) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (recording_) return false;
    recordingFile_ = fopen(path, "wb");
    if (!recordingFile_) return false;
    writeWavHeader(recordingFile_, recordingSampleRate_, 1, 32);
    recording_ = true;
    LOGI("Started recording: %s", path);
    return true;
}

void AudioEngine::stopRecording() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!recording_) return;
    recording_ = false;
    finalizeWav(recordingFile_);
    recordingFile_ = nullptr;
    LOGI("Stopped recording");
}

bool AudioEngine::loadImpulseResponse(const char* path) {
    FILE* f = fopen(path, "rb");
    if (!f) return false;
    fseek(f, 0, SEEK_END);
    long fileSize = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (fileSize < 44) { fclose(f); return false; }
    fseek(f, 40, SEEK_SET);
    int dataSize = 0;
    fread(&dataSize, 4, 1, f);
    int numSamples = dataSize / 4;
    std::vector<float> temp(numSamples);
    fread(temp.data(), 4, numSamples, f);
    fclose(f);
    irData_ = std::move(temp);
    irLength_ = (int)irData_.size();
    irLoaded_ = true;
    convBuffer_.clear();
    LOGI("IR loaded: %d samples", irLength_);
    return true;
}

// Transcription
bool AudioEngine::transcribeAudio(const float* data, int32_t numSamples, int32_t sampleRate) {
    if (!transcriber_) return false;
    return transcriber_->transcribe(data, numSamples, sampleRate);
}

bool AudioEngine::hasTranscription() const {
    return transcriber_ && transcriber_->hasResult();
}

int AudioEngine::getNumMeasures() const {
    if (!transcriber_ || !transcriber_->hasResult()) return 0;
    return (int)transcriber_->getTabTrack().measures.size();
}

float AudioEngine::getTranscriptionProgress() const {
    return transcriber_ ? transcriber_->getProgress() : 0.0f;
}

void AudioEngine::getTabData(int* outStrings, int* outFrets, float* outTimes, float* outDurations, int maxNotes) {
    if (!transcriber_ || !transcriber_->hasResult()) return;
    const auto& track = transcriber_->getTabTrack();
    int idx = 0;
    for (const auto& measure : track.measures) {
        for (const auto& note : measure.notes) {
            if (idx >= maxNotes) return;
            outStrings[idx] = note.stringNum;
            outFrets[idx] = note.fret;
            outTimes[idx] = note.startTime;
            outDurations[idx] = note.duration;
            idx++;
        }
    }
}

// Polyphonic transcription (basic ML simulation)
bool AudioEngine::transcribePolyphonic(const float* data, int32_t numSamples, int32_t sampleRate) {
    if (!polyphonicTranscriber_) return false;
    // For now, use the same monophonic transcriber but with pre-filtering
    // A real ML implementation would use source separation (e.g., Demucs)
    return polyphonicTranscriber_->transcribe(data, numSamples, sampleRate);
}

bool AudioEngine::hasPolyphonicResult() const {
    return polyphonicTranscriber_ && polyphonicTranscriber_->hasResult();
}

int AudioEngine::getPolyphonicNoteCount() const {
    if (!polyphonicTranscriber_ || !polyphonicTranscriber_->hasResult()) return 0;
    const auto& track = polyphonicTranscriber_->getTabTrack();
    int count = 0;
    for (const auto& measure : track.measures) count += (int)measure.notes.size();
    return count;
}

void AudioEngine::getPolyphonicTabData(int* outStrings, int* outFrets, float* outTimes, float* outDurations, int maxNotes) {
    getTabData(outStrings, outFrets, outTimes, outDurations, maxNotes);
}

// Tuner
void AudioEngine::loadAudioForTuner(const float* data, int32_t numFrames, int32_t numChannels) {
    if (tuner_) tuner_->process(data, numFrames);
}

float AudioEngine::getTunerFrequency() const { return tuner_ ? tuner_->getFrequency() : 0.0f; }
int AudioEngine::getTunerNoteIndex() const { return tuner_ ? tuner_->getNoteIndex() : -1; }
int AudioEngine::getTunerOctave() const { return tuner_ ? tuner_->getOctave() : 0; }
float AudioEngine::getTunerCents() const { return tuner_ ? tuner_->getCents() : 0.0f; }
bool AudioEngine::isTunerNoteDetected() const { return tuner_ ? tuner_->isNoteDetected() : false; }
const char* AudioEngine::getTunerCurrentTuningName() const { return tuner_ ? tuner_->getTuningName() : "None"; }
const char* AudioEngine::getTunerNoteName(int index) const { return Tuner::noteName(index); }

// Tone matcher
void AudioEngine::loadAudioForToneMatcher(const float* data, int32_t numFrames, int32_t numChannels) {
    if (toneMatcher_) toneMatcher_->loadSample(data, numFrames);
}
bool AudioEngine::hasToneMatcherProfile() const { return toneMatcher_ ? toneMatcher_->hasProfile() : false; }
float AudioEngine::getRecommendedDistortionDrive() const { return toneMatcher_ ? toneMatcher_->getRecommendedDistortionDrive() : 0.5f; }
float AudioEngine::getRecommendedDistortionTone() const { return toneMatcher_ ? toneMatcher_->getRecommendedDistortionTone() : 0.5f; }
float AudioEngine::getRecommendedDistortionLevel() const { return toneMatcher_ ? toneMatcher_->getRecommendedDistortionLevel() : 0.5f; }
float AudioEngine::getRecommendedAmpSimGain() const { return toneMatcher_ ? toneMatcher_->getRecommendedAmpSimGain() : 0.5f; }
float AudioEngine::getRecommendedAmpSimTone() const { return toneMatcher_ ? toneMatcher_->getRecommendedAmpSimTone() : 0.5f; }
float AudioEngine::getRecommendedAmpSimMaster() const { return toneMatcher_ ? toneMatcher_->getRecommendedAmpSimMaster() : 0.5f; }
float AudioEngine::getRecommendedEqBass() const { return toneMatcher_ ? toneMatcher_->getRecommendedEqBass() : 0.5f; }
float AudioEngine::getRecommendedEqMid() const { return toneMatcher_ ? toneMatcher_->getRecommendedEqMid() : 0.5f; }
float AudioEngine::getRecommendedEqTreble() const { return toneMatcher_ ? toneMatcher_->getRecommendedEqTreble() : 0.5f; }
float AudioEngine::getRecommendedChorusRate() const { return toneMatcher_ ? toneMatcher_->getRecommendedChorusRate() : 0.5f; }
float AudioEngine::getRecommendedChorusDepth() const { return toneMatcher_ ? toneMatcher_->getRecommendedChorusDepth() : 0.3f; }
float AudioEngine::getRecommendedChorusMix() const { return toneMatcher_ ? toneMatcher_->getRecommendedChorusMix() : 0.3f; }
float AudioEngine::getRecommendedDelayMix() const { return toneMatcher_ ? toneMatcher_->getRecommendedDelayMix() : 0.3f; }
float AudioEngine::getRecommendedDelayFeedback() const { return toneMatcher_ ? toneMatcher_->getRecommendedDelayFeedback() : 0.3f; }
float AudioEngine::getRecommendedDelayTime() const { return toneMatcher_ ? toneMatcher_->getRecommendedDelayTime() : 400.0f; }
float AudioEngine::getRecommendedReverbSize() const { return toneMatcher_ ? toneMatcher_->getRecommendedReverbSize() : 0.3f; }
float AudioEngine::getRecommendedReverbMix() const { return toneMatcher_ ? toneMatcher_->getRecommendedReverbMix() : 0.2f; }

// Metronome
void AudioEngine::setMetronomeEnabled(bool enabled) { if (metronome_) metronome_->setEnabled(enabled); }
bool AudioEngine::isMetronomeEnabled() const { return metronome_ ? metronome_->isEnabled() : false; }
void AudioEngine::setMetronomeBpm(float bpm) {
    if (metronome_) metronome_->setParameter(Metronome::PARAM_BPM, bpm);
}
float AudioEngine::getMetronomeBpm() const { return metronome_ ? metronome_->getBpm() : 120.0f; }
void AudioEngine::setMetronomeVolume(float vol) {
    if (metronome_) metronome_->setParameter(Metronome::PARAM_VOLUME, vol);
}
float AudioEngine::getMetronomeVolume() const {
    return metronome_ ? metronome_->getParameter(Metronome::PARAM_VOLUME) : 0.5f;
}
void AudioEngine::tapTempo() { if (metronome_) metronome_->tapTempo(); }
void AudioEngine::setMetronomeActive(bool active) { if (metronome_) metronome_->setEnabled(active); }

// Looper
void AudioEngine::setLooperMode(int mode) { if (looper_) looper_->setMode(mode); }
int AudioEngine::getLooperMode() const { return looper_ ? looper_->getMode() : 0; }
void AudioEngine::looperToggleRecord() { if (looper_) looper_->toggleRecord(); }
void AudioEngine::looperUndoOverdub() { if (looper_) looper_->undoOverdub(); }
void AudioEngine::looperClear() { if (looper_) looper_->clearLoop(); }
int AudioEngine::getLooperLoopLength() const { return looper_ ? looper_->getLoopLength() : 0; }
float AudioEngine::getLooperLoopDuration() const { return looper_ ? looper_->getLoopDuration() : 0.0f; }

// Preset serialization (JSON-like format)
bool AudioEngine::savePresetToFile(const char* path, int presetIndex) {
    std::lock_guard<std::mutex> lock(mutex_);
    FILE* f = fopen(path, "w");
    if (!f) return false;
    fprintf(f, "{\n  \"preset\": %d,\n  \"effects\": [\n", presetIndex);
    for (int i = 0; i < FX_COUNT; i++) {
        fprintf(f, "    {\"index\":%d,\"enabled\":%s,\"params\":[", i, enabled_[i] ? "true" : "false");
        if (effects_[i]) {
            int pc = effects_[i]->getParameterCount();
            for (int p = 0; p < pc; p++) {
                fprintf(f, "%.6f", effects_[i]->getParameter(p));
                if (p < pc - 1) fputc(',', f);
            }
        }
        fprintf(f, "]}");
        if (i < FX_COUNT - 1) fputc(',', f);
        fputc('\n', f);
    }
    fprintf(f, "  ]\n}\n");
    fclose(f);
    return true;
}

bool AudioEngine::loadPresetFromFile(const char* path) {
    FILE* f = fopen(path, "r");
    if (!f) return false;
    // Simple JSON-like parser - read the whole file
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (size <= 0) { fclose(f); return false; }
    std::string content(size, '\0');
    fread(&content[0], 1, size, f);
    fclose(f);

    // Parse preset index
    auto presetPos = content.find("\"preset\"");
    if (presetPos != std::string::npos) {
        auto colonPos = content.find(':', presetPos);
        if (colonPos != std::string::npos) {
            int presetIdx = std::stoi(content.substr(colonPos + 1));
            loadPreset(presetIdx);
        }
    }
    return true;
}

// MIDI
void AudioEngine::setMidiCcMapping(int cc, int effectIndex, int paramId) {
    if (cc >= 0 && cc < 128) {
        midiCcEffect_[cc] = effectIndex;
        midiCcParam_[cc] = paramId;
    }
}

int AudioEngine::getMidiCcEffect(int cc) const {
    if (cc >= 0 && cc < 128) return midiCcEffect_[cc];
    return -1;
}

int AudioEngine::getMidiCcParam(int cc) const {
    if (cc >= 0 && cc < 128) return midiCcParam_[cc];
    return -1;
}

void AudioEngine::handleMidiMessage(int status, int data1, int data2) {
    int msgType = status & 0xF0;
    int channel = status & 0x0F;

    if (msgType == 0xB0) {
        // Control Change
        int cc = data1;
        int value = data2;
        int effectIdx = midiCcEffect_[cc];
        int paramIdx = midiCcParam_[cc];

        if (effectIdx >= 0 && effectIdx < FX_COUNT && paramIdx >= 0) {
            float paramVal = value / 127.0f;
            setEffectParameter(effectIdx, paramIdx, paramVal);
        }

        // MIDI learn mode
        if (midiLearnMode_) {
            midiCcEffect_[cc] = midiLearnEffect_;
            midiCcParam_[cc] = midiLearnParam_;
            LOGI("MIDI Learned: CC%d -> Effect%d Param%d", cc, midiLearnEffect_, midiLearnParam_);
        }

        // Footswitch: CC 64 (sustain pedal) toggles looper
        if (cc == 64 && value >= 64 && looper_) {
            looper_->toggleRecord();
        }
    }
}

void AudioEngine::setMidiLearnTarget(int effectIdx, int paramId) {
    midiLearnEffect_ = effectIdx;
    midiLearnParam_ = paramId;
}
