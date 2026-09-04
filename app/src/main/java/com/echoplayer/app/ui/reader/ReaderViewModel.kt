package com.echoplayer.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoplayer.app.EchoApp
import com.echoplayer.app.audio.TtsEngine
import com.echoplayer.app.data.Settings
import com.echoplayer.app.data.db.IssueEntity
import com.echoplayer.app.data.db.MaterialEntity
import com.echoplayer.app.data.db.PracticeRecordEntity
import com.echoplayer.app.data.db.UnitBest
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.data.remote.AssessResult
import com.echoplayer.app.data.remote.ExplainResponse
import com.echoplayer.app.data.remote.ServerException
import com.echoplayer.app.data.remote.WordResult
import com.echoplayer.app.data.repo.VocabRepository
import com.echoplayer.app.util.Words
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class Playback { IDLE, SENTENCE, WORDS, RECORDING_PLAYBACK }

data class ReaderUiState(
    val material: MaterialEntity? = null,
    val units: List<UnitEntity> = emptyList(),
    val index: Int = 0,
    val rate: Float = 1.0f,
    val loop: Boolean = false,
    val autoAdvance: Boolean = false,
    val showTranslation: Boolean = true,
    val blindMode: Boolean = false,
    val textRevealed: Boolean = true,
    val playback: Playback = Playback.IDLE,
    val recording: Boolean = false,
    val recordingSec: Double = 0.0,
    val assessing: Boolean = false,
    val result: AssessResult? = null,
    val recordingPath: String? = null,
    val translating: Boolean = false,
    val message: String? = null,
    val ttsState: TtsEngine.State = TtsEngine.State.INIT,
    val serverConfigured: Boolean = false,
    val showPhonemes: Boolean = true,
) {
    val unit: UnitEntity? get() = units.getOrNull(index)
    val hasPrev: Boolean get() = index > 0
    val hasNext: Boolean get() = index < units.size - 1
    val isPlaying: Boolean get() = playback != Playback.IDLE
    val context: List<UnitEntity> get() = if (index <= 0) emptyList() else units.subList(maxOf(0, index - 2), index)
}

/** 每句最近一次的评分结果与录音路径，切换句子再切回来还能看到。 */
private data class UnitResult(val result: AssessResult, val recordingPath: String?)

