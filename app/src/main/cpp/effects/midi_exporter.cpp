#include "midi_exporter.h"
#include <cstring>

void MidiExporter::writeVarLen(FILE* f, uint32_t value) {
    uint32_t buffer = value & 0x7F;
    while ((value >>= 7) > 0) {
        buffer <<= 8;
        buffer |= ((value & 0x7F) | 0x80);
    }
    while (true) {
        fputc(buffer & 0xFF, f);
        if (buffer & 0x80) buffer >>= 8;
        else break;
    }
}

void MidiExporter::writeU16(FILE* f, uint16_t value) {
    fputc((value >> 8) & 0xFF, f);
    fputc(value & 0xFF, f);
}

void MidiExporter::writeU32(FILE* f, uint32_t value) {
    fputc((value >> 24) & 0xFF, f);
    fputc((value >> 16) & 0xFF, f);
    fputc((value >> 8) & 0xFF, f);
    fputc(value & 0xFF, f);
}

bool MidiExporter::exportToMidi(const TabTrack& track, const char* path) {
    FILE* f = fopen(path, "wb");
    if (!f) return false;

    int ticksPerQuarterNote = 480;
    float secondsPerTick = (60.0f / track.tempo) / ticksPerQuarterNote;

    // Count all notes across all measures
    int totalNotes = 0;
    for (const auto& measure : track.measures) {
        totalNotes += (int)measure.notes.size();
    }

    // === HEADER CHUNK ===
    fwrite("MThd", 1, 4, f);
    writeU32(f, 6);          // chunk length
    writeU16(f, 1);          // format 1 (multiple tracks)
    writeU16(f, 2);          // 2 tracks: tempo track + notes track
    writeU16(f, ticksPerQuarterNote);

    // === TEMPO TRACK ===
    fwrite("MTrk", 1, 4, f);
    long tempoTrackSizePos = ftell(f);
    writeU32(f, 0);          // placeholder size

    // Set tempo (microseconds per quarter note)
    uint32_t usPerQuarter = (uint32_t)(60000000.0 / track.tempo);
    writeVarLen(f, 0);       // delta time = 0
    fputc(0xFF, f);          // meta event
    fputc(0x51, f);          // set tempo
    writeVarLen(f, 3);
    fputc((usPerQuarter >> 16) & 0xFF, f);
    fputc((usPerQuarter >> 8) & 0xFF, f);
    fputc(usPerQuarter & 0xFF, f);

    // Set track name
    writeVarLen(f, 0);
    fputc(0xFF, f);
    fputc(0x03, f);          // track name
    std::string name = track.name;
    writeVarLen(f, (uint32_t)name.length());
    fwrite(name.c_str(), 1, name.length(), f);

    // End of track
    writeVarLen(f, 0);
    fputc(0xFF, f);
    fputc(0x2F, f);
    writeVarLen(f, 0);

    // Write tempo track size
    long tempoTrackEndPos = ftell(f);
    fseek(f, tempoTrackSizePos, SEEK_SET);
    writeU32(f, (uint32_t)(tempoTrackEndPos - tempoTrackSizePos - 4));
    fseek(f, tempoTrackEndPos, SEEK_SET);

    // === NOTES TRACK ===
    fwrite("MTrk", 1, 4, f);
    long notesTrackSizePos = ftell(f);
    writeU32(f, 0);

    // Track name
    writeVarLen(f, 0);
    fputc(0xFF, f);
    fputc(0x03, f);
    writeVarLen(f, (uint32_t)name.length());
    fwrite(name.c_str(), 1, name.length(), f);

    // Program change to acoustic guitar (25)
    writeVarLen(f, 0);
    fputc(0xC0, f);          // program change, channel 0
    fputc(24, f);            // Acoustic Guitar (nylon)

    // Collect all notes sorted by start time
    std::vector<std::pair<float, TabNote>> sortedNotes;
    for (const auto& measure : track.measures) {
        for (const auto& note : measure.notes) {
            sortedNotes.push_back({note.startTime, note});
        }
    }
    std::sort(sortedNotes.begin(), sortedNotes.end(),
        [](const auto& a, const auto& b) { return a.first < b.first; });

    // Note on/off events sorted by time
    struct MidiEvent {
        int type;       // 0=note_on, 1=note_off
        float time;
        int midiNote;
        int velocity;
    };
    std::vector<MidiEvent> midiEvents;
    midiEvents.reserve(totalNotes * 2);

    for (const auto& pair : sortedNotes) {
        const auto& note = pair.second;
        if (note.midiNote <= 0 || note.midiNote > 127) continue;

        int velocity = (int)(note.amplitude * 100) + 20;
        if (velocity < 20) velocity = 20;
        if (velocity > 127) velocity = 127;

        midiEvents.push_back({0, note.startTime, note.midiNote, velocity});
        midiEvents.push_back({1, note.startTime + note.duration, note.midiNote, 0});
    }

    // Sort events by time (note ons before note offs at same time)
    std::sort(midiEvents.begin(), midiEvents.end(),
        [](const MidiEvent& a, const MidiEvent& b) {
            if (fabsf(a.time - b.time) > 0.001f) return a.time < b.time;
            return a.type < b.type; // note on before note off
        });

    // Write events
    float currentTime = 0.0f;
    for (const auto& ev : midiEvents) {
        float deltaTime = ev.time - currentTime;
        currentTime = ev.time;

        uint32_t ticks = (uint32_t)(deltaTime / secondsPerTick + 0.5f);
        writeVarLen(f, ticks);

        if (ev.type == 0) {
            fputc(0x90, f);  // note on, channel 0
            fputc(ev.midiNote, f);
            fputc(ev.velocity, f);
        } else {
            fputc(0x80, f);  // note off, channel 0
            fputc(ev.midiNote, f);
            fputc(64, f);    // standard release velocity
        }
    }

    // End of track
    writeVarLen(f, 0);
    fputc(0xFF, f);
    fputc(0x2F, f);
    writeVarLen(f, 0);

    // Write notes track size
    long notesTrackEndPos = ftell(f);
    fseek(f, notesTrackSizePos, SEEK_SET);
    writeU32(f, (uint32_t)(notesTrackEndPos - notesTrackSizePos - 4));
    fseek(f, notesTrackEndPos, SEEK_SET);

    fclose(f);
    return true;
}

