package com.echoplayer.app.ui.vocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoplayer.app.EchoApp
import com.echoplayer.app.data.db.VocabEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VocabViewModel(private val app: EchoApp) : ViewModel() {
    val all: StateFlow<List<VocabEntity>> = app.vocab.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _review = MutableStateFlow<List<VocabEntity>>(emptyList())
    val review: StateFlow<List<VocabEntity>> = _review

    private val _reviewIndex = MutableStateFlow(0)
    val reviewIndex: StateFlow<Int> = _reviewIndex

    fun speak(word: String) {
        app.tts.speak(word, 0.9f)
    }

    fun delete(entry: VocabEntity) = viewModelScope.launch { app.vocab.delete(entry) }

    fun setFamiliarity(entry: VocabEntity, f: Int) = viewModelScope.launch { app.vocab.update(entry.copy(familiarity = f)) }

    fun saveNote(entry: VocabEntity, note: String?, translation: String?) = viewModelScope.launch {
        app.vocab.update(entry.copy(note = note?.takeIf { it.isNotBlank() }, translation = translation?.takeIf { it.isNotBlank() }))
    }

    fun startReview() = viewModelScope.launch {
        _review.value = app.vocab.dueForReview(20)
        _reviewIndex.value = 0
    }

    fun answer(known: Boolean) = viewModelScope.launch {
        val entry = _review.value.getOrNull(_reviewIndex.value) ?: return@launch
        app.vocab.review(entry, known)
        _reviewIndex.value = _reviewIndex.value + 1
    }

    fun endReview() {
        _review.value = emptyList()
        _reviewIndex.value = 0
    }
}
