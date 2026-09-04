package com.echoplayer.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoplayer.app.EchoApp
import com.echoplayer.app.data.db.PracticeSetEntity
import com.echoplayer.app.data.remote.AssessResult
import com.echoplayer.app.data.remote.PracticeItemDto
import com.echoplayer.app.data.remote.PracticeTypes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class SessionState(
    val set: PracticeSetEntity? = null,
    val items: List<PracticeItemDto> = emptyList(),
    val index: Int = 0,
    val results: Map<String, Boolean> = emptyMap(),
    /** 本题已提交的答案；null = 还没答。 */
    val answered: String? = null,
    val correct: Boolean? = null,
    val revealed: Boolean = false,
    /** 句子重组已经点上去的块。 */
    val picked: List<Int> = emptyList(),
    val recording: Boolean = false,
    val recordingSec: Double = 0.0,
    val assessing: Boolean = false,
    val shadowResult: AssessResult? = null,
    val message: String? = null,
    val finished: Boolean = false,
) {
    val item: PracticeItemDto? get() = items.getOrNull(index)
    val total: Int get() = items.size
    val correctCount: Int get() = results.count { it.value }
}

class PracticeSessionViewModel(private val app: EchoApp, private val setId: String) : ViewModel() {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state

    val recordingLevel = app.recorder.level

    private var recordingFile: File? = null
    private var recordingTimer: Job? = null

    init {
        app.tts.setCallbacks(onDone = {}, onError = {})
        viewModelScope.launch {
            val set = app.practiceSets.get(setId) ?: return@launch
            val items = app.practiceSets.items(set)
            val results = app.practiceSets.results(set)
            val start = if (set.completedAt != null) 0 else set.lastIndex.coerceIn(0, maxOf(0, items.size - 1))
            _state.update { it.copy(set = set, items = items, index = start, results = if (set.completedAt != null) emptyMap() else results) }
            speakCurrent()
        }
    }

    // ---- 播放 -------------------------------------------------------------------

    fun speakCurrent() {
        val item = _state.value.item ?: return
        val text = item.speak ?: return
        // 闪卡和讲解题不自动出声，其余题目进来就播
        if (item.type == PracticeTypes.FLASHCARD || item.type == PracticeTypes.EXPLAIN) return
        app.tts.speak(text, 1.0f)
    }

    fun replay(slow: Boolean = false) {
        val item = _state.value.item ?: return
        val text = item.speak ?: item.text ?: return
        app.tts.speak(text, if (slow) 0.7f else 1.0f)
    }

    fun speak(text: String, rate: Float = 1.0f) {
        app.tts.speak(text, rate)
    }

    // ---- 答题 -------------------------------------------------------------------

    fun answer(choice: String) {
        val item = _state.value.item ?: return
        if (_state.value.answered != null) return
        val ok = normalize(choice) == normalize(item.answer.orEmpty())
        record(item, ok, choice)
    }

    /** 闪卡：认识 / 不认识。 */
    fun flashcardAnswer(known: Boolean) {
        val item = _state.value.item ?: return
        if (_state.value.answered != null) return
        record(item, known, if (known) "known" else "unknown")
        item.vocab_word?.let { word ->
            viewModelScope.launch { app.vocab.reviewByWord(word, known) }
        }
    }

    fun reveal() = _state.update { it.copy(revealed = true) }

    /** 句子重组：点一个块加到答案末尾。 */
    fun pickChunk(i: Int) {
        val st = _state.value
        val item = st.item ?: return
        if (st.answered != null || i in st.picked) return
        val picked = st.picked + i
        _state.update { it.copy(picked = picked) }
        if (picked.size == item.chunks.size) {
            val built = picked.map { item.chunks[it] }
            val ok = built == item.answer_chunks
            record(item, ok, built.joinToString(" "))
        }
    }

    fun unpickChunk(i: Int) {
        if (_state.value.answered != null) return
        _state.update { it.copy(picked = it.picked - i) }
    }

    fun markExplainDone() {
        val item = _state.value.item ?: return
        record(item, true, "ok")
    }

    private fun record(item: PracticeItemDto, ok: Boolean, answer: String) {
        _state.update { it.copy(answered = answer, correct = ok, revealed = true, results = it.results + (item.id to ok)) }
    }

    /** 中途退出时保存进度，下次从这一题继续。 */
    fun saveAndExit() {
        val st = _state.value
        val set = st.set ?: return
        if (st.finished) return
        app.appScope.launch { app.practiceSets.saveProgress(set, st.index, st.results) }
    }

    fun next() {
        val st = _state.value
        val set = st.set ?: return
        val nextIndex = st.index + 1
        viewModelScope.launch { app.practiceSets.saveProgress(set, nextIndex, st.results) }
        if (nextIndex >= st.items.size) {
            _state.update { it.copy(index = nextIndex, finished = true) }
        } else {
            _state.update {
                it.copy(index = nextIndex, answered = null, correct = null, revealed = false, picked = emptyList(), shadowResult = null)
            }
            speakCurrent()
        }
    }

    fun skip() {
        val item = _state.value.item ?: return
        if (_state.value.answered == null) record(item, false, "skipped")
        next()
    }

    // ---- 跟读题 ------------------------------------------------------------------

    fun startRecording() {
        val item = _state.value.item ?: return
        if (_state.value.recording) return
        app.tts.stop()
        val file = File(File(app.filesDir, "recordings"), "practice-${item.id}-${System.currentTimeMillis()}.wav")
        try {
            app.recorder.start(file)
        } catch (e: Exception) {
            _state.update { it.copy(message = e.message ?: "无法录音") }
            return
        }
        recordingFile = file
        _state.update { it.copy(recording = true, recordingSec = 0.0) }
        recordingTimer = viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            while (true) {
                delay(100)
                val sec = (System.currentTimeMillis() - t0) / 1000.0
                _state.update { it.copy(recordingSec = sec) }
                if (sec >= 30) { stopRecording(); break }
            }
        }
    }

    fun stopRecording() {
        if (!_state.value.recording) return
        recordingTimer?.cancel(); recordingTimer = null
        val dur = app.recorder.stop()
        val file = recordingFile
        val item = _state.value.item
        _state.update { it.copy(recording = false) }
        if (file == null || item == null) return
        if (dur < 0.4) { file.delete(); _state.update { it.copy(message = "录音太短了，再读一次") }; return }
        if (!app.api.isConfigured) {
            _state.update { it.copy(message = "没有配置服务器，跟读题只能自己听。配置后可以打分") }
            return
        }
        _state.update { it.copy(assessing = true) }
        viewModelScope.launch {
            runCatching { app.practice.assessText(item.text ?: item.speak.orEmpty(), file) }
                .onSuccess { r ->
                    _state.update { it.copy(assessing = false, shadowResult = r) }
                    record(item, r.overall.accuracy >= 70, "${r.overall.accuracy}")
                }
                .onFailure { e -> _state.update { it.copy(assessing = false, message = "评分失败：${e.message}") } }
        }
    }

    fun playMyRecording() {
        val f = recordingFile ?: return
        if (f.exists()) app.clipPlayer.playFile(f)
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        app.tts.stop()
        app.clipPlayer.stop()
        if (_state.value.recording) { recordingTimer?.cancel(); app.recorder.stop() }
        super.onCleared()
    }

    private fun normalize(s: String) = s.trim().lowercase().trim { !it.isLetterOrDigit() && it != '\'' && it != ' ' && it != '-' }
}
