package com.echoplayer.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val serverUrl: String = "",
    val ttsRate: Float = 1.0f,
    val autoAdvance: Boolean = false,
    val loopSentence: Boolean = false,
    val showTranslation: Boolean = true,
    val blindMode: Boolean = false,
    val showPhonemes: Boolean = true,
    val contextLines: Int = 2,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val ttsRate = floatPreferencesKey("tts_rate")
        val autoAdvance = booleanPreferencesKey("auto_advance")
        val loopSentence = booleanPreferencesKey("loop_sentence")
        val showTranslation = booleanPreferencesKey("show_translation")
        val blindMode = booleanPreferencesKey("blind_mode")
        val showPhonemes = booleanPreferencesKey("show_phonemes")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            serverUrl = p[Keys.serverUrl] ?: "",
            ttsRate = p[Keys.ttsRate] ?: 1.0f,
            autoAdvance = p[Keys.autoAdvance] ?: false,
            loopSentence = p[Keys.loopSentence] ?: false,
            showTranslation = p[Keys.showTranslation] ?: true,
            blindMode = p[Keys.blindMode] ?: false,
            showPhonemes = p[Keys.showPhonemes] ?: true,
        )
    }

    @Volatile
    var cachedServerUrl: String = ""
        private set

    suspend fun warm() {
        cachedServerUrl = settings.first().serverUrl
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setServerUrl(v: String) {
        cachedServerUrl = v.trim()
        context.dataStore.edit { it[Keys.serverUrl] = v.trim() }
    }

    suspend fun setTtsRate(v: Float) = context.dataStore.edit { it[Keys.ttsRate] = v }
    suspend fun setAutoAdvance(v: Boolean) = context.dataStore.edit { it[Keys.autoAdvance] = v }
    suspend fun setLoopSentence(v: Boolean) = context.dataStore.edit { it[Keys.loopSentence] = v }
    suspend fun setShowTranslation(v: Boolean) = context.dataStore.edit { it[Keys.showTranslation] = v }
    suspend fun setBlindMode(v: Boolean) = context.dataStore.edit { it[Keys.blindMode] = v }
    suspend fun setShowPhonemes(v: Boolean) = context.dataStore.edit { it[Keys.showPhonemes] = v }
}
