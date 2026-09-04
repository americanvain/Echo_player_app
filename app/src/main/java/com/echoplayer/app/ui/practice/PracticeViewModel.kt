package com.echoplayer.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoplayer.app.EchoApp
import com.echoplayer.app.data.db.PracticeSetEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 练习区首页：把记录变成练习集。 */
class PracticeViewModel(private val app: EchoApp) : ViewModel() {

    val sets: StateFlow<List<PracticeSetEntity>> =
        app.practiceSets.sets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val openIssues: StateFlow<Int> =
        app.issues.openCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val vocabCount: StateFlow<Int> =
        app.vocab.count.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val practiceCount: StateFlow<Int> =
        app.practice.count.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val serverConfigured: StateFlow<Boolean> = app.settings.settings
        .map { it.serverUrl.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _analysis = MutableStateFlow<String?>(null)
    val analysis: StateFlow<String?> = _analysis

    fun consumeMessage() { _message.value = null }

    fun generate(preferLocal: Boolean = false) {
        if (_generating.value) return
        _generating.value = true
        viewModelScope.launch {
            val r = runCatching { app.practiceSets.generate(preferLocal) }
            _generating.value = false
            r.onSuccess { out ->
                out.analysis?.let { _analysis.value = it }
                _message.value = when {
                    out.sets > 0 && out.fromServer -> "AI 生成了 ${out.sets} 组、共 ${out.items} 题"
                    out.sets > 0 -> (out.message?.plus("；") ?: "") + "本机生成了 ${out.sets} 组、共 ${out.items} 题"
                    else -> out.message ?: "这次没有生成新的练习"
                }
            }.onFailure { _message.value = "生成失败：${it.message ?: it.javaClass.simpleName}" }
        }
    }

    fun delete(id: String) = viewModelScope.launch { app.practiceSets.delete(id) }

    fun reset(set: PracticeSetEntity) = viewModelScope.launch { app.practiceSets.resetProgress(set) }
}
