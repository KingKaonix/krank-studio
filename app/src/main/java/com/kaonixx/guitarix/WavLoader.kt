import android.net.Uri
package com.kaonixx.guitarix

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.InputStream

object WavLoader {

    data class WavResult(
        val sampleRate: Int,
        val numChannels: Int,
        val bitsPerSample: Int,
        val samples: FloatArray
    )

    fun load(context: Context, uri: Uri): WavResult? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(12)
                if (stream.read(header) < 12) return null
                stream.close()

                val riff = String(header.sliceArray(0..3), Charsets.US_ASCII)
                if (riff == "RIFF") {
                    // Try WAV parse
                    context.contentResolver.openInputStream(uri)?.use { s -> parseWav(s) }
                } else {
                    // Use MediaExtractor for MP3/AAC/OGG etc.
                    decodeWithMediaExtractor(context, uri)
                }
            } ?: decodeWithMediaExtractor(context, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to MediaExtractor
            try { decodeWithMediaExtractor(context, uri) } catch (e2: Exception) { null }
        }
    }

    private fun decodeWithMediaExtractor(context: Context, uri: Uri): WavResult? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            return null
        }

        // Find audio track
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrackIndex = i
                break
            }
        }
        if (audioTrackIndex < 0) return null

        extractor.selectTrack(audioTrackIndex)
        val format = extractor.getTrackFormat(audioTrackIndex)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val numChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

        // Decode all audio to PCM float
        val allSamples = mutableListOf<Float>()
        val buffer = ByteBuffer.allocateDirect(65536)
        buffer.order(ByteOrder.nativeOrder())

        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            buffer.position(0)
            buffer.limit(sampleSize)

            // Convert bytes to float samples based on format
            val bytes = ByteArray(sampleSize)
            buffer.get(bytes)

            // Assume 16-bit PCM from decoder (most Android decoders output 16-bit)
            val numSamples = sampleSize / 2
            for (i in 0 until numSamples) {
                val lo = bytes[i * 2].toInt() and 0xFF
                val hi = bytes[i * 2 + 1].toInt()
                val sample = (lo or (hi shl 8)).toShort().toFloat() / 32768f
                allSamples.add(sample.coerceIn(-1f, 1f))
            }

            extractor.advance()
        }

        extractor.release()

        if (allSamples.isEmpty()) return null

        return WavResult(
            sampleRate = sampleRate,
            numChannels = numChannels,
            bitsPerSample = 16,
            samples = allSamples.toFloatArray()
        )
    }

    private fun parseWav(stream: InputStream): WavResult? {
        val riff = readString(stream, 4)
        if (riff != "RIFF") return null
        readIntLE(stream)
        val wave = readString(stream, 4)
        if (wave != "WAVE") return null

        var sampleRate = 0
        var numChannels = 0
        var bitsPerSample = 0
        var pcmData: ByteArray? = null

        while (true) {
            val chunkId = peekString(stream, 4) ?: break
            when (chunkId) {
                "fmt " -> {
                    readString(stream, 4)
                    val fmtSize = readIntLE(stream)
                    val audioFormat = readShortLE(stream).toInt() and 0xFFFF
                    numChannels = readShortLE(stream).toInt() and 0xFFFF
                    sampleRate = readIntLE(stream)
                    readIntLE(stream)
                    readShortLE(stream)
                    bitsPerSample = readShortLE(stream).toInt() and 0xFFFF
                    if (fmtSize > 16) stream.skip((fmtSize - 16).toLong())
                }
                "data" -> {
                    readString(stream, 4)
                    val dataSize = readIntLE(stream)
                    pcmData = ByteArray(dataSize)
                    var offset = 0
                    while (offset < dataSize) {
                        val read = stream.read(pcmData, offset, dataSize - offset)
                        if (read == -1) break
                        offset += read
                    }
                    break
                }
                else -> {
                    val chunkSize = readIntLE(stream)
                    stream.skip(chunkSize.toLong())
                }
            }
        }

        val data = pcmData ?: return null
        if (sampleRate == 0 || numChannels == 0 || bitsPerSample == 0) return null

        val bytesPerSample = bitsPerSample / 8
        val numSamples = data.size / bytesPerSample
        val samples = FloatArray(numSamples)

        when (bitsPerSample) {
            16 -> {
                for (i in 0 until numSamples) {
                    val sample = (data[i * 2].toInt() and 0xFF) or (data[i * 2 + 1].toInt() shl 8)
                    samples[i] = sample.toShort().toFloat() / 32768f
                }
            }
            24 -> {
                for (i in 0 until numSamples) {
                    val sample = (data[i * 3].toInt() and 0xFF) or
                            ((data[i * 3 + 1].toInt() and 0xFF) shl 8) or
                            (data[i * 3 + 2].toInt() shl 16)
                    samples[i] = sample.toFloat() / 8388608f
                }
            }
            32 -> {
                for (i in 0 until numSamples) {
                    val sample = (data[i * 4].toInt() and 0xFF) or
                            ((data[i * 4 + 1].toInt() and 0xFF) shl 8) or
                            ((data[i * 4 + 2].toInt() and 0xFF) shl 16) or
                            ((data[i * 4 + 3].toInt() and 0xFF) shl 24)
                    samples[i] = sample.toFloat() / 2147483648f
                }
            }
            8 -> {
                for (i in 0 until numSamples) {
                    samples[i] = ((data[i].toInt() and 0xFF) - 128) / 128f
                }
            }
        }

        return WavResult(sampleRate, numChannels, bitsPerSample, samples)
    }

    private fun readString(stream: InputStream, len: Int): String {
        val bytes = ByteArray(len)
        stream.read(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun peekString(stream: InputStream, len: Int): String? {
        stream.mark(len + 1)
        val bytes = ByteArray(len)
        val read = stream.read(bytes)
        if (read < len) { stream.reset(); return null }
        stream.reset()
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readIntLE(stream: InputStream): Int {
        return (stream.read() and 0xFF) or
                ((stream.read() and 0xFF) shl 8) or
                ((stream.read() and 0xFF) shl 16) or
                (stream.read() shl 24)
    }

    private fun readShortLE(stream: InputStream): Short {
        return ((stream.read() and 0xFF) or ((stream.read() and 0xFF) shl 8)).toShort()
    }
}