bool MidiExporter::exportToAbc(const TabTrack& track, const char* path) {
    FILE* f = fopen(path, "w");
    if (!f) return false;

    // ABC notation header
    fprintf(f, "X:1\n");
    fprintf(f, "T:%s\n", track.name.c_str());
    fprintf(f, "M:4/4\n");
    fprintf(f, "L:1/16\n");
    fprintf(f, "Q:1/4=%d\n", (int)track.tempo);

    // Tuning annotation
    fprintf(f, "%% Tuning: EADGBE (guitar)\n");
    fprintf(f, "%% Generated by KRANK STUDIO\n");
    fprintf(f, "K:C\n");

    // Collect all notes sorted by start time
    std::vector<std::pair<float, TabNote>> allNotes;
    for (const auto& measure : track.measures) {
        for (const auto& note : measure.notes) {
            allNotes.push_back({note.startTime, note});
        }
    }
    std::sort(allNotes.begin(), allNotes.end(),
        [](const auto& a, const auto& b) { return a.first < b.first; });

    // Output notes
    float beatDuration = 60.0f / track.tempo;
    float measureDuration = beatDuration * 4.0f;

    // Group by string for multi-line tab view
    // For ABC, output as single melodic line
    fprintf(f, "%% MIDI guitar program\n");
    fprintf(f, "%%MIDI program 25\n");

    // Write each string as a voice
    const char* stringNames[] = {"High E", "B", "G", "D", "A", "Low E"};
    for (int s = 0; s < 6; s++) {
        fprintf(f, "V:%d name=\"%s\" clef=tab\n", s + 1, stringNames[s]);
        fprintf(f, "%%MIDI channel %d\n", s);
    }
    fprintf(f, "%%\n");

    // Write a basic melodic representation
    float currentPos = 0.0f;
    fprintf(f, "%% Notes: ");
    for (const auto& pair : allNotes) {
        const auto& note = pair.second;
        if (note.midiNote <= 0) continue;
        // Map to ABC note letters
        static const char* noteLetters[] = {
            "C,", "^C,", "D,", "^D,", "E,", "F,", "^F,", "G,", "^G,", "A,", "^A,", "B,",
            "C", "^C", "D", "^D", "E", "F", "^F", "G", "^G", "A", "^A", "B",
            "c", "^c", "d", "^d", "e", "f", "^f", "g", "^g", "a", "^a", "b",
            "c'", "^c'", "d'", "^d'", "e'", "f'", "^f'", "g'", "^g'", "a'", "^a'", "b'"
        };
        int noteIdx = (note.midiNote - 48); // C3 = midi 48
        if (noteIdx >= 0 && noteIdx < 48) {
            fprintf(f, "%s ", noteLetters[noteIdx % 48]);
        }
    }
    fprintf(f, "\n");

    fclose(f);
    return true;
}
