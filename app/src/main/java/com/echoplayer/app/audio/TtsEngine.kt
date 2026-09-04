package com.echoplayer.app.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 本机 TTS 朗读。Echo_player 第三部分要求的"接近真人、有情感"的语音由服务器合成，
 * 下载后 [UnitEntity.audioPath] 非空即优先播放文件；这里是随时可用的兜底。
 */
class TtsEngine(context: Context) {

    enum class State { INIT, READY, NO_ENGINE, NO_LANGUAGE }

    private val _state = MutableStateFlow(State.INIT)
    val state: StateFlow<State> = _state

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    private val counter = AtomicInteger()
    private var currentId: String? = null
    private var onDone: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status != TextToSpeech.SUCCESS) {
            _state.value = State.NO_ENGINE
            return@TextToSpeech
        }
        val res = runCatching { ttsRef.setLanguage(Locale.US) }.getOrDefault(TextToSpeech.LANG_MISSING_DATA)
        _state.value = if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) State.NO_LANGUAGE else State.READY
        pickVoice()
    }
    private val ttsRef get() = tts

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == currentId) {
                    _speaking.value = false
                    utteranceId?.let { onDone?.invoke(it) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _speaking.value = false
                utteranceId?.let { onError?.invoke(it) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _speaking.value = false
                utteranceId?.let { onError?.invoke(it) }
            }
        })
    }

    /** 优先挑一个高质量、非网络的美音 voice。 */
    private fun pickVoice() {
        runCatching {
            val voices: Set<Voice> = tts.voices ?: return
            val candidates = voices.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            val best = candidates
                .sortedWith(compareByDescending<Voice> { it.locale.country == "US" }.thenByDescending { it.quality })
                .firstOrNull()
            if (best != null) tts.voice = best
        }
    }

    val ready: Boolean get() = _state.value == State.READY

    fun setCallbacks(onDone: (String) -> Unit, onError: (String) -> Unit) {
        this.onDone = onDone
        this.onError = onError
    }

    /** 朗读一段文本，返回 utteranceId；引擎不可用时返回 null。 */
    fun speak(text: String, rate: Float): String? {
        if (!ready) return null
        val id = "u${counter.incrementAndGet()}"
        currentId = id
        tts.setSpeechRate(rate.coerceIn(0.4f, 2.0f))
        val params = Bundle()
        val r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (r != TextToSpeech.SUCCESS) {
            currentId = null
            return null
        }
        _speaking.value = true
        return id
    }

    fun stop() {
        currentId = null
        runCatching { tts.stop() }
        _speaking.value = false
    }

    fun shutdown() {
        runCatching { tts.shutdown() }
    }
}
