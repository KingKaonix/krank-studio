#include "transcriber.h"
#include <algorithm>
#include <cmath>

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
    if (half < 4) return 0.0f;

    // Difference function
    std::vector<float> diff(half, 0.0f);
    for (int tau = 1; tau < half; ++tau) {
        float d = 0.0f;
        for (int i = 0; i < n - tau; ++i) {
            float dv = signal[i] - signal[i + tau];
            d += dv * dv;
        }
        diff[tau] = d;
    }

    // Cumulative mean normalized difference
    std::vector<float> cmnd(half, 1.0f);
    cmnd[0] = 1.0f;
    float runningSum = 0.0f;
    for (int tau = 1; tau < half; ++tau) {
        runningSum += diff[tau];
        cmnd[tau] = (runningSum > 0.0f) ? diff[tau] * tau / runningSum : 1.0f;
    }

    // Find first minimum below threshold (absolute threshold)
    int bestTau = -1;
    float bestVal = 1.0f;
    for (int tau = 2; tau < half; ++tau) {
        if (cmnd[tau] < YIN_THRESHOLD && cmnd[tau] < cmnd[tau - 1]) {
            bestTau = tau;
            bestVal = cmnd[tau];
            break;
        }
    }

    // Fallback: global minimum if nothing below threshold
    if (bestTau < 0) {
        for (int tau = 2; tau < half; ++tau) {
            if (cmnd[tau] < bestVal) {
                bestVal = cmnd[tau];
                bestTau = tau;
            }
        }
    }

    if (bestTau <= 0) return 0.0f;

    // Parabolic interpolation for better accuracy
    if (bestTau > 0 && bestTau < half - 1) {
        float y0 = cmnd[bestTau - 1];
        float y1 = cmnd[bestTau];
        float y2 = cmnd[bestTau + 1];
        float a = (y0 + y2 - 2.0f * y1) / 2.0f;
        if (a != 0.0f) {
            float correction = (y0 - y2) / (4.0f * a);
            if (correction > -0.5f && correction < 0.5f) {
                bestTau = (int)(bestTau + correction);
            }
        }
    }

    float freq = 48000.0f / bestTau;
    if (freq < MIN_FREQ || freq > MAX_FREQ) return 0.0f;
    return freq;
}

