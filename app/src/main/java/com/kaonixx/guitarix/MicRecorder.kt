package com.kaonixx.guitarix

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MicRecorder {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    data class RecordingResult(
        val samples: FloatArray,
        val sampleRate: Int,
        val numChannels: Int
    )

    suspend fun record(durationMs: Int = 5000): RecordingResult? = withContext(Dispatchers.IO) {
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

            val totalSamples = (sampleRate * durationMs / 1000)
            val buffer = ShortArray(bufferSize / 2)
            val allSamples = mutableListOf<Float>()

            recorder.startRecording()
            isRecording = true

            var samplesCollected = 0
            while (samplesCollected < totalSamples && isRecording) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        allSamples.add(buffer[i].toFloat() / 32768f)
                    }
                    samplesCollected += read
                }
            }

            recorder.stop()
            recorder.release()

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
            isRecording = false
        }
    }

    fun stop() {
        isRecording = false
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