class ReaderViewModel(
    private val app: EchoApp,
    private val materialId: String,
    private val initialIndex: Int?,
    private val initialUnitId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state

    val recordingLevel: StateFlow<Float> = app.recorder.level

    private val currentUnitId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val attempts: StateFlow<List<PracticeRecordEntity>> = currentUnitId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else app.practice.observeForUnit(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val unitIssues: StateFlow<List<IssueEntity>> = currentUnitId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else app.issues.observeForUnit(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bestByUnit: StateFlow<Map<String, UnitBest>> = app.practice.observeBestByUnit(materialId)
        .map { list -> list.associateBy { it.unitId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val openIssuesByUnit: StateFlow<Map<String, Int>> = app.issues.observeOpenByUnit(materialId)
        .map { list -> list.associate { it.unitId to it.n } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val vocabWords: StateFlow<Set<String>> = app.vocab.words
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val results = HashMap<String, UnitResult>()
    private var wordQueue: ArrayDeque<String> = ArrayDeque()
    private var queueRate: Float = 1f
    private var recordingFile: File? = null
    private var recordingTimer: Job? = null
    private var initialized = false
    private var settingsLoaded = false

    init {
        app.tts.setCallbacks(onDone = { onUtteranceDone() }, onError = { onUtteranceError() })
        viewModelScope.launch { app.tts.state.collect { s -> _state.update { it.copy(ttsState = s) } } }
        viewModelScope.launch {
            app.settings.settings.collect { s ->
                applySettings(s)
            }
        }
        viewModelScope.launch { app.materials.observe(materialId).collect { m -> _state.update { it.copy(material = m) } } }
        viewModelScope.launch {
            app.materials.observeUnits(materialId).collect { units ->
                _state.update { st ->
                    val idx = if (!initialized && units.isNotEmpty()) {
                        initialized = true
                        val byId = initialUnitId?.let { id -> units.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
                        (byId ?: initialIndex ?: st.material?.lastUnitIndex ?: app.materials.get(materialId)?.lastUnitIndex ?: 0)
                            .coerceIn(0, units.size - 1)
                    } else st.index.coerceIn(0, maxOf(0, units.size - 1))
                    st.copy(units = units, index = idx)
                }
                syncUnit()
            }
        }
    }

    private fun applySettings(s: Settings) {
        _state.update {
            val first = !settingsLoaded
            settingsLoaded = true
            it.copy(
                rate = if (first) s.ttsRate else it.rate,
                loop = if (first) s.loopSentence else it.loop,
                autoAdvance = if (first) s.autoAdvance else it.autoAdvance,
                showTranslation = if (first) s.showTranslation else it.showTranslation,
                blindMode = if (first) s.blindMode else it.blindMode,
                textRevealed = if (first) !s.blindMode else it.textRevealed,
                showPhonemes = s.showPhonemes,
                serverConfigured = s.serverUrl.isNotBlank(),
            )
        }
    }

    private fun syncUnit() {
        val st = _state.value
        val unit = st.unit
        currentUnitId.value = unit?.id
        val saved = unit?.let { results[it.id] }
        _state.update { it.copy(result = saved?.result, recordingPath = saved?.recordingPath) }
        if (unit != null) viewModelScope.launch { app.materials.updateProgress(materialId, st.index) }
    }

    // ---- 导航 -----------------------------------------------------------------

    fun next() = jumpTo(_state.value.index + 1)
    fun prev() = jumpTo(_state.value.index - 1)

    fun jumpTo(i: Int) {
        val st = _state.value
        if (st.units.isEmpty()) return
        val idx = i.coerceIn(0, st.units.size - 1)
        if (idx == st.index && st.textRevealed) return
        stopPlayback()
        _state.update { it.copy(index = idx, textRevealed = !it.blindMode, message = null) }
        syncUnit()
    }

    fun reveal() = _state.update { it.copy(textRevealed = true) }

    // ---- 播放 -----------------------------------------------------------------

    fun togglePlay() {
        if (_state.value.isPlaying) stopPlayback() else playCurrent()
    }

    fun playCurrent(rateOverride: Float? = null) {
        val st = _state.value
        val unit = st.unit ?: return
        stopPlayback(keepState = true)
        val rate = rateOverride ?: st.rate
        val audio = unit.audioPath?.let { File(it) }?.takeIf { it.exists() }
        _state.update { it.copy(playback = Playback.SENTENCE) }
        if (audio != null) {
            app.clipPlayer.playFile(audio, rate) { onUtteranceDone() }
        } else {
            val id = app.tts.speak(unit.text, rate)
            if (id == null) {
                _state.update { it.copy(playback = Playback.IDLE, message = ttsProblem()) }
            }
        }
    }

    fun playSlow() = playCurrent(rateOverride = (_state.value.rate * 0.7f).coerceAtLeast(0.5f))

    fun playWordByWord() {
        val unit = _state.value.unit ?: return
        stopPlayback(keepState = true)
        wordQueue = ArrayDeque(Words.tokenize(unit.text).map { it.display }.filter { it.any(Char::isLetterOrDigit) })
        queueRate = (_state.value.rate * 0.85f).coerceAtLeast(0.5f)
        if (wordQueue.isEmpty()) return
        _state.update { it.copy(playback = Playback.WORDS) }
        speakNextWord()
    }

    /** 联系上下文：把前两句和当前句连起来读一遍。 */
    fun playContext() {
        val st = _state.value
        val unit = st.unit ?: return
        stopPlayback(keepState = true)
        wordQueue = ArrayDeque(st.context.map { it.text } + unit.text)
        queueRate = st.rate
        _state.update { it.copy(playback = Playback.WORDS) }
        speakNextWord()
    }

    private fun speakNextWord() {
        val w = wordQueue.removeFirstOrNull()
        if (w == null) {
            _state.update { it.copy(playback = Playback.IDLE) }
            return
        }
        val id = app.tts.speak(w, queueRate)
        if (id == null) _state.update { it.copy(playback = Playback.IDLE, message = ttsProblem()) }
    }

    fun speakWord(word: String) {
        stopPlayback(keepState = true)
        val id = app.tts.speak(Words.normalize(word).ifEmpty { word }, (_state.value.rate * 0.9f).coerceAtLeast(0.5f))
        if (id == null) _state.update { it.copy(message = ttsProblem()) }
    }

    private fun onUtteranceDone() {
        when (_state.value.playback) {
            Playback.WORDS -> {
                // 词与词之间留一点空隙
                viewModelScope.launch { delay(180); if (_state.value.playback == Playback.WORDS) speakNextWord() }
            }
            Playback.SENTENCE -> {
                val st = _state.value
                when {
                    st.loop -> viewModelScope.launch { delay(600); if (_state.value.playback == Playback.SENTENCE) playCurrent() }
                    st.autoAdvance && st.hasNext -> viewModelScope.launch {
                        delay(700)
                        if (_state.value.playback == Playback.SENTENCE) {
                            _state.update { it.copy(index = it.index + 1, textRevealed = !it.blindMode) }
                            syncUnit()
                            playCurrent()
                        }
                    }
                    else -> _state.update { it.copy(playback = Playback.IDLE) }
                }
            }
            Playback.RECORDING_PLAYBACK -> _state.update { it.copy(playback = Playback.IDLE) }
            Playback.IDLE -> {}
        }
    }

    private fun onUtteranceError() {
        _state.update { it.copy(playback = Playback.IDLE, message = "语音引擎出错了") }
    }

    fun stopPlayback(keepState: Boolean = false) {
        wordQueue.clear()
        app.tts.stop()
        app.clipPlayer.stop()
        if (!keepState) _state.update { it.copy(playback = Playback.IDLE) }
    }

    private fun ttsProblem(): String = when (app.tts.state.value) {
        TtsEngine.State.NO_ENGINE -> "手机上没有可用的语音引擎，请安装 Google 文字转语音或系统 TTS"
        TtsEngine.State.NO_LANGUAGE -> "语音引擎缺少英语语音包，请到系统设置里下载"
        TtsEngine.State.INIT -> "语音引擎还在初始化，稍等一下再试"
        TtsEngine.State.READY -> "朗读失败了，再试一次"
    }

    fun setRate(r: Float) {
        val v = r.coerceIn(0.5f, 1.5f)
        _state.update { it.copy(rate = v) }
        viewModelScope.launch { app.settings.setTtsRate(v) }
    }

    fun toggleLoop() {
        val v = !_state.value.loop
        _state.update { it.copy(loop = v, autoAdvance = if (v) false else it.autoAdvance) }
        viewModelScope.launch { app.settings.setLoopSentence(v); if (v) app.settings.setAutoAdvance(false) }
    }

    fun toggleAutoAdvance() {
        val v = !_state.value.autoAdvance
        _state.update { it.copy(autoAdvance = v, loop = if (v) false else it.loop) }
        viewModelScope.launch { app.settings.setAutoAdvance(v); if (v) app.settings.setLoopSentence(false) }
    }

    fun toggleTranslation() {
        val v = !_state.value.showTranslation
        _state.update { it.copy(showTranslation = v) }
        viewModelScope.launch { app.settings.setShowTranslation(v) }
    }

    fun toggleBlindMode() {
        val v = !_state.value.blindMode
        _state.update { it.copy(blindMode = v, textRevealed = !v) }
        viewModelScope.launch { app.settings.setBlindMode(v) }
    }

    // ---- 跟读 / 评分 ---------------------------------------------------------------

    fun startRecording() {
        val unit = _state.value.unit ?: return
        if (_state.value.recording) return
        stopPlayback()
        val dir = File(app.filesDir, "recordings")
        val file = File(dir, "${unit.id.replace('#', '_')}-${System.currentTimeMillis()}.wav")
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
        _state.update { it.copy(recording = false) }
        if (file == null) return
        if (dur < 0.4) {
            file.delete()
            _state.update { it.copy(message = "录音太短了，再读一次") }
            return
        }
        _state.update { it.copy(recordingPath = file.absolutePath) }
        assess(file)
    }

    fun cancelRecording() {
        if (!_state.value.recording) return
        recordingTimer?.cancel(); recordingTimer = null
        app.recorder.stop()
        recordingFile?.delete()
        _state.update { it.copy(recording = false) }
    }

    private fun assess(file: File) {
        val unit = _state.value.unit ?: return
        if (!app.api.isConfigured) {
            _state.update { it.copy(message = "录音已保存。要打分请先在「设置」里填写评测服务器地址") }
            return
        }
        _state.update { it.copy(assessing = true) }
        viewModelScope.launch {
            val r = runCatching { app.practice.assess(unit, file) }
            r.onSuccess { (_, result) ->
                results[unit.id] = UnitResult(result, file.absolutePath)
                _state.update { st ->
                    if (st.unit?.id == unit.id) st.copy(assessing = false, result = result, recordingPath = file.absolutePath)
                    else st.copy(assessing = false)
                }
            }.onFailure { e ->
                val msg = when (e) {
                    is ServerException -> e.message ?: "评测失败"
                    else -> "评测失败：${e.message ?: e.javaClass.simpleName}"
                }
                _state.update { it.copy(assessing = false, message = msg) }
            }
        }
    }

    fun playMyRecording() {
        val path = _state.value.recordingPath ?: return
        val f = File(path)
        if (!f.exists()) { _state.update { it.copy(message = "录音文件不在了") }; return }
        stopPlayback(keepState = true)
        _state.update { it.copy(playback = Playback.RECORDING_PLAYBACK) }
        app.clipPlayer.playFile(f) { onUtteranceDone() }
    }

    fun playWordClip(word: WordResult) {
        val path = _state.value.recordingPath ?: return
        val f = File(path)
        if (!f.exists()) return
        stopPlayback(keepState = true)
        _state.update { it.copy(playback = Playback.RECORDING_PLAYBACK) }
        app.clipPlayer.playWavRange(f, word.start, word.end) { onUtteranceDone() }
    }

    fun showAttempt(record: PracticeRecordEntity) {
        val result = app.practice.decode(record) ?: return
        results[record.unitId] = UnitResult(result, record.recordingPath)
        _state.update { it.copy(result = result, recordingPath = record.recordingPath) }
    }

    // ---- 生词本 --------------------------------------------------------------------

    fun toggleVocab(word: String) {
        val unit = _state.value.unit
        val title = _state.value.material?.title
        viewModelScope.launch {
            val key = VocabRepository.normalize(word)
            if (key.isEmpty()) return@launch
            if (app.vocab.contains(key)) {
                app.vocab.remove(key)
                _state.update { it.copy(message = "已从生词本移除 $key") }
            } else {
                app.vocab.add(word, unit, title)
                _state.update { it.copy(message = "已加入生词本：$key") }
            }
        }
    }

    // ---- 翻译 ----------------------------------------------------------------------

    fun translateCurrent() {
        val unit = _state.value.unit ?: return
        if (!unit.translation.isNullOrBlank()) return
        if (!app.api.isConfigured) {
            _state.update { it.copy(message = "这句还没有翻译。配置服务器后可以在线翻译") }
            return
        }
        _state.update { it.copy(translating = true) }
        viewModelScope.launch {
            runCatching { app.materials.translateUnit(unit) }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "翻译失败") } }
            _state.update { it.copy(translating = false) }
        }
    }

    // ---- 问题定位 ------------------------------------------------------------------

    suspend fun recordIssue(layer: ProblemLayer, note: String?): IssueEntity? {
        val unit = _state.value.unit ?: return null
        val issue = app.issues.record(unit, layer, note)
        _state.update { it.copy(message = "已记录：${layer.title}") }
        return issue
    }

    /** 请求教学 Agent 讲解；服务器不可用时返回离线模板。返回 (讲解, 是否来自服务器)。 */
    suspend fun explain(layer: ProblemLayer, note: String?, issueId: Long?): Pair<ExplainResponse, Boolean> {
        val st = _state.value
        val unit = st.unit ?: return offlineExplanation(layer, null, note) to false
        val ctx = st.context.map { it.text }
        val history = unitIssues.value.mapNotNull { it.note }
        val remote = if (app.api.isConfigured) runCatching { app.issues.explain(unit, ctx, layer, note, history) }.getOrNull() else null
        if (remote != null) {
            issueId?.let { app.issues.saveExplanation(it, remote.explanation) }
            return remote to true
        }
        return offlineExplanation(layer, unit, note) to false
    }

    private fun offlineExplanation(layer: ProblemLayer, unit: UnitEntity?, note: String?): ExplainResponse {
        val sb = StringBuilder()
        sb.append("你把这一句的问题定位在「${layer.title}」。\n\n")
        sb.append("这一层在做什么：").append(layer.definition).append("\n\n")
        sb.append("典型表现：\n")
        layer.symptoms.forEach { sb.append("• ").append(it).append('\n') }
        sb.append("\n现在可以做的：\n")
        layer.actions.forEach { sb.append("• ").append(it.label).append("：").append(it.description).append('\n') }
        if (!note.isNullOrBlank()) sb.append("\n你的疑问已记录：").append(note).append('\n')
        sb.append("\n接入教学服务器后，这里会由 AI 结合这句话、上下文和你的疑问给出针对性的讲解、例句和小测验。")
        val examples = unit?.let { listOf(it.text) }.orEmpty()
        return ExplainResponse(explanation = sb.toString(), examples = examples)
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        stopPlayback()
        if (_state.value.recording) cancelRecording()
        super.onCleared()
    }
}
