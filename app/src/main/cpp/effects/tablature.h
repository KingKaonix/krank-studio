#ifndef KRANK_TABLATURE_H
#define KRANK_TABLATURE_H

#include <cstdint>
#include <vector>
#include <string>
#include <cmath>

// A single note in tablature
struct TabNote {
    int stringNum = -1;    // 0=high E, 5=low E
    int fret = -1;         // fret number (0=open)
    float pitch = 0.0f;    // MIDI note number
    float startTime = 0.0f; // seconds
    float duration = 0.0f;  // seconds
    float amplitude = 0.0f;
    int midiNote = 0;
};

// A measure/bar of tab
struct TabMeasure {
    std::vector<TabNote> notes;
    float startTime = 0.0f;
    float duration = 0.0f;
    int timeSignatureNum = 4;
    int timeSignatureDen = 4;
};

// Full tablature for one track
struct TabTrack {
    std::string name = "Guitar";
    int numStrings = 6;
    float tuning[6] = {329.63f, 246.94f, 196.0f, 146.83f, 110.0f, 82.41f}; // EADGBE
    std::vector<TabMeasure> measures;
    float tempo = 120.0f;    // BPM
    float sampleRate = 44100.0f;
};

// Fret-to-pitch mapping
class TabMapper {
public:
    static float freqToMidiNote(float freq) {
        if (freq <= 0) return 0;
        return 69.0f + 12.0f * log2f(freq / 440.0f);
    }


    static float midiNoteToFreq(float midiNote) {
        return 440.0f * powf(2.0f, (midiNote - 69.0f) / 12.0f);
    }
    static int midiNoteToFret(float midiNote, float stringOpenFreq) {
        float stringMidi = freqToMidiNote(stringOpenFreq);
        int semitones = (int)roundf(midiNote - stringMidi);
        if (semitones < 0 || semitones > 24) return -1; // out of range
        return semitones;
    }

    static int freqToClosestString(float freq, const float* tunings, int numStrings) {
        if (freq <= 0) return -1;
        float midi = freqToMidiNote(freq);
        int bestString = -1;
        int bestFret = 99;
        for (int s = 0; s < numStrings; ++s) {
            int fret = midiNoteToFret(midi, tunings[s]);
            if (fret >= 0 && fret < bestFret) {
                bestFret = fret;
                bestString = s;
            }
        }
        return bestString;
    }

    // Format a single tab as ASCII string
    static std::string noteToTabString(const TabNote& note, int numStrings) {
        std::string line;
        for (int s = 0; s < numStrings; ++s) {
            if (s == note.stringNum) {
                if (note.fret >= 10) line += std::to_string(note.fret);
                else line += "-" + std::to_string(note.fret);
            } else {
                line += "--";
            }
            line += "-";
        }
        return line;
    }
};

#endif
