package com.kaosnet.krank

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class MicRecorder {

    private var audioRecord: AudioRecord? = null
    @Volatile private var recording = false
    private var recordingJob: Job? = null

    data class RecordingResult(
        val samples: FloatArray,
        val sampleRate: Int,
        val numChannels: Int
    )

    fun isRecording(): Boolean = recording

    suspend fun startRecording(durationMs: Int = Int.MAX_VALUE, onData: ((FloatArray) -> Unit)? = null): RecordingResult? = withContext(Dispatchers.IO) {
        if (recording) return@withContext null

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext null
            }

            audioRecord = recorder
            val totalSamples = if (durationMs == Int.MAX_VALUE) Int.MAX_VALUE else (sampleRate * durationMs / 1000)
            val buffer = ShortArray(bufferSize / 2)
            val allSamples = mutableListOf<Float>()

            recorder.startRecording()
            recording = true

            var samplesCollected = 0
            while (recording && samplesCollected < totalSamples && isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val chunk = FloatArray(read)
                    for (i in 0 until read) {
                        val sample = buffer[i].toFloat() / 32768f
                        chunk[i] = sample
                        allSamples.add(sample)
                    }
                    onData?.invoke(chunk)
                    samplesCollected += read
                }
            }

            try {
                recorder.stop()
            } catch (_: Exception) {}
            recorder.release()
            audioRecord = null

            if (allSamples.isEmpty()) return@withContext null

            RecordingResult(
                samples = allSamples.toFloatArray(),
                sampleRate = sampleRate,
                numChannels = 1
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            recording = false
        }
    }

    fun stop() {
        recording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    companion object {
        fun hasPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
