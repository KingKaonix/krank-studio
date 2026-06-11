#ifndef GUITARIX_ENGINE_H
#define GUITARIX_ENGINE_H

#include <oboe/Oboe.h>
#include <memory>
#include <vector>
#include <mutex>
#include "effects/effect.h"

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

    // oboe::AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) override;

    // oboe::AudioStreamErrorCallback
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> stream_;
    std::vector<std::unique_ptr<Effect>> effects_;
    std::vector<bool> enabled_;
    int currentPreset_ = 0;
    std::mutex mutex_;

    void initEffects();
    void buildSignalChain(float* buffer, int32_t numFrames, int32_t numChannels);
};

#endif