void Transcriber::updateTrack(const float* audio, int32_t numSamples, int32_t sampleRate) {
    track_.measures.clear();
    track_.sampleRate = sampleRate;

    int numWindows = (numSamples - YIN_WINDOW) / HOP_SIZE;
    if (numWindows <= 0) return;

    // Collect raw note events: (time, freq, amp)
    struct RawEvent { float time, freq, amp; };
    std::vector<RawEvent> events;
    events.reserve(numWindows);

    for (int w = 0; w < numWindows; ++w) {
        int startSample = w * HOP_SIZE;
        if (startSample + YIN_WINDOW > numSamples) break;
        progress_ = (float)w / numWindows;

        const float* window = audio + startSample;
        float freq = yinPitch(window, YIN_WINDOW);

        // RMS amplitude
        float sumSq = 0.0f;
        for (int i = 0; i < YIN_WINDOW; ++i) {
            sumSq += window[i] * window[i];
        }
        float rms = sqrtf(sumSq / YIN_WINDOW);

        float timeSec = (float)startSample / sampleRate;
        events.push_back({timeSec, freq, rms});
    }

    // Group events into sustained notes
    std::vector<TabNote> allNotes;
    float prevMidi = 0.0f;
    float noteStartTime = 0.0f;
    float noteFreqSum = 0.0f;
    int noteFreqCount = 0;
    bool inNote = false;
    int silenceCounter = 0;
    const int maxSilenceHops = 4; // ~43ms of silence before note break

    for (size_t i = 0; i < events.size(); ++i) {
        const auto& ev = events[i];

        if (ev.freq > 0.0f && ev.amp > 0.002f) {
            float currMidi = TabMapper::freqToMidiNote(ev.freq);

            if (!inNote) {
                // Start new note
                inNote = true;
                silenceCounter = 0;
                noteStartTime = ev.time;
                noteFreqSum = currMidi;
                noteFreqCount = 1;
                prevMidi = currMidi;
            } else {
                // Check if this is still the same note (within 1 semitone)
                if (fabsf(currMidi - prevMidi) <= 1.5f) {
                    noteFreqSum += currMidi;
                    noteFreqCount++;
                    prevMidi = currMidi;
                    silenceCounter = 0;
                } else {
                    // New different note - finalize previous
                    TabNote note;
                    note.pitch = noteFreqSum / noteFreqCount;
                    note.midiNote = (int)roundf(note.pitch);
                    note.startTime = noteStartTime;
                    note.duration = ev.time - noteStartTime;
                    note.amplitude = ev.amp;
                    int string = TabMapper::freqToClosestString(
                        TabMapper::midiNoteToFreq(note.pitch), track_.tuning, 6);
                    if (string >= 0) {
                        note.stringNum = string;
                        float stringFreq = track_.tuning[string];
                        float stringMidi = TabMapper::freqToMidiNote(stringFreq);
                        note.fret = (int)roundf(note.pitch - stringMidi);
                        if (note.fret < 0) note.fret = 0;
                        if (note.fret > 24) note.fret = 24;
                    }
                    allNotes.push_back(note);

                    // Start new note
                    noteStartTime = ev.time;
                    noteFreqSum = currMidi;
                    noteFreqCount = 1;
                    prevMidi = currMidi;
                }
            }
        } else {
            if (inNote) {
                silenceCounter++;
                if (silenceCounter > maxSilenceHops) {
                    // End the note
                    TabNote note;
                    note.pitch = noteFreqCount > 0 ? noteFreqSum / noteFreqCount : 0;
                    note.midiNote = (int)roundf(note.pitch);
                    note.startTime = noteStartTime;
                    float endTime = events[i - maxSilenceHops > 0 ? i - maxSilenceHops : 0].time;
                    note.duration = endTime - noteStartTime;
                    if (note.duration < 0.05f) note.duration = 0.05f;
                    note.amplitude = ev.amp;
                    int string = TabMapper::freqToClosestString(
                        TabMapper::midiNoteToFreq(note.pitch), track_.tuning, 6);
                    if (string >= 0) {
                        note.stringNum = string;
                        float stringFreq = track_.tuning[string];
                        float stringMidi = TabMapper::freqToMidiNote(stringFreq);
                        note.fret = (int)roundf(note.pitch - stringMidi);
                        if (note.fret < 0) note.fret = 0;
                        if (note.fret > 24) note.fret = 24;
                    }
                    allNotes.push_back(note);
                    inNote = false;
                }
            }
        }
    }

    // Close last note if still active
    if (inNote && noteFreqCount > 0) {
        TabNote note;
        note.pitch = noteFreqSum / noteFreqCount;
        note.midiNote = (int)roundf(note.pitch);
        note.startTime = noteStartTime;
        note.duration = events.empty() ? 0.5f : events.back().time - noteStartTime;
        if (note.duration < 0.05f) note.duration = 0.05f;
        note.amplitude = events.empty() ? 0 : events.back().amp;
        int string = TabMapper::freqToClosestString(
            TabMapper::midiNoteToFreq(note.pitch), track_.tuning, 6);
        if (string >= 0) {
            note.stringNum = string;
            float stringFreq = track_.tuning[string];
            float stringMidi = TabMapper::freqToMidiNote(stringFreq);
            note.fret = (int)roundf(note.pitch - stringMidi);
            if (note.fret < 0) note.fret = 0;
            if (note.fret > 24) note.fret = 24;
        }
        allNotes.push_back(note);
    }

    if (allNotes.empty()) {
        hasResult_ = true;
        return;
    }

    // Group into measures (4/4 time, ~120bpm default)
    float totalDuration = (float)numSamples / sampleRate;
    float beatDuration = 60.0f / track_.tempo;
    float measureDuration = beatDuration * 4.0f;
    int numMeasures = (int)ceilf(totalDuration / measureDuration);
    if (numMeasures < 1) numMeasures = 1;

    track_.measures.resize(numMeasures);
    for (int m = 0; m < numMeasures; ++m) {
        track_.measures[m].startTime = m * measureDuration;
        track_.measures[m].duration = measureDuration;
        track_.measures[m].timeSignatureNum = 4;
        track_.measures[m].timeSignatureDen = 4;
    }

    for (auto& note : allNotes) {
        int mIdx = (int)(note.startTime / measureDuration);
        if (mIdx >= 0 && mIdx < numMeasures) {
            track_.measures[mIdx].notes.push_back(note);
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
    return transcribe(guitarAudio, numSamples, sampleRate);
}

bool Transcriber::loadSeparationModel(const char* modelPath) {
    return false;
}
