package com.gemini.hw.nativeapp.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var isInitialized = false

    private val _speakingMessageId = MutableStateFlow<String?>(null)
    val speakingMessageId: StateFlow<String?> = _speakingMessageId.asStateFlow()

    private var onDoneCallback: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale("ru", "RU")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    _speakingMessageId.value = null
                    onDoneCallback?.invoke()
                    onDoneCallback = null
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _speakingMessageId.value = null
                    onDoneCallback?.invoke()
                    onDoneCallback = null
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    _speakingMessageId.value = null
                    onDoneCallback?.invoke()
                    onDoneCallback = null
                }
            })
        }
    }

    fun speak(messageId: String, text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized || tts == null) {
            onDone?.invoke()
            return
        }
        if (_speakingMessageId.value == messageId) {
            stop()
            onDone?.invoke()
            return
        }
        stop()
        _speakingMessageId.value = messageId
        onDoneCallback = onDone

        // Remove markdown symbols and code blocks for fluid speech
        val cleanText = text
            .replace(Regex("""```[\s\S]*?```"""), " Блок кода. ")
            .replace(Regex("""`([^`]+)`"""), "$1")
            .replace(Regex("""[*#_~>\[\]\(\)]"""), " ")
            .replace(Regex("""https?://\S+"""), " ссылка ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleanText.isNotBlank()) {
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId)
        } else {
            _speakingMessageId.value = null
            onDone?.invoke()
            onDoneCallback = null
        }
    }

    fun stop() {
        tts?.stop()
        _speakingMessageId.value = null
        onDoneCallback = null
    }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
