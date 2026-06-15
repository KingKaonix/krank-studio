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
 * Generates synthetic guitar tones from tablature data and plays them
 * synchronized with a backing audio track via AudioTrack.
 */
class TabPlayer {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        private const val BUFFER_MS = 50
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
    @Volatile private var seekPositionMs: Long = -1
    @Volatile private var paused = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var notes: List<TabNoteData> = emptyList()
    private var backingAudio: FloatArray? = null
    private var backingSampleRate: Int = SAMPLE_RATE
    private var playbackStartTime: Long = 0
    @Volatile private var renderedPositionMs: Long = 0

    private var stateListener: ((PlaybackState) -> Unit)? = null

    fun setStateListener(listener: (PlaybackState) -> Unit) {
        stateListener = listener
    }

    fun load(notes: List<TabNoteData>, backingAudio: FloatArray?, sampleRate: Int) {
        stop()
        this.notes = notes.sortedBy { it.startTime }
        this.backingAudio = backingAudio
        this.backingSampleRate = if (sampleRate > 0) sampleRate else SAMPLE_RATE
        renderedPositionMs = 0
        seekPositionMs = -1

        val totalMs = if (notes.isNotEmpty()) {
            (notes.maxOf { it.startTime + it.duration } * 1000).toLong()
        } else 0L

        mainHandler.post {
            stateListener?.invoke(PlaybackState(
                isPlaying = false,
                currentTimeMs = 0,
                totalDurationMs = totalMs,
                progress = 0f
            ))
        }
    }

    fun play() {
        if (playing) return
        if (notes.isEmpty()) return
        playing = true
        paused = false
        seekPositionMs = -1

        // If we were paused, continue from rendered position
        if (renderedPositionMs > 0) {
            seekPositionMs = renderedPositionMs
        }

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
        mainHandler.post {
            stateListener?.invoke(PlaybackState(
                isPlaying = false,
                currentTimeMs = 0,
                totalDurationMs = stateListener?.let { _ -> notes.maxOfOrNull { (it.startTime + it.duration) * 1000 }?.toLong() ?: 0 } ?: 0,
                progress = 0f
            ))
        }
    }

    fun seekTo(positionMs: Long) {
        seekPositionMs = positionMs
        if (!playing) {
            renderedPositionMs = positionMs
            mainHandler.post {
                val totalMs = if (notes.isNotEmpty()) {
                    (notes.maxOf { it.startTime + it.duration } * 1000).toLong()
                } else 0L
                stateListener?.invoke(PlaybackState(
                    isPlaying = false,
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

    private fun generateNoteBuffer(fret: Int, stringNum: Int, durationMs: Int): FloatArray {
        // Approximate frequencies for standard guitar tuning
        val stringFreqs = floatArrayOf(329.63f, 246.94f, 196.0f, 146.83f, 110.0f, 82.41f)
        val baseFreq = if (stringNum in 0..5) {
            stringFreqs[stringNum] * Math.pow(2.0, fret / 12.0).toFloat()
        } else 440f

        val numSamples = (SAMPLE_RATE * durationMs / 1000).coerceIn(512, SAMPLE_RATE * 2)
        val buffer = FloatArray(numSamples)

        // Generate tone with harmonics
        val attackSamples = (SAMPLE_RATE * 0.005f).toInt().coerceAtMost(numSamples / 4)
        val decaySamples = (SAMPLE_RATE * 0.3f).toInt().coerceAtMost(numSamples / 2)

        for (i in 0 until numSamples) {
            var sample = 0f
            for (h in GUITAR_HARMONICS.indices) {
                val freq = baseFreq * GUITAR_HARMONIC_RATIOS[h]
                val harmonicAmp = GUITAR_HARMONICS[h] * (1.0f / (h + 1).toFloat().pow(0.5f))
                sample += sin(2.0 * PI * freq * i / SAMPLE_RATE).toFloat() * harmonicAmp
            }

            // Envelope: attack / decay / sustain
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

    private fun mixNotes(notes: List<TabNoteData>, totalDurationMs: Long): FloatArray {
        val totalSamples = (SAMPLE_RATE * totalDurationMs / 1000).toInt() + SAMPLE_RATE
        val mixBuffer = FloatArray(totalSamples)

        for (note in notes) {
            val startSample = (note.startTime * SAMPLE_RATE).toInt()
            val durationMs = (note.duration * 1000).toInt().coerceIn(50, 2000)
            val noteBuffer = generateNoteBuffer(note.fret, note.stringNum, durationMs)
            val endPos = minOf(startSample + noteBuffer.size, totalSamples)
            for (i in 0 until (endPos - startSample)) {
                mixBuffer[startSample + i] = (mixBuffer[startSample + i] + noteBuffer[i]).coerceIn(-1f, 1f)
            }
        }
        return mixBuffer
    }

    private fun playbackLoop() {
        val totalMs = if (notes.isNotEmpty()) {
            (notes.maxOf { it.startTime + it.duration } * 1000).toLong() + 500
        } else {
            mainHandler.post { stateListener?.invoke(PlaybackState(isPlaying = false)) }
            playing = false
            return
        }

        // Generate the full mix buffer
        val mixBuffer = mixNotes(notes, totalMs)

        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING)
            .coerceAtLeast(SAMPLE_RATE * 2)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNELS)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.let { track ->
            track.play()
            var offset = 0
            val chunkSize = bufferSize / 4

            // Apply seek if requested
            if (seekPositionMs > 0) {
                offset = (SAMPLE_RATE * seekPositionMs / 1000).toInt().coerceIn(0, mixBuffer.size - 1)
                seekPositionMs = -1
                renderedPositionMs = seekPositionMs.coerceAtLeast(0)
            }

            // If we're resuming from pause
            if (renderedPositionMs > 0 && offset == 0) {
                offset = (SAMPLE_RATE * renderedPositionMs / 1000).toInt().coerceIn(0, mixBuffer.size - 1)
            }

            while (offset < mixBuffer.size && playing && !paused) {
                // Check for seek
                if (seekPositionMs >= 0) {
                    offset = (SAMPLE_RATE * seekPositionMs / 1000).toInt().coerceIn(0, mixBuffer.size - 1)
                    track.flush()
                    seekPositionMs = -1
                    renderedPositionMs = offset.toLong() * 1000 / SAMPLE_RATE
                }

                val end = minOf(offset + chunkSize, mixBuffer.size)
                val chunk = mixBuffer.copyOfRange(offset, end)
                track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                offset = end

                renderedPositionMs = offset.toLong() * 1000 / SAMPLE_RATE

                // Report progress on main thread
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

            if (!paused || !playing) {
                track.stop()
            }
        }

        if (!paused) {
            playing = false
            mainHandler.post {
                stateListener?.invoke(PlaybackState(
                    isPlaying = false,
                    currentTimeMs = totalMs,
                    totalDurationMs = totalMs,
                    progress = 1f
                ))
            }
        }
    }
}
