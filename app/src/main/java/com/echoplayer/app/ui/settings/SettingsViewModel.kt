package com.echoplayer.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoplayer.app.EchoApp
import com.echoplayer.app.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: EchoApp) : ViewModel() {
    val settings: StateFlow<Settings> = app.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Settings())
    val ttsState = app.tts.state

    private val _health = MutableStateFlow<String?>(null)
    val health: StateFlow<String?> = _health

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing

    fun setServerUrl(v: String) = viewModelScope.launch { app.settings.setServerUrl(v); _health.value = null }
    fun setTtsRate(v: Float) = viewModelScope.launch { app.settings.setTtsRate(v) }
    fun setAutoAdvance(v: Boolean) = viewModelScope.launch { app.settings.setAutoAdvance(v) }
    fun setShowTranslation(v: Boolean) = viewModelScope.launch { app.settings.setShowTranslation(v) }
    fun setBlindMode(v: Boolean) = viewModelScope.launch { app.settings.setBlindMode(v) }
    fun setShowPhonemes(v: Boolean) = viewModelScope.launch { app.settings.setShowPhonemes(v) }

    fun testConnection() = viewModelScope.launch {
        _testing.value = true
        _health.value = runCatching { app.api.health() }.fold(
            onSuccess = { "连接成功：${it.model_id} · ${it.device}" + (it.scorer?.let { s -> " · 打分头 $s" } ?: "") },
            onFailure = { "连接失败：${it.message}" },
        )
        _testing.value = false
    }

    fun testTts() {
        app.tts.speak("Hello! This is Echo Player. Let's practice English together.", settings.value.ttsRate)
    }
}
