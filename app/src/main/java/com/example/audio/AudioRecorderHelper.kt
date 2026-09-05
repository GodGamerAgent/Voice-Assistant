package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var amplitudeJob: Job? = null
    var isRecording: Boolean = false
        private set

    fun startRecording(
        outputFile: File,
        scope: CoroutineScope,
        onAmplitude: (Float) -> Unit
    ): Boolean {
        if (isRecording) {
            stopRecording()
        }
        return try {
            currentOutputFile = outputFile
            if (outputFile.exists()) {
                outputFile.delete()
            }

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true

            amplitudeJob = scope.launch(Dispatchers.Default) {
                while (isActive && isRecording) {
                    try {
                        val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                        val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                        onAmplitude(normalized)
                    } catch (e: Exception) {
                        // ignore amplitude polling errors
                    }
                    delay(80)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Failed to start recording", e)
            cleanUp()
            false
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        amplitudeJob?.cancel()
        amplitudeJob = null
        val file = currentOutputFile
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Error stopping recorder", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }
        return if (file != null && file.exists() && file.length() > 0) file else null
    }

    fun cancelRecording() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            mediaRecorder = null
            isRecording = false
            currentOutputFile?.delete()
            currentOutputFile = null
        }
    }

    private fun cleanUp() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaRecorder = null
        isRecording = false
    }
}
