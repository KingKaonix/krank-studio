package com.kaosnet.krank

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.pow

/**
 * Generates synthetic guitar tones from tab data and plays them
 * mixed with the original backing audio track.
 */
class TabPlayer {

    companion object {
        private const val PLAYBACK_SAMPLE_RATE = 44100
        private const val BUFFER_MS = 50
        // Harmonics for a more natural guitar tone
        private val GUITAR_HARMONICS = floatArrayOf(1.0f, 0.5f, 0.3f, 0.15f, 0.08f, 0.04f)
        private val GUITAR_HARMONIC_RATIOS = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f)
    }

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val currentTimeMs: Long = 0,
        val totalDurationMs: Long = 0,
        val progress: Float = 0f
    )

    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null
    @Volatile private var playing = false
    @Volatile private var paused = false
    @Volatile private var seekPositionMs: Long = -1
    private val mainHandler = Handler(Looper.getMainLooper())

    private var notes: List<TabNoteData> = emptyList()
    private var backingSampleRate: Int = PLAYBACK_SAMPLE_RATE
    @Volatile private var renderedPositionMs: Long = 0
    private var stateListener: ((PlaybackState) -> Unit)? = null

    fun setStateListener(listener: (PlaybackState) -> Unit) {
        stateListener = listener
    }

    fun load(notes: List<TabNoteData>) {
        stop()
        this.notes = notes.sortedBy { it.startTime }
        renderedPositionMs = 0
        seekPositionMs = -1

        val totalMs = if (notes.isNotEmpty()) {
            (notes.maxOf { it.startTime + it.duration } * 1000).toLong() + 500
        } else 0L

        mainHandler.post {
            stateListener?.invoke(PlaybackState(totalDurationMs = totalMs))
        }
    }

    fun play() {
        if (playing) return
        if (notes.isEmpty()) return
        playing = true
        paused = false

        playThread = Thread({ playbackLoop() }, "tab-player")
        playThread?.start()
    }

    fun pause() {
        paused = true
        playing = false
        audioTrack?.stop()
        audioTrack?.flush()
    }

    fun stop() {
        paused = false
        playing = false
        seekPositionMs = -1
        renderedPositionMs = 0
        audioTrack?.stop()
        audioTrack?.flush()
        audioTrack?.release()
        audioTrack = null
        playThread?.join(200)
        playThread = null
        val totalMs = if (notes.isNotEmpty()) {
            (notes.maxOf { it.startTime + it.duration } * 1000).toLong() + 500
        } else 0L
        mainHandler.post {
            stateListener?.invoke(PlaybackState(currentTimeMs = 0, totalDurationMs = totalMs))
        }
    }

    fun seekTo(positionMs: Long) {
        seekPositionMs = positionMs
        if (!playing) {
            renderedPositionMs = positionMs
            val totalMs = if (notes.isNotEmpty()) {
                (notes.maxOf { it.startTime + it.duration } * 1000).toLong() + 500
            } else 0L
            mainHandler.post {
                stateListener?.invoke(PlaybackState(
                    currentTimeMs = positionMs,
                    totalDurationMs = totalMs,
                    progress = if (totalMs > 0) positionMs.toFloat() / totalMs.toFloat() else 0f
                ))
            }
        }
    }

    fun isPlaying(): Boolean = playing && !paused

    fun release() {
        stop()
        stateListener = null
    }

    /** Generate a short tone buffer for one guitar note */
    private fun generateNoteBuffer(fret: Int, stringNum: Int, durationMs: Int): FloatArray {
        val stringFreqs = floatArrayOf(329.63f, 246.94f, 196.0f, 146.83f, 110.0f, 82.41f)
        val baseFreq = if (stringNum in 0..5) {
            stringFreqs[stringNum] * Math.pow(2.0, fret / 12.0).toFloat()
        } else 440f

        val numSamples = (PLAYBACK_SAMPLE_RATE * durationMs / 1000).coerceIn(512, PLAYBACK_SAMPLE_RATE * 2)
        val buffer = FloatArray(numSamples)
        val attackSamples = (PLAYBACK_SAMPLE_RATE * 0.005f).toInt().coerceAtMost(numSamples / 4)
        val decaySamples = (PLAYBACK_SAMPLE_RATE * 0.3f).toInt().coerceAtMost(numSamples / 2)

        for (i in 0 until numSamples) {
            var sample = 0f
            for (h in GUITAR_HARMONICS.indices) {
                val freq = baseFreq * GUITAR_HARMONIC_RATIOS[h]
                val harmonicAmp = GUITAR_HARMONICS[h] * (1.0f / (h + 1).toFloat().pow(0.5f))
                sample += sin(2.0 * PI * freq * i / PLAYBACK_SAMPLE_RATE).toFloat() * harmonicAmp
            }
            val envelope = when {
                i < attackSamples -> i.toFloat() / attackSamples
                i < attackSamples + decaySamples -> {
                    val decayPos = (i - attackSamples).toFloat() / decaySamples
                    1.0f - decayPos * 0.7f
                }
                else -> 0.3f
            }
            buffer[i] = sample * envelope * 0.3f
        }
        return buffer
    }

    /** Mix all notes into one stereo buffer at PLAYBACK_SAMPLE_RATE */
    private fun generateMixedTones(totalSamples: Int): FloatArray {
        val buf = FloatArray(totalSamples)
        for (note in notes) {
            val startSample = (note.startTime * PLAYBACK_SAMPLE_RATE).toInt()
            if (startSample >= totalSamples) continue
            val durationMs = (note.duration * 1000).toInt().coerceIn(50, 2000)
            val noteBuf = generateNoteBuffer(note.fret, note.stringNum, durationMs)
            val endPos = minOf(startSample + noteBuf.size, totalSamples)
            for (i in 0 until (endPos - startSample)) {
                buf[startSample + i] = (buf[startSample + i] + noteBuf[i]).coerceIn(-1f, 1f)
            }
        }
        return buf
    }

    /** Resample backing audio to PLAYBACK_SAMPLE_RATE using linear interpolation */
    private fun resampleAudio(input: FloatArray, inputSampleRate: Int, targetLength: Int): FloatArray {
        if (inputSampleRate == PLAYBACK_SAMPLE_RATE) {
            return if (input.size >= targetLength) input.copyOf(targetLength)
            else input.copyOf(targetLength).also { it.fill(0f, input.size, targetLength) }
        }
        val ratio = inputSampleRate.toDouble() / PLAYBACK_SAMPLE_RATE.toDouble()
        val output = FloatArray(targetLength)
        for (i in 0 until targetLength) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            val frac = (srcPos - srcIdx).toFloat()
            if (srcIdx < input.size - 1) {
                output[i] = input[srcIdx] * (1f - frac) + input[srcIdx + 1] * frac
            } else if (srcIdx < input.size) {
                output[i] = input[srcIdx]
            }
        }
        return output
    }

    private fun playbackLoop() {
        // Calculate total duration of the tab
        val totalMs = if (notes.isNotEmpty()) {
            (notes.maxOf { it.startTime + it.duration } * 1000).toLong() + 500
        } else {
            mainHandler.post { stateListener?.invoke(PlaybackState()) }
            playing = false
            return
        }

        val totalSamples = (PLAYBACK_SAMPLE_RATE * totalMs / 1000).toInt() + PLAYBACK_SAMPLE_RATE

        // Generate the guitar tones
        val toneBuffer = generateMixedTones(totalSamples)

        // Get backing audio from wherever it was stored
        val backingRef = backingRef_
        val backingSr = backingSr_

        // Resample backing audio to match
        val backingResampled = if (backingRef != null) {
            val backingLen = minOf(
                (backingRef.size.toDouble() * PLAYBACK_SAMPLE_RATE / backingSr).toInt() + 1,
                totalSamples
            )
            resampleAudio(backingRef, backingSr, totalSamples)
        } else null

        // Mix tone + backing into final buffer
        val mixed = FloatArray(totalSamples)
        if (backingResampled != null) {
            // Mix: backing at full volume, tones slightly quieter
            for (i in 0 until totalSamples) {
                val t = if (i < toneBuffer.size) toneBuffer[i] * 0.6f else 0f
                val b = if (i < backingResampled.size) backingResampled[i] * 0.7f else 0f
                mixed[i] = (t + b).coerceIn(-1f, 1f)
            }
        } else {
            // No backing - just tones at lower volume
            for (i in 0 until totalSamples) {
                mixed[i] = (if (i < toneBuffer.size) toneBuffer[i] else 0f).coerceIn(-1f, 1f)
            }
        }

        // Set up AudioTrack
        val bufferSize = AudioTrack.getMinBufferSize(PLAYBACK_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            .coerceAtLeast(PLAYBACK_SAMPLE_RATE / 20 * 4)
            .coerceAtLeast(4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(PLAYBACK_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.let { track ->
            track.play()

            // Determine starting offset
            var offset = if (seekPositionMs > 0) {
                (PLAYBACK_SAMPLE_RATE * seekPositionMs / 1000).toInt().coerceIn(0, mixed.size - 1)
            } else if (renderedPositionMs > 0) {
                (PLAYBACK_SAMPLE_RATE * renderedPositionMs / 1000).toInt().coerceIn(0, mixed.size - 1)
            } else 0
            seekPositionMs = -1

            val chunkSize = bufferSize / 4

            while (offset < mixed.size && playing && !paused) {
                // Handle seek commands during playback
                if (seekPositionMs >= 0) {
                    offset = (PLAYBACK_SAMPLE_RATE * seekPositionMs / 1000).toInt().coerceIn(0, mixed.size - 1)
                    track.flush()
                    seekPositionMs = -1
                }

                val end = minOf(offset + chunkSize, mixed.size)
                val chunk = mixed.copyOfRange(offset, end)
                track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                offset = end

                renderedPositionMs = offset.toLong() * 1000 / PLAYBACK_SAMPLE_RATE

                val ms = renderedPositionMs
                mainHandler.post {
                    stateListener?.invoke(PlaybackState(
                        isPlaying = true,
                        currentTimeMs = ms,
                        totalDurationMs = totalMs,
                        progress = if (totalMs > 0) ms.toFloat() / totalMs.toFloat() else 0f
                    ))
                }
            }

            if (playing && !paused) {
                track.stop()
            }
        }

        playing = false
        if (!paused) {
            mainHandler.post {
                stateListener?.invoke(PlaybackState(
                    currentTimeMs = totalMs, totalDurationMs = totalMs, progress = 1f
                ))
            }
        }
    }

    // Thread-safe backing audio references
    private val backingLock = Any()
    private var backingRef_: FloatArray? = null
    private var backingSr_: Int = PLAYBACK_SAMPLE_RATE

    fun setBackingAudio(data: FloatArray?, sampleRate: Int) {
        synchronized(backingLock) {
            backingRef_ = data
            backingSr_ = if (sampleRate > 0) sampleRate else PLAYBACK_SAMPLE_RATE
        }
    }
}
