#include "transcriber.h"
#include <algorithm>

Transcriber::Transcriber() {
    track_.tuning[0] = 329.63f; // high E
    track_.tuning[1] = 246.94f; // B
    track_.tuning[2] = 196.0f;  // G
    track_.tuning[3] = 146.83f; // D
    track_.tuning[4] = 110.0f;  // A
    track_.tuning[5] = 82.41f;  // low E
}

float Transcriber::yinPitch(const float* signal, int n) {
    int half = n / 2;
    if (half < 2) return 0.0f;

    // Difference function
    float* diff = new float[half];
    for (int tau = 0; tau < half; ++tau) {
        float d = 0.0f;
        for (int i = 0; i < n - tau; ++i) {
            float diffVal = signal[i] - signal[i + tau];
            d += diffVal * diffVal;
        }
        diff[tau] = d;
    }

    // Cumulative mean normalized difference
    float* cmnd = new float[half];
    cmnd[0] = 1.0f;
    float runningSum = 0.0f;
    for (int tau = 1; tau < half; ++tau) {
        runningSum += diff[tau];
        cmnd[tau] = (runningSum > 0.0f) ? diff[tau] * tau / runningSum : 1.0f;
    }

    // Find first minimum below threshold
    int bestTau = -1;
    for (int tau = 1; tau < half; ++tau) {
        if (cmnd[tau] < YIN_THRESHOLD) {
            bestTau = tau;
            break;
        }
    }

    if (bestTau < 0) {
        // Fallback: global minimum
        float minVal = 1.0f;
        for (int tau = 1; tau < half; ++tau) {
            if (cmnd[tau] < minVal) {
                minVal = cmnd[tau];
                bestTau = tau;
            }
        }
    }

    delete[] diff;
    delete[] cmnd;

    if (bestTau <= 0) return 0.0f;

    // Parabolic interpolation
    float freq = 48000.0f / bestTau;

    // Clamp to guitar range
    if (freq < MIN_FREQ || freq > MAX_FREQ) return 0.0f;

    return freq;
}

void Transcriber::updateTrack(const float* audio, int32_t numSamples, int32_t sampleRate) {
    track_.measures.clear();
    track_.sampleRate = sampleRate;

    std::vector<TabNote> allNotes;
    float prevFreq = 0.0f;
    float noteStart = 0.0f;
    float prevAmp = 0.0f;
    bool noteActive = false;

    int numWindows = (numSamples - YIN_WINDOW) / HOP_SIZE;
    if (numWindows <= 0) return;

    for (int w = 0; w < numWindows; ++w) {
        int startSample = w * HOP_SIZE;
        if (startSample + YIN_WINDOW > numSamples) break;

        progress_ = (float)w / numWindows;

        const float* window = audio + startSample;
        float freq = yinPitch(window, YIN_WINDOW);

        // Compute amplitude in window
        float amp = 0.0f;
        for (int i = 0; i < YIN_WINDOW; ++i) {
            amp += fabsf(window[i]);
        }
        amp /= YIN_WINDOW;

        float timeSec = (float)startSample / sampleRate;

        if (freq > 0 && amp > 0.003f) {
            // Note detected
            if (!noteActive || fabsf(freq - prevFreq) > 3.0f) {
                // New note
                if (noteActive) {
                    // Finalize previous note
                    allNotes.back().duration = timeSec - allNotes.back().startTime;
                }
                TabNote note;
                note.pitch = freq;
                note.midiNote = (int)roundf(TabMapper::freqToMidiNote(freq));
                note.startTime = timeSec;
                note.duration = 0.0f;
                note.amplitude = amp;
                // Map to string/fret
                int string = TabMapper::freqToClosestString(note.pitch, track_.tuning, 6);
                if (string >= 0) {
                    note.stringNum = string;
                    float stringFreq = track_.tuning[string];
                    float midiNote = TabMapper::freqToMidiNote(freq);
                    float stringMidi = TabMapper::freqToMidiNote(stringFreq);
                    note.fret = (int)roundf(midiNote - stringMidi);
                    if (note.fret < 0) note.fret = 0;
                }

                allNotes.push_back(note);
                noteActive = true;
            } else {
                // Update ongoing note frequency
                if (!allNotes.empty()) {
                    allNotes.back().pitch = (allNotes.back().pitch + TabMapper::freqToMidiNote(freq)) * 0.5f;
                }
            }
            prevFreq = freq;
        } else {
            // No note - silence or noise
            if (noteActive && !allNotes.empty()) {
                allNotes.back().duration = timeSec - allNotes.back().startTime;
                noteActive = false;
            }
        }
        prevAmp = amp;

        // Update freq for next note
        if (freq > 0) prevFreq = freq;
    }

    // Close last note
    if (noteActive && !allNotes.empty()) {
        float totalTime = (float)numSamples / sampleRate;
        allNotes.back().duration = totalTime - allNotes.back().startTime;
    }

    // Group notes into measures
    float bps = track_.tempo / 60.0f;
    float beatDuration = 1.0f / bps;
    float measureDuration = beatDuration * 4; // 4/4 time

    if (allNotes.empty()) {
        hasResult_ = true;
        return;
    }

    float totalDuration = (float)numSamples / sampleRate;
    int numMeasures = (int)ceilf(totalDuration / measureDuration);
    if (numMeasures <= 0) numMeasures = 1;

    track_.measures.resize(numMeasures);
    for (int m = 0; m < numMeasures; ++m) {
        track_.measures[m].startTime = m * measureDuration;
        track_.measures[m].duration = measureDuration;
        track_.measures[m].timeSignatureNum = 4;
        track_.measures[m].timeSignatureDen = 4;
    }

    for (auto& note : allNotes) {
        int measureIdx = (int)(note.startTime / measureDuration);
        if (measureIdx >= 0 && measureIdx < numMeasures) {
            track_.measures[measureIdx].notes.push_back(note);
        }
    }

    hasResult_ = true;
}

bool Transcriber::transcribe(const float* audio, int32_t numSamples, int32_t sampleRate) {
    progress_ = 0.0f;
    hasResult_ = false;
    updateTrack(audio, numSamples, sampleRate);
    progress_ = 1.0f;
    return hasResult_;
}

bool Transcriber::transcribeSeparated(const float* guitarAudio, int32_t numSamples, int32_t sampleRate) {
    // Same as transcribe for now - when Demucs model is loaded, use it first
    return transcribe(guitarAudio, numSamples, sampleRate);
}

bool Transcriber::loadSeparationModel(const char* modelPath) {
    // TODO: Load TFLite model for Demucs source separation
    return false;
}
