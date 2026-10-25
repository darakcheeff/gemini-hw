package com.gemini.hw.nativeapp.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Autonomous Voice Manager for GMS-free devices (Huawei / HarmonyOS / AOSP).
 * Uses Android's native MediaRecorder to capture audio directly from hardware
 * without depending on Google Play Services or Google SpeechRecognizer.
 */
class VoiceInputManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun startRecording(
        onError: (String) -> Unit = {}
    ): Boolean {
        return try {
            stopRecording()

            val file = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.m4a")
            currentOutputFile = file

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
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            _isRecording.value = true
            Log.d("VoiceInputManager", "Recording started: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("VoiceInputManager", "startRecording error: ${e.message}", e)
            _isRecording.value = false
            mediaRecorder = null
            onError("Не удалось запустить запись: ${e.message}")
            false
        }
    }

    fun stopRecording(): File? {
        val file = currentOutputFile
        try {
            if (_isRecording.value && mediaRecorder != null) {
                mediaRecorder?.stop()
                mediaRecorder?.reset()
                mediaRecorder?.release()
            }
        } catch (e: Exception) {
            Log.w("VoiceInputManager", "stopRecording error: ${e.message}")
        } finally {
            mediaRecorder = null
            _isRecording.value = false
        }
        return if (file != null && file.exists() && file.length() > 0) file else null
    }

    fun getMaxAmplitude(): Int {
        return try {
            if (_isRecording.value) {
                mediaRecorder?.maxAmplitude ?: 0
            } else 0
        } catch (_: Exception) {
            0
        }
    }

    fun cancelRecording() {
        try {
            stopRecording()
            currentOutputFile?.delete()
            currentOutputFile = null
        } catch (_: Exception) {}
    }
}
