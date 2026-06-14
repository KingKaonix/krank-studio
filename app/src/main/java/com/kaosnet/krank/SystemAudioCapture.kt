package com.kaosnet.krank

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class SystemAudioCapture(private val activity: Activity) {

    private var mediaProjection: MediaProjection? = null
    @Volatile private var capturing = false

    data class CaptureResult(
        val samples: FloatArray,
        val sampleRate: Int,
        val numChannels: Int,
        val durationMs: Int
    )

    fun createCaptureIntent(): Intent? {
        return try {
            val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            mpm?.createScreenCaptureIntent()
        } catch (e: Exception) { null }
    }

    fun setup(resultCode: Int, data: Intent): Boolean {
        return try {
            val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (mpm != null) {
                mediaProjection = mpm.getMediaProjection(resultCode, data)
                mediaProjection != null
            } else false
        } catch (e: Exception) { false }
    }

    suspend fun capture(maxDurationMs: Int = 30000): CaptureResult? = withContext(Dispatchers.IO) {
        val proj = mediaProjection ?: return@withContext null

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = (AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4).coerceAtLeast(4096)

        try {
            // Try creating AudioRecord from MediaProjection (official API 29+)
            val audioRecord = createMediaProjectionAudioRecord(proj, sampleRate, channelConfig, audioFormat, bufferSize)

            if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                return@withContext null
            }

            val maxSamples = sampleRate * maxDurationMs / 1000
            val buffer = ShortArray(bufferSize / 2)
            val allSamples = mutableListOf<Float>()

            audioRecord.startRecording()
            capturing = true

            var collected = 0
            while (capturing && collected < maxSamples && isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        allSamples.add(buffer[i].toFloat() / 32768f)
                    }
                    collected += read
                } else if (read < 0) {
                    break // Error
                }
            }

            try { audioRecord.stop() } catch (_: Exception) {}
            audioRecord.release()

            if (allSamples.isEmpty()) return@withContext null

            CaptureResult(
                samples = allSamples.toFloatArray(),
                sampleRate = sampleRate,
                numChannels = 1,
                durationMs = (allSamples.size * 1000 / sampleRate).coerceAtMost(maxDurationMs)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            capturing = false
        }
    }

    private fun createMediaProjectionAudioRecord(
        proj: MediaProjection,
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSize: Int
    ): AudioRecord? {
        if (Build.VERSION.SDK_INT < 29) return null

        // Use reflection for broad compatibility
        return try {
            val method = MediaProjection::class.java.getMethod(
                "createAudioRecord",
                Int::class.java, Int::class.java, Int::class.java, Int::class.java, Int::class.java
            )
            // Try REMOTE_SUBMIX first (captures device audio), fall back to DEFAULT
            val sources = intArrayOf(
                MediaRecorder.AudioSource.REMOTE_SUBMIX,
                MediaRecorder.AudioSource.DEFAULT,
                0  // Try raw 0 as last resort
            )
            for (src in sources) {
                try {
                    val record = method.invoke(proj, src, sampleRate, channelConfig, audioFormat, bufferSize) as? AudioRecord
                    if (record?.state == AudioRecord.STATE_INITIALIZED) return record
                    record?.release()
                } catch (_: Exception) {}
            }
            null
        } catch (e: Exception) { null }
    }

    fun stopCapture() { capturing = false }

    fun release() {
        capturing = false
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
    }

    companion object {
        const val REQUEST_CODE = 1001
    }
}
