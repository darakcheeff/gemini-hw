package com.gemini.hw.nativeapp.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Autonomous Speech-to-Text Manager for de-Googled / Huawei / Wear devices.
 * Uses Android's native SpeechRecognizer binding to installed offline/online
 * recognition engines (like Huawei SuiteBinderService or Wear IME) without GMS.
 */
class SpeechToTextManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    fun startListening(
        onPartialText: (String) -> Unit = {},
        onFinalText: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        try {
            stopListening()

            val hwComponent = ComponentName("com.iflytek.speechsuite.oversea", "com.iflytek.speechsuite.binder.SuiteBinderService")
            val recognizer = try {
                SpeechRecognizer.createSpeechRecognizer(context, hwComponent)
            } catch (_: Exception) {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    Log.d("SpeechToTextManager", "onReadyForSpeech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("SpeechToTextManager", "onBeginningOfSpeech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _isListening.value = false
                    Log.d("SpeechToTextManager", "onEndOfSpeech")
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио"
                        SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на запись аудио"
                        SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут сети"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Служба распознавания занята"
                        SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Нет речи"
                        else -> "Код ошибки: $error"
                    }
                    Log.w("SpeechToTextManager", "onError: $errorMsg ($error)")
                    onError(errorMsg)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        _recognizedText.value = text
                        onFinalText(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull() ?: ""
                    if (text.isNotBlank()) {
                        onPartialText(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            _isListening.value = false
            Log.e("SpeechToTextManager", "startListening failed: ${e.message}", e)
            onError(e.message ?: "Не удалось запустить распознавание")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        _isListening.value = false
    }
}
