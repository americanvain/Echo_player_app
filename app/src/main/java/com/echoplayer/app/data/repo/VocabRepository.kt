package com.echoplayer.app.data.repo

import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.db.VocabDao
import com.echoplayer.app.data.db.VocabEntity
import com.echoplayer.app.data.model.Familiarity
import kotlinx.coroutines.flow.Flow

class VocabRepository(private val dao: VocabDao) {
    val all: Flow<List<VocabEntity>> = dao.observeAll()
    val words: Flow<List<String>> = dao.observeWords()
    val count: Flow<Int> = dao.observeCount()

    suspend fun contains(word: String) = dao.byWord(normalize(word)) != null

    suspend fun add(word: String, unit: UnitEntity?, materialTitle: String?, translation: String? = null): VocabEntity? {
        val key = normalize(word)
        if (key.isEmpty()) return null
        val entry = VocabEntity(
            word = key,
            displayWord = word.trim().trim { !it.isLetterOrDigit() && it != '\'' && it != '-' },
            contextSentence = unit?.text,
            contextTranslation = unit?.translation,
            unitId = unit?.id,
            materialId = unit?.materialId,
            materialTitle = materialTitle,
            translation = translation,
            addedAt = System.currentTimeMillis(),
        )
        val id = dao.insert(entry)
        return if (id > 0) entry.copy(id = id) else dao.byWord(key)
    }

    suspend fun remove(word: String) {
        dao.byWord(normalize(word))?.let { dao.delete(it) }
    }

    suspend fun delete(entry: VocabEntity) = dao.delete(entry)
    suspend fun update(entry: VocabEntity) = dao.update(entry)

    suspend fun dueForReview(limit: Int = 20) = dao.dueForReview(limit)

    suspend fun review(entry: VocabEntity, known: Boolean) {
        val f = if (known) minOf(Familiarity.KNOWN, entry.familiarity + 1) else Familiarity.NEW
        dao.review(entry.id, f, System.currentTimeMillis())
    }

    companion object {
        fun normalize(word: String): String =
            word.trim().lowercase().trim { !it.isLetterOrDigit() && it != '\'' && it != '-' }
    }
}
