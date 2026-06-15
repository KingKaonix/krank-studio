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

bool MidiExporter::exportToMusicXml(const TabTrack& track, const char* path) {
    FILE* f = fopen(path, "w");
    if (!f) return false;

    // Collect all notes sorted by time
    std::vector<std::pair<float, TabNote>> allNotes;
    for (const auto& measure : track.measures) {
        for (const auto& note : measure.notes) {
            allNotes.push_back({note.startTime, note});
        }
    }
    std::sort(allNotes.begin(), allNotes.end(),
        [](const auto& a, const auto& b) { return a.first < b.first; });

    if (allNotes.empty()) {
        fclose(f);
        return false;
    }

    float totalDuration = 0.0f;
    for (const auto& p : allNotes) {
        float end = p.first + p.second.duration;
        if (end > totalDuration) totalDuration = end;
    }

    float beatDuration = 60.0f / track.tempo;
    float divisions = 4.0f;

    fprintf(f, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    fprintf(f, "<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\"\n");
    fprintf(f, "  \"http://www.musicxml.org/dtds/partwise.dtd\">\n");
    fprintf(f, "<score-partwise version=\"4.0\">\n");

    // Work (song) info
    fprintf(f, "  <work>\n    <work-title>%s</work-title>\n  </work>\n", track.name.c_str());
    fprintf(f, "  <movement-title>%s</movement-title>\n", track.name.c_str());

    // Identification
    fprintf(f, "  <identification>\n");
    fprintf(f, "    <encoding>\n");
    fprintf(f, "      <software>KRANK STUDIO v1.3.0</software>\n");
    fprintf(f, "      <encoding-date>2026-06-15</encoding-date>\n");
    fprintf(f, "      <supports attribute=\"new-system\" element=\"print\" type=\"yes\" value=\"yes\"/>\n");
    fprintf(f, "    </encoding>\n");
    fprintf(f, "  </identification>\n");

    // Part list
    fprintf(f, "  <part-list>\n");
    fprintf(f, "    <score-part id=\"P1\">\n");
    fprintf(f, "      <part-name>%s</part-name>\n", track.name.c_str());
    fprintf(f, "      <part-abbreviation>Gtr</part-abbreviation>\n");
    fprintf(f, "      <score-instrument id=\"P1-I1\">\n");
    fprintf(f, "        <instrument-name>Electric Guitar</instrument-name>\n");
    fprintf(f, "      </score-instrument>\n");
    fprintf(f, "      <midi-device id=\"P1-I1\" port=\"1\"></midi-device>\n");
    fprintf(f, "      <midi-instrument id=\"P1-I1\">\n");
    fprintf(f, "        <midi-channel>1</midi-channel>\n");
    fprintf(f, "        <midi-program>25</midi-program>\n");
    fprintf(f, "        <volume>78.7402</volume>\n");
    fprintf(f, "        <pan>0</pan>\n");
    fprintf(f, "      </midi-instrument>\n");
    fprintf(f, "    </score-part>\n");
    fprintf(f, "  </part-list>\n");

    // Part
    fprintf(f, "  <part id=\"P1\">\n");

    // Group notes into measures (4/4 time)
    float measureDuration = beatDuration * 4.0f;
    int numMeasures = (int)ceilf(totalDuration / measureDuration);
    if (numMeasures < 1) numMeasures = 1;

    // String names for tab
    const char* stringNames[] = {"E", "A", "D", "G", "B", "E"};

    int noteIndex = 0;
    for (int m = 0; m < numMeasures; m++) {
        float measureStart = m * measureDuration;
        float measureEnd = measureStart + measureDuration;

        fprintf(f, "    <measure number=\"%d\">\n", m + 1);

        // Attributes on first measure
        if (m == 0) {
            fprintf(f, "      <attributes>\n");
            fprintf(f, "        <divisions>%d</divisions>\n", (int)divisions);
            fprintf(f, "        <key>\n");
            fprintf(f, "          <fifths>0</fifths>\n");
            fprintf(f, "          <mode>major</mode>\n");
            fprintf(f, "        </key>\n");
            fprintf(f, "        <time>\n");
            fprintf(f, "          <beats>4</beats>\n");
            fprintf(f, "          <beat-type>4</beat-type>\n");
            fprintf(f, "        </time>\n");
            fprintf(f, "        <clef>\n");
            fprintf(f, "          <sign>TAB</sign>\n");
            fprintf(f, "          <line>5</line>\n");
            fprintf(f, "        </clef>\n");
            fprintf(f, "        <staff-details>\n");
            fprintf(f, "          <staff-lines>6</staff-lines>\n");
            fprintf(f, "          <staff-tuning line=\"1\">\n");
            fprintf(f, "            <tuning-step>E</tuning-step>\n");
            fprintf(f, "            <tuning-octave>4</tuning-octave>\n");
            fprintf(f, "          </staff-tuning>\n");
            fprintf(f, "          <staff-tuning line=\"2\">\n");
            fprintf(f, "            <tuning-step>B</tuning-step>\n");
            fprintf(f, "            <tuning-octave>3</tuning-octave>\n");
            fprintf(f, "          </staff-tuning>\n");
            fprintf(f, "          <staff-tuning line=\"3\">\n");
            fprintf(f, "            <tuning-step>G</tuning-step>\n");
            fprintf(f, "            <tuning-octave>3</tuning-octave>\n");
            fprintf(f, "          </staff-tuning>\n");
            fprintf(f, "          <staff-tuning line=\"4\">\n");
            fprintf(f, "            <tuning-step>D</tuning-step>\n");
            fprintf(f, "            <tuning-octave>3</tuning-octave>\n");
            fprintf(f, "          </staff-tuning>\n");
            fprintf(f, "          <staff-tuning line=\"5\">\n");
            fprintf(f, "            <tuning-step>A</tuning-step>\n");
            fprintf(f, "            <tuning-octave>2</tuning-octave>\n");
            fprintf(f, "          </staff-tuning>\n");
            fprintf(f, "          <staff-tuning line=\"6\">\n");
            fprintf(f, "            <tuning-step>E</tuning-step>\n");
            fprintf(f, "            <tuning-octave>2</tuning-octave>\n");
            fprintf(f, "          </staff-tuning>\n");
            fprintf(f, "        </staff-details>\n");
            fprintf(f, "      </attributes>\n");

            // Direction for tempo
            fprintf(f, "      <direction placement=\"above\">\n");
            fprintf(f, "        <direction-type>\n");
            fprintf(f, "          <words default-y=\"20\" font-style=\"italic\">Quarter = %d</words>\n", (int)track.tempo);
            fprintf(f, "        </direction-type>\n");
            fprintf(f, "        <sound tempo=\"%.1f\"/>\n", track.tempo);
            fprintf(f, "      </direction>\n");
        }

        // Add notes in this measure
        while (noteIndex < (int)allNotes.size()) {
            const auto& pair = allNotes[noteIndex];
            const auto& note = pair.second;
            if (pair.first >= measureEnd) break; // not in this measure

            float noteStartInMeasure = pair.first - measureStart;
            float noteDur = note.duration;

            // Duration in quarters
            float quarters = noteDur / beatDuration;
            int durDiv = (int)(quarters * divisions + 0.5f);
            if (durDiv < 1) durDiv = 1;

            // Type
            const char* type = "eighth";
            if (quarters >= 4.0f) type = "whole";
            else if (quarters >= 2.0f) type = "half";
            else if (quarters >= 0.99f) type = "quarter";
            else if (quarters >= 0.49f) type = "eighth";
            else if (quarters >= 0.24f) type = "16th";
            else type = "32nd";

            int dots = 0;

            fprintf(f, "      <note>\n");

            // Pitch (use MIDI note for standard notation)
            if (note.midiNote > 0) {
                int step_idx = note.midiNote % 12;
                const char* steps[] = {"C", "C", "D", "D", "E", "F", "F", "G", "G", "A", "A", "B"};
                const char* step = steps[step_idx % 12];
                int alter = (step_idx == 1 || step_idx == 3 || step_idx == 6 || step_idx == 8 || step_idx == 10) ? 1 : 0;
                int octave = (note.midiNote / 12) - 1;
                fprintf(f, "        <pitch>\n");
                fprintf(f, "          <step>%s</step>\n", step);
                if (alter) fprintf(f, "          <alter>%d</alter>\n", alter);
                fprintf(f, "          <octave>%d</octave>\n", octave);
                fprintf(f, "        </pitch>\n");
            }

            fprintf(f, "        <duration>%d</duration>\n", durDiv);
            if (dots > 0) fprintf(f, "        <dot/>\n");
            fprintf(f, "        <voice>1</voice>\n");
            fprintf(f, "        <type>%s</type>\n", type);
            if (note.fret >= 0) {
                fprintf(f, "        <notations>\n");
                fprintf(f, "          <technical>\n");
                // Convert string number: our 0=highE=line1, MusicXML 1=highE=line1
                fprintf(f, "            <string>%d</string>\n", note.stringNum + 1);
                fprintf(f, "            <fret>%d</fret>\n", note.fret);
                fprintf(f, "          </technical>\n");
                fprintf(f, "        </notations>\n");
            }
            fprintf(f, "      </note>\n");

            noteIndex++;
        }

        // If no notes in this measure, add a rest
        if (noteIndex > 0) {
            int prevIdx = noteIndex - 1;
            if (prevIdx < (int)allNotes.size() && allNotes[prevIdx].first < measureStart) {
                // We started a note before this measure, need to track it
            }
        }

        // Add rest for empty measures
        bool hasNotesInMeasure = false;
        int checkIdx = noteIndex - 1;
        while (checkIdx >= 0) {
            const auto& p = allNotes[checkIdx];
            if (p.first >= measureStart && p.first < measureEnd) {
                hasNotesInMeasure = true;
                break;
            }
            if (p.first < measureStart) break;
            checkIdx--;
        }

        // Check using the processed notes
        int measureNoteStart = noteIndex;
        // Count how many notes were in this measure
        bool foundNote = false;
        for (int i = 0; i < noteIndex; i++) {
            if (allNotes[i].first >= measureStart && allNotes[i].first < measureEnd) {
                foundNote = true;
                break;
            }
        }
        if (!foundNote && noteIndex > 0) {
            // Add rest only if no notes found
            int restDur = (int)(4.0f * divisions + 0.5f);
            fprintf(f, "      <note>\n");
            fprintf(f, "        <rest/>\n");
            fprintf(f, "        <duration>%d</duration>\n", restDur);
            fprintf(f, "        <voice>1</voice>\n");
            fprintf(f, "        <type>whole</type>\n");
            fprintf(f, "      </note>\n");
        }

        // Barline on last measure
        if (m == numMeasures - 1) {
            fprintf(f, "      <barline location=\"final\">\n");
            fprintf(f, "        <bar-style>light-heavy</bar-style>\n");
            fprintf(f, "      </barline>\n");
        }

        fprintf(f, "    </measure>\n");
    }

    fprintf(f, "  </part>\n");
    fprintf(f, "</score-partwise>\n");

    fclose(f);
    return true;
}
