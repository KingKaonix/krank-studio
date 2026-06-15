#ifndef KRANK_MIDI_EXPORTER_H
#define KRANK_MIDI_EXPORTER_H

#include <cstdint>
#include <vector>
#include <string>
#include <cstdio>
#include <cmath>
#include "tablature.h"

class MidiExporter {
public:
    static bool exportToMidi(const TabTrack& track, const char* path);
    static bool exportToMusicXml(const TabTrack& track, const char* path);
    static bool exportToAbc(const TabTrack& track, const char* path);

private:
    static void writeVarLen(FILE* f, uint32_t value);
    static void writeU16(FILE* f, uint16_t value);
    static void writeU32(FILE* f, uint32_t value);
};

#endif
