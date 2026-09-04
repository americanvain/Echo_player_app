package com.echoplayer.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoplayer.app.EchoApp
import com.echoplayer.app.data.db.MaterialEntity
import com.echoplayer.app.data.db.MaterialWithProgress
import com.echoplayer.app.data.model.MaterialStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val app: EchoApp) : ViewModel() {
    val library: StateFlow<List<MaterialWithProgress>> =
        app.materials.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.echoplayer.app.data.Settings())

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() { _message.value = null }

    fun importFile(uri: Uri, mime: String?, onDone: (MaterialEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val isPdf = mime == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")
            _busy.value = if (isPdf) "正在上传 PDF…" else "正在切分句子…"
            val result = runCatching { if (isPdf) app.materials.importPdf(uri) else app.materials.importTxt(uri) }
            _busy.value = null
            result.onSuccess { m ->
                _message.value = when (MaterialStatus.fromId(m.status)) {
                    MaterialStatus.READY -> "已导入《${m.title}》，共 ${m.unitCount} 句"
                    MaterialStatus.PROCESSING -> "《${m.title}》已交给服务器处理"
                    MaterialStatus.FAILED -> "导入失败：${m.statusMessage}"
                }
                onDone(m)
            }.onFailure {
                _message.value = "导入失败：${it.message ?: it.javaClass.simpleName}"
                onDone(null)
            }
        }
    }

    fun importText(text: String, title: String) {
        viewModelScope.launch {
            _busy.value = "正在切分句子…"
            val r = runCatching { app.materials.importPlainText(text, title) }
            _busy.value = null
            r.onSuccess { _message.value = "已导入《${it.title}》，共 ${it.unitCount} 句" }
                .onFailure { _message.value = "导入失败：${it.message}" }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val m = app.materials.delete(id)
            _message.value = if (m != null) "已删除《${m.title}》" else "已删除"
        }
    }

    fun refresh(id: String) {
        viewModelScope.launch {
            _busy.value = "正在查询服务器…"
            val m = app.materials.refreshRemote(id)
            _busy.value = null
            if (m != null) _message.value = "${m.title}：${MaterialStatus.fromId(m.status).label}${m.statusMessage?.let { " · $it" } ?: ""}"
        }
    }

    fun syncArticles() {
        viewModelScope.launch {
            _busy.value = "正在同步服务器题库…"
            val r = runCatching { app.materials.syncRemoteArticles() }
            _busy.value = null
            r.onSuccess { _message.value = "已同步 $it 篇文章" }
                .onFailure { _message.value = it.message ?: "同步失败" }
        }
    }

    private var polling = false

    /** 处理中的素材每 5 秒轮询一次服务器。 */
    fun pollProcessing() {
        if (polling) return
        polling = true
        viewModelScope.launch {
            while (true) {
                val processing = library.value.filter { it.status == MaterialStatus.PROCESSING.id }
                if (processing.isEmpty() || !app.api.isConfigured) { delay(5000); continue }
                processing.forEach { runCatching { app.materials.refreshRemote(it.id) } }
                delay(5000)
            }
        }
    }
}
