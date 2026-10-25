package com.gemini.hw.nativeapp.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class VoiceInputManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startListening(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit,
        onStateChange: (Boolean) -> Unit
    ) {
        mainHandler.post {
            try {
                stopListening()
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    onError("Распознавание речи недоступно на устройстве")
                    return@post
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            onStateChange(true)
                        }

                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}

                        override fun onError(error: Int) {
                            onStateChange(false)
                            val errMsg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "Речь не распознана"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Время ожидания речи истекло"
                                SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи звука"
                                SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                                SpeechRecognizer.ERROR_CLIENT -> "Отменено"
                                else -> "Ошибка распознавания ($error)"
                            }
                            Log.w("VoiceInputManager", "onError: $errMsg ($error)")
                            onError(errMsg)
                        }

                        override fun onResults(results: Bundle?) {
                            onStateChange(false)
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            if (text.isNotBlank()) {
                                onFinalResult(text)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull() ?: ""
                            if (text.isNotBlank()) {
                                onPartialResult(text)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("VoiceInputManager", "startListening error: ${e.message}", e)
                onStateChange(false)
                onError("Сбой запуска микрофона: ${e.message}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }
}
