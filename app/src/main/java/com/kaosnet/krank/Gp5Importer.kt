package com.kaosnet.krank

import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * Parses Guitar Pro 5 (.gp5) binary files into TabNoteData.
 * Based on the reverse-engineered GP5 format specification.
 */
object Gp5Importer {

    fun import(bytes: ByteArray): TabImporter.ImportResult? {
        return try {
            val input = DataInputStream(ByteArrayInputStream(bytes))
            parse(input)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private data class Gp5Header(
        val version: String,
        val title: String,
        val subtitle: String,
        val artist: String,
        val album: String
    )

    private data class Gp5Track(
        val name: String,
        val strings: IntArray,    // MIDI note numbers for each string
        val tuning: String        // tuning name derived from strings
    )

    private fun parse(input: DataInputStream): TabImporter.ImportResult? {
        // ── HEADER ──
        val magic = ByteArray(30)
        input.readFully(magic)
        val magicStr = String(magic, Charsets.US_ASCII).trim()
        if (!magicStr.contains("FICHIER GUITAR PRO")) {
            return null // not a GP5 file
        }

        // Skip \r\n (2 bytes)
        input.readByte(); input.readByte()

        // Version byte
        val versionByte = input.readUnsignedByte()

        // ── SCORE INFORMATION ──
        val title = readGpString(input)
        val subtitle = readGpString(input)
        val artist = readGpString(input)
        val album = readGpString(input)
        val composer = readGpString(input)
        val copyright = readGpString(input)
        val transcriber = readGpString(input)
        val instructions = readGpString(input)
        // Notice lines (3)
        readGpString(input); readGpString(input); readGpString(input)

        // Triplet feeling
        input.readUnsignedByte()

        // ── LYRICS ──
        val hasLyrics = input.readUnsignedByte()
        if (hasLyrics != 0) {
            readGpString(input) // lyrics
            input.readInt()     // language
            input.readByte()    // beginning of measure
            input.readInt()     // offset
        }

        // ── SYSTEMS ──
        val tempo = input.readInt().coerceAtLeast(30).toFloat()
        val keySig = input.readByte()
        val octave = input.readByte()
        val timeNum = input.readUnsignedByte()
        val timeDenomExp = input.readUnsignedByte()
        val timeDenom = 1 shl timeDenomExp // 4/4 -> 4, 6/8 -> 8, etc.
        val numMeasures = input.readUnsignedByte()
        val numTracks = input.readUnsignedByte()

        // ── TRACKS ──
        val tracks = mutableListOf<Gp5Track>()
        for (t in 0 until numTracks) {
            val trackName = readGpStringInt(input)
            val numStrings = input.readInt()

            val tuning = IntArray(7) // GP5 always stores 7 tuning ints
            for (i in 0 until 7) {
                tuning[i] = input.readInt()
            }

            val port = input.readInt()
            val channel = input.readInt()
            val channelEffects = input.readInt()
            val fretCount = input.readInt()
            val capo = input.readInt()
            val colorR = input.readUnsignedByte()
            val colorG = input.readUnsignedByte()
            val colorB = input.readUnsignedByte()

            val showTuning = input.readByte()
            val flags = input.readByte()
            val instrument = input.readUnsignedByte()

            val shortName = readGpString(input)
            val longNameBytes = ByteArray(35)
            input.readFully(longNameBytes)

            val bank = input.readUnsignedByte()
            val volume = input.readUnsignedByte()
            val pan = input.readUnsignedByte()
            val chorus = input.readUnsignedByte()
            val reverb = input.readUnsignedByte()
            val phaser = input.readUnsignedByte()
            val tremolo = input.readUnsignedByte()

            // 8-velocities
            val velocities = input.readUnsignedByte()

            // Store track
            val actualStrings = numStrings.coerceIn(1, 6)
            val activeTuning = IntArray(actualStrings) { i ->
                if (i < tuning.size) tuning[i] else 0
            }

            tracks.add(Gp5Track(
                name = trackName.ifEmpty { "Track ${t + 1}" },
                strings = activeTuning,
                tuning = deriveTuningName(activeTuning)
            ))
        }

        // ── MEASURES ──
        val tickToSecond = 60.0 / (tempo * 120) // 120 ticks per quarter, tempo in BPM

        val notes = mutableListOf<TabNoteData>()
        val stringCounts = tracks.map { it.strings.size }

        // GP5 measure data: for each measure, for each track
        for (trackIdx in 0 until numTracks) {
            var absoluteTick = 0L
            for (measureIdx in 0 until numMeasures) {
                val result = readMeasure(input, trackIdx, tickToSecond, absoluteTick, stringCounts.getOrElse(trackIdx) { 6 })
                notes.addAll(result.notes)
                absoluteTick = result.nextAbsoluteTick
            }
        }

        if (notes.isEmpty()) return null

        notes.sortBy { it.startTime }
        return TabImporter.ImportResult(
            notes = notes,
            tempo = tempo,
            title = title.ifEmpty { "Imported GP5" },
            numMeasures = numMeasures
        )
    }

    private data class MeasureReadResult(
        val notes: List<TabNoteData>,
        val nextAbsoluteTick: Long
    )

    private fun readMeasure(
        input: DataInputStream,
        trackIdx: Int,
        tickToSecond: Double,
        absoluteTickStart: Long,
        numStrings: Int
    ): MeasureReadResult {
        val notes = mutableListOf<TabNoteData>()

        // Measure header flags
        val headerFlags = input.readUnsignedByte()

        // Time signature change
        if (headerFlags and 0x01 != 0) {
            input.readUnsignedByte() // numerator
            input.readUnsignedByte() // denominator exponent
            input.readUnsignedByte() // beats per measure display
        }

        // Key signature change
        if (headerFlags and 0x02 != 0) {
            input.readByte() // key
            input.readByte() // mode
        }

        // Beginning of repeat
        if (headerFlags and 0x04 != 0) {
            input.readByte() // repeat count
        }

        // End of repeat
        if (headerFlags and 0x08 != 0) {
            input.readByte() // repeat count
        }

        // Number of beats flags
        val numBeatsFlag = input.readUnsignedByte()
        if (numBeatsFlag == 0) {
            // Empty measure, still advances one measure worth of ticks
            return MeasureReadResult(notes, absoluteTickStart + 480) // default 480 ticks per measure
        }

        // Number of beats (variable length)
        val numBeats = readVarLen(input, numBeatsFlag)
        var measureTick = 0L

        for (beatIdx in 0 until numBeats) {
            val beatResult = readBeat(input, trackIdx, tickToSecond, absoluteTickStart + measureTick, numStrings)
            notes.addAll(beatResult.notes)
            measureTick += beatResult.durationTicks.coerceAtLeast(60)
        }

        val nextAbsoluteTick = absoluteTickStart + measureTick.coerceAtLeast(120)
        return MeasureReadResult(notes, nextAbsoluteTick)
    }

    private data class BeatReadResult(
        val notes: List<TabNoteData>,
        val durationTicks: Long
    )

    private fun readBeat(
        input: DataInputStream,
        trackIdx: Int,
        tickToSecond: Double,
        absoluteTick: Long,
        numStrings: Int
    ): BeatReadResult {
        val notes = mutableListOf<TabNoteData>()

        // Beat flags
        val beatFlags = input.readUnsignedByte()

        // Check if rest
        val isRest = (beatFlags and 0x20) != 0
        val hasNotes = (beatFlags and 0x04) != 0

        // Duration type
        val durationType = input.readUnsignedByte()

        // Convert GP duration to ticks
        var durationTicks = gpDurationToTicks(durationType)

        // Dotted
        if (beatFlags and 0x08 != 0) {
            input.readUnsignedByte() // dotted flag
            durationTicks = (durationTicks * 1.5).toLong()
        }

        // Beat effects
        if (beatFlags and 0x80 != 0) {
            readBeatEffects(input)
        }

        // Chord diagram
        if (beatFlags and 0x40 != 0) {
            readChordDiagram(input)
        }

        // Number of notes in this beat
        val numNotes = input.readUnsignedByte()

        // If rest, skip note details but count them
        if (isRest) {
            for (n in 0 until numNotes) {
                // Notes are still written in the file but with rest beat flag
                readNote(input)
            }
            return BeatReadResult(emptyList(), durationTicks)
        }

        // Read each note
        for (n in 0 until numNotes) {
            val noteResult = readNoteWithPosition(input, absoluteTick, durationTicks.toFloat() * tickToSecond.toFloat(), numStrings)
            if (noteResult != null) {
                notes.add(noteResult)
            }
        }

        return BeatReadResult(notes, durationTicks)
    }

    private fun readNoteWithPosition(
        input: DataInputStream,
        absoluteTick: Long,
        durationSeconds: Float,
        numStrings: Int
    ): TabNoteData? {
        val noteFlags = input.readUnsignedByte()

        val isTie = (noteFlags and 0x01) != 0
        val isDead = (noteFlags and 0x02) != 0
        val isGhost = (noteFlags and 0x04) != 0
        val hasEffects = (noteFlags and 0x10) != 0

        // String number (0 = high E, 5 = low E in GP5)
        val stringNum = input.readUnsignedByte()

        // Fret number
        val fret = input.readUnsignedByte()

        // Effects
        if (hasEffects) {
            readNoteEffects(input)
        }

        // Dynamic
        val dynamic = input.readUnsignedByte()

        // Fret type
        val fretType = input.readUnsignedByte()

        // Duration in ticks (2 bytes big-endian)
        val durHi = input.readUnsignedByte()
        val durLo = input.readUnsignedByte()
        val noteTicks = (durHi shl 8) or durLo

        // Convert GP string numbering (0=high E) to our system (0=high E)
        // Our system: 0=high e, 5=low E (same as GP5)
        if (stringNum < numStrings && !isDead && !isTie) {
            val startTime = (absoluteTick * tickToSecond).toFloat()
            return TabNoteData(
                stringNum = stringNum.coerceIn(0, 5),
                fret = fret.coerceIn(0, 24),
                startTime = startTime,
                duration = durationSeconds
            )
        }

        return null
    }

    private fun readNote(input: DataInputStream) {
        val noteFlags = input.readUnsignedByte()
        input.readUnsignedByte() // string
        input.readUnsignedByte() // fret
        if (noteFlags and 0x10 != 0) readNoteEffects(input)
        input.readUnsignedByte() // dynamic
        input.readUnsignedByte() // fret type
        input.readUnsignedByte() // duration hi
        input.readUnsignedByte() // duration lo
    }

    private fun readBeatEffects(input: DataInputStream) {
        val effectsFlags = input.readUnsignedByte()
        if (effectsFlags and 0x20 != 0) {
            // Bend
            input.readUnsignedByte() // bend type
            input.readUnsignedByte() // bend value
            input.readUnsignedByte() // bend point count or similar
        }
        if (effectsFlags and 0x40 != 0) {
            // Grace note
            input.readUnsignedByte() // fret
            input.readUnsignedByte() // dynamic
            input.readByte() // transition
            input.readUnsignedByte() // duration
        }
    }

    private fun readNoteEffects(input: DataInputStream) {
        val effectsFlags = input.readUnsignedByte()
        if (effectsFlags and 0x01 != 0) {
            // Bend
            val bendType = input.readUnsignedByte()
            val bendValue = input.readUnsignedByte()
            val numPoints = input.readUnsignedByte()
            for (i in 0 until numPoints) {
                input.readInt() // position
                input.readInt() // value
            }
        }
        if (effectsFlags and 0x02 != 0) {
            // Harmonic
            input.readByte() // harmonic type
        }
        if (effectsFlags and 0x04 != 0) {
            // Grace note
            input.readByte() // fret
            input.readByte() // dynamic
            input.readByte() // transition
            input.readByte() // duration
        }
        // Other effects can be skipped for basic reading
    }

    private fun readChordDiagram(input: DataInputStream) {
        val diagramByte = input.readByte()
        if (diagramByte.toInt() == 0) return
        // Chord name
        val name = readGpString(input)
        // Fret positions
        val frets = IntArray(7)
        for (i in 0 until 7) frets[i] = input.readByte().toInt()
        // Number of barre frets
        val barreFrets = IntArray(5)
        for (i in 0 until 5) barreFrets[i] = input.readByte().toInt()
        // Omissions, sharps etc
        val omission = input.readByte().toInt()
        val sharp = input.readByte().toInt()
        val root = input.readByte().toInt()
        val type = input.readByte().toInt()
        val extension = input.readByte().toInt()
        val bassNote = input.readByte().toInt()
        val tonality = input.readByte().toInt()
        val addInfo = input.readByte().toInt()
        val fifth = input.readByte().toInt()
        val ninth = input.readByte().toInt()
        val eleventh = input.readByte().toInt()
        // Color
        input.readByte(); input.readByte(); input.readByte()
    }

    // ── HELPERS ──

    private fun readGpString(input: DataInputStream): String {
        val len = input.readUnsignedByte()
        if (len == 0) return ""
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readGpStringInt(input: DataInputStream): String {
        val len = input.readInt()
        if (len == 0) return ""
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readVarLen(input: DataInputStream, firstByte: Int): Int {
        var value = firstByte and 0x1F // top 3 bits are flags
        if (firstByte and 0x20 != 0) {
            value = (value shl 7) or (input.readUnsignedByte() and 0x7F)
            if (firstByte and 0x40 != 0) {
                value = (value shl 7) or (input.readUnsignedByte() and 0x7F)
                if (firstByte and 0x80 != 0) {
                    value = (value shl 7) or (input.readUnsignedByte() and 0x7F)
                }
            }
        }
        return value
    }

    private fun gpDurationToTicks(durationType: Int): Long {
        // GP5 duration types: -2: whole, -1: half, 1: whole, 2: half, 3: quarter, 4: eighth, etc.
        // Actually: 1=whole, 2=half, 3=quarter, 4=eighth, 5=16th, 6=32nd
        return when (durationType) {
            1 -> 480L  // whole
            2 -> 240L  // half
            3 -> 120L  // quarter
            4 -> 60L   // eighth
            5 -> 30L   // 16th
            6 -> 15L   // 32nd
            else -> 120L // default to quarter
        }
    }

    private fun deriveTuningName(strings: IntArray): String {
        return when {
            strings.contentEquals(intArrayOf(40, 45, 50, 55, 59, 64)) -> "Standard"
            strings.contentEquals(intArrayOf(38, 45, 50, 55, 59, 64)) -> "Drop D"
            strings.contentEquals(intArrayOf(36, 43, 50, 55, 59, 64)) -> "Drop C"
            strings.contentEquals(intArrayOf(42, 47, 52, 57, 62, 67)) -> "Open D"
            strings.contentEquals(intArrayOf(40, 45, 50, 55, 60, 64)) -> "Open G"
            strings.contentEquals(intArrayOf(40, 45, 50, 56, 59, 64)) -> "Open E"
            strings.contentEquals(intArrayOf(38, 43, 48, 53, 57, 62)) -> "Half-Step Down"
            strings.contentEquals(intArrayOf(36, 41, 46, 51, 55, 60)) -> "Full-Step Down"
            else -> "Custom"
        }
    }
}
