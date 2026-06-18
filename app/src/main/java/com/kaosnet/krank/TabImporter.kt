package com.kaosnet.krank

import android.content.Context
import android.net.Uri
import java.io.InputStream
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Imports tablature files (MIDI, MusicXML) and converts to TabNoteData.
 */
object TabImporter {

    data class ImportResult(
        val notes: List<TabNoteData>,
        val tempo: Float = 120f,
        val title: String = "Imported Tab",
        val numMeasures: Int = 0
    )

    fun import(context: Context, uri: Uri): ImportResult? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size < 4) return null

                // Detect format by magic bytes
                val header = String(bytes.sliceArray(0..3), Charsets.US_ASCII)
                when {
                    header == "MThd" -> parseMidi(bytes)
                    bytes[0] == 0x3C.toByte() && bytes[1] == 0x3F.toByte() -> parseMusicXml(bytes)
                    bytes[0] == 0x3C.toByte() -> parseMusicXml(bytes)
                    bytes.size >= 30 && String(bytes.sliceArray(0..29), Charsets.US_ASCII).contains("FICHIER GUITAR PRO") -> Gp5Importer.import(bytes)
                    else -> null // unsupported format
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ── MIDI PARSER ──

    private data class MidiEvent(val delta: Int, val type: Int, val data1: Int, val data2: Int) {
        // type: 0x9=note_on, 0x8=note_off, 0xFF=meta, 0x51=set_tempo
    }

    private fun parseMidi(bytes: ByteArray): ImportResult? {
        var pos = 0

        // Read header
        if (readString(bytes, pos, 4) != "MThd") return null
        pos += 4
        val headerLen = read32be(bytes, pos); pos += 4
        val format = read16be(bytes, pos); pos += 2
        val numTracks = read16be(bytes, pos); pos += 2
        val division = read16be(bytes, pos); pos += 2
        val ticksPerQuarter = if (division and 0x8000 == 0) division else 480

        // Track names
        val trackNames = mutableListOf<String>()

        // Parse all tracks to collect events
        var tempo: Float = 120f
        val allEvents = mutableListOf<Pair<Long, Int>>() // (time_in_ticks, midiNote, velocity, is_note_on)

        for (t in 0 until numTracks) {
            if (pos + 8 > bytes.size) break
            if (readString(bytes, pos, 4) != "MTrk") break
            pos += 4
            val trackLen = read32be(bytes, pos); pos += 4
            val trackEnd = pos + trackLen

            var tickTime = 0L

            while (pos < trackEnd) {
                val delta = readVarLen(bytes, pos)
                pos += varLenSize(bytes, pos)
                tickTime += delta

                if (pos >= bytes.size) break
                val status = bytes[pos].toInt() and 0xFF

                if (status == 0xFF) {
                    // Meta event
                    pos++
                    val metaType = bytes[pos].toInt() and 0xFF; pos++
                    val metaLen = readVarLen(bytes, pos)
                    pos += varLenSize(bytes, pos)
                    when (metaType) {
                        0x03 -> {
                            // Track name
                            val name = if (pos + metaLen <= bytes.size) {
                                String(bytes.sliceArray(pos until pos + metaLen), Charsets.US_ASCII)
                            } else ""
                            trackNames.add(name)
                        }
                        0x51 -> {
                            // Set tempo (microseconds per quarter note)
                            if (metaLen >= 3 && pos + 3 <= bytes.size) {
                                val usPerQ = ((bytes[pos].toInt() and 0xFF) shl 16) or
                                        ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                                        (bytes[pos + 2].toInt() and 0xFF)
                                tempo = 60000000f / usPerQ
                            }
                        }
                    }
                    pos += metaLen
                } else if ((status and 0xF0) == 0x90) {
                    // Note on
                    val channel = status and 0x0F
                    val note = bytes[pos + 1].toInt() and 0xFF
                    val velocity = bytes[pos + 2].toInt() and 0xFF
                    pos += 3
                    if (velocity > 0) {
                        allEvents.add(Pair(tickTime * 100L, note))
                    } else {
                        allEvents.add(Pair(tickTime * 100L or 1, note))
                    }
                } else if ((status and 0xF0) == 0x80) {
                    // Note off
                    val note = bytes[pos + 1].toInt() and 0xFF
                    pos += 3
                    allEvents.add(Pair(tickTime * 100L or 1, note))
                } else if ((status and 0xF0) == 0xB0) {
                    // Controller - skip 2 bytes
                    pos += 3
                } else if ((status and 0xF0) == 0xC0) {
                    // Program change - skip 1 byte
                    pos += 2
                } else if ((status and 0xF0) == 0xE0) {
                    // Pitch bend - skip 2 bytes
                    pos += 3
                } else {
                    // Running status or unknown
                    pos++
                }
            }
        }

        // Convert to TabNoteData
        val secondsPerTick = (60.0 / tempo) / ticksPerQuarter
        val noteMap = mutableMapOf<Int, MutableList<Long>>()
        val noteOffMap = mutableMapOf<Int, MutableList<Long>>()

        for ((timeAndFlag, note) in allEvents) {
            val isOff = (timeAndFlag and 1) == 1L
            val time = timeAndFlag shr 1

            if (isOff) {
                noteOffMap.getOrPut(note) { mutableListOf() }.add(time)
            } else {
                noteMap.getOrPut(note) { mutableListOf() }.add(time)
            }
        }

        val notes = mutableListOf<TabNoteData>()
        for ((midiNote, onTimes) in noteMap) {
            val offTimes = noteOffMap[midiNote] ?: emptyList()
            for ((i, onTick) in onTimes.withIndex()) {
                val offTick = if (i < offTimes.size) offTimes[i] else onTick + ticksPerQuarter
                val startTime = (onTick * secondsPerTick).toFloat()
                val duration = ((offTick - onTick) * secondsPerTick).toFloat().coerceAtLeast(0.05f)

                // Approximate string/fret from MIDI note
                val midiFloat = midiNote.toFloat()
                val stringFreqs = floatArrayOf(329.63f, 246.94f, 196.0f, 146.83f, 110.0f, 82.41f)

                var bestString = 2
                var bestFret = 5
                var bestDiff = 999f
                for (s in 0..5) {
                    val stringMidi = 69f + 12f * kotlin.math.log2(stringFreqs[s] / 440f)
                    val fret = midiFloat - stringMidi
                    if (fret >= 0 && fret < 25) {
                        val diff = kotlin.math.abs(fret - fret.roundToInt())
                        if (diff < bestDiff) {
                            bestDiff = diff
                            bestString = s
                            bestFret = fret.roundToInt()
                        }
                    }
                }

                notes.add(TabNoteData(bestString, bestFret, startTime, duration))
            }
        }

        notes.sortBy { it.startTime }
        val numMeasures = if (notes.isNotEmpty()) {
            val totalBeats = (notes.maxOf { it.startTime + it.duration } / (60f / tempo))
            (totalBeats / 4f).toInt().coerceAtLeast(1)
        } else 0

        return ImportResult(
            notes = notes,
            tempo = tempo,
            title = trackNames.firstOrNull() ?: "Imported MIDI",
            numMeasures = numMeasures
        )
    }

    // ── MUSICXML PARSER ──

    private fun parseMusicXml(bytes: ByteArray): ImportResult? {
        val xml = String(bytes, Charsets.UTF_8)
        val notes = mutableListOf<TabNoteData>()

        // Extract tempo
        var tempo = 120f
        val tempoRegex = Regex("""<sound tempo="([\d.]+)"""")
        tempoRegex.find(xml)?.let { tempo = it.groupValues[1].toFloatOrNull() ?: 120f }

        // Extract divisions
        var divisions = 1
        val divRegex = Regex("""<divisions>(\d+)</divisions>""")
        divRegex.find(xml)?.let { divisions = it.groupValues[1].toIntOrNull() ?: 1 }

        // Extract title
        var title = "Imported Tab"
        val titleRegex = Regex("""<movement-title>([^<]+)</movement-title>""")
        titleRegex.find(xml)?.let { title = it.groupValues[1] }

        // Extract notes with fret/string info
        // MusicXML format: <note>...<string>N</string><fret>N</fret>...<duration>N</duration>...</note>
        var currentTime = 0f
        val measureDuration = 4f / (tempo / 60f) // seconds per measure (4/4)

        // Parse measure by measure
        val measureRegex = Regex("""<measure number="(\d+)">(.*?)</measure>""", RegexOption.DOT_MATCHES_ALL)
        var measureIdx = 0
        for (measureMatch in measureRegex.findAll(xml)) {
            val measureXml = measureMatch.groupValues[2]
            val measureStart = measureIdx * measureDuration

            // Parse attributes (time signature changes, etc)
            var beats = 4
            var beatType = 4
            val beatsRegex = Regex("""<beats>(\d+)</beats>""")
            beatsRegex.find(measureXml)?.let { beats = it.groupValues[1].toIntOrNull() ?: 4 }
            val beatTypeRegex = Regex("""<beat-type>(\d+)</beat-type>""")
            beatTypeRegex.find(measureXml)?.let { beatType = it.groupValues[1].toIntOrNull() ?: 4 }

            val measDiv = measureDuration / (beats * divisions)

            // Parse notes in this measure
            val noteRegex = Regex("""<note>(.*?)</note>""", RegexOption.DOT_MATCHES_ALL)
            for (noteMatch in noteRegex.findAll(measureXml)) {
                val noteXml = noteMatch.groupValues[1]

                // Check for rest
                if (noteXml.contains("<rest/>") || noteXml.contains("<rest>")) {
                    val durReg = Regex("""<duration>(\d+)</duration>""")
                    durReg.find(noteXml)?.let {
                        val dur = it.groupValues[1].toIntOrNull() ?: 1
                        currentTime += dur * measDiv
                    }
                    continue
                }

                val durReg = Regex("""<duration>(\d+)</duration>""")
                val dur = durReg.find(noteXml)?.let { it.groupValues[1].toIntOrNull() ?: 1 } ?: 1

                val stringReg = Regex("""<string>(\d+)</string>""")
                val fretReg = Regex("""<fret>(\d+)</fret>""")

                val stringNum = stringReg.find(noteXml)?.let { it.groupValues[1].toIntOrNull()?.let { s -> 6 - s } } ?: -1
                val fretNum = fretReg.find(noteXml)?.let { it.groupValues[1].toIntOrNull() } ?: 0

                if (stringNum >= 0) {
                    val startTime = currentTime
                    val duration = dur * measDiv
                    notes.add(TabNoteData(stringNum, fretNum, startTime, duration))
                }

                currentTime += dur * measDiv
            }
            measureIdx++
        }

        if (notes.isEmpty()) return null

        notes.sortBy { it.startTime }
        return ImportResult(
            notes = notes,
            tempo = tempo,
            title = title,
            numMeasures = measureIdx
        )
    }

    // ── MIDI UTILITIES ──

    private fun readString(bytes: ByteArray, pos: Int, len: Int): String {
        if (pos + len > bytes.size) return ""
        return String(bytes.sliceArray(pos until pos + len), Charsets.US_ASCII)
    }

    private fun read16be(bytes: ByteArray, pos: Int): Int {
        if (pos + 2 > bytes.size) return 0
        return ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
    }

    private fun read32be(bytes: ByteArray, pos: Int): Int {
        if (pos + 4 > bytes.size) return 0
        return ((bytes[pos].toInt() and 0xFF) shl 24) or
                ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
    }

    private fun readVarLen(bytes: ByteArray, start: Int): Int {
        var value = 0
        var pos = start
        while (pos < bytes.size) {
            val b = bytes[pos].toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F)
            pos++
            if (b and 0x80 == 0) break
        }
        return value
    }

    private fun varLenSize(bytes: ByteArray, start: Int): Int {
        var pos = start
        while (pos < bytes.size) {
            if (bytes[pos].toInt() and 0x80 == 0) return pos - start + 1
            pos++
        }
        return pos - start
    }
}
