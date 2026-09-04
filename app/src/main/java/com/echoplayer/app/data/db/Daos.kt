package com.echoplayer.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MaterialWithProgress(
    val id: String,
    val title: String,
    val titleZh: String?,
    val description: String?,
    val descriptionZh: String?,
    val level: String?,
    val topic: String?,
    val sourceType: String,
    val status: String,
    val statusMessage: String?,
    val unitCount: Int,
    val createdAt: Long,
    val lastOpenedAt: Long?,
    val lastUnitIndex: Int,
    val practicedUnits: Int,
    val issueCount: Int,
)

@Dao
interface MaterialDao {
    @Query(
        """
        SELECT m.id, m.title, m.titleZh, m.description, m.descriptionZh, m.level, m.topic,
               m.sourceType, m.status, m.statusMessage, m.unitCount, m.createdAt, m.lastOpenedAt, m.lastUnitIndex,
               (SELECT COUNT(DISTINCT unitId) FROM practice_records p WHERE p.materialId = m.id) AS practicedUnits,
               (SELECT COUNT(*) FROM issues i WHERE i.materialId = m.id AND i.resolved = 0) AS issueCount
        FROM materials m
        ORDER BY COALESCE(m.lastOpenedAt, 0) DESC, m.sortOrder ASC, m.createdAt DESC
        """
    )
    fun observeLibrary(): Flow<List<MaterialWithProgress>>

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun get(id: String): MaterialEntity?

    @Query("SELECT * FROM materials WHERE id = :id")
    fun observe(id: String): Flow<MaterialEntity?>

    @Query("SELECT COUNT(*) FROM materials")
    suspend fun count(): Int

    @Query("SELECT id FROM materials WHERE sourceType = 'bundled'")
    suspend fun bundledIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(material: MaterialEntity)

    @Update
    suspend fun update(material: MaterialEntity)

    @Query("UPDATE materials SET lastOpenedAt = :at, lastUnitIndex = :unitIndex WHERE id = :id")
    suspend fun updateProgress(id: String, unitIndex: Int, at: Long)

    @Query("UPDATE materials SET status = :status, statusMessage = :message, unitCount = :unitCount WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, message: String?, unitCount: Int)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<UnitEntity>)

    @Query("DELETE FROM units WHERE materialId = :materialId")
    suspend fun deleteUnits(materialId: String)

    @Query("DELETE FROM segments WHERE materialId = :materialId")
    suspend fun deleteSegments(materialId: String)

    @Query("SELECT * FROM units WHERE materialId = :materialId ORDER BY orderIndex")
    suspend fun units(materialId: String): List<UnitEntity>

    @Query("SELECT * FROM units WHERE materialId = :materialId ORDER BY orderIndex")
    fun observeUnits(materialId: String): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE id = :id")
    suspend fun unit(id: String): UnitEntity?

    @Query("UPDATE units SET translation = :translation WHERE id = :id")
    suspend fun updateTranslation(id: String, translation: String?)

    @Query("UPDATE units SET audioPath = :path, audioDuration = :duration WHERE id = :id")
    suspend fun updateAudio(id: String, path: String?, duration: Double?)

    @Transaction
    suspend fun replaceContent(material: MaterialEntity, segments: List<SegmentEntity>, units: List<UnitEntity>) {
        insert(material)
        deleteUnits(material.id)
        deleteSegments(material.id)
        insertSegments(segments)
        insertUnits(units)
    }
}

@Dao
interface PracticeDao {
    @Insert
    suspend fun insert(record: PracticeRecordEntity): Long

    @Query("SELECT * FROM practice_records ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<PracticeRecordEntity>>

    @Query("SELECT * FROM practice_records ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PracticeRecordEntity>

    @Query("SELECT * FROM practice_records WHERE unitId = :unitId ORDER BY createdAt DESC")
    fun observeForUnit(unitId: String): Flow<List<PracticeRecordEntity>>

    @Query("SELECT * FROM practice_records WHERE materialId = :materialId ORDER BY createdAt DESC")
    suspend fun forMaterial(materialId: String): List<PracticeRecordEntity>

    @Query("SELECT unitId, MAX(accuracy) AS best, COUNT(*) AS attempts FROM practice_records WHERE materialId = :materialId GROUP BY unitId")
    fun observeBestByUnit(materialId: String): Flow<List<UnitBest>>

    @Query("SELECT * FROM practice_records WHERE id = :id")
    suspend fun get(id: Long): PracticeRecordEntity?

    @Query("DELETE FROM practice_records WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM practice_records")
    fun observeCount(): Flow<Int>

    @Query("SELECT AVG(accuracy) FROM practice_records")
    fun observeAverageAccuracy(): Flow<Double?>
}

data class UnitBest(val unitId: String, val best: Int, val attempts: Int)

@Dao
interface IssueDao {
    @Insert
    suspend fun insert(issue: IssueEntity): Long

    @Update
    suspend fun update(issue: IssueEntity)

    @Query("SELECT * FROM issues ORDER BY resolved ASC, createdAt DESC")
    fun observeAll(): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE unitId = :unitId ORDER BY createdAt DESC")
    fun observeForUnit(unitId: String): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE id = :id")
    suspend fun get(id: Long): IssueEntity?

    @Query("UPDATE issues SET resolved = :resolved, resolvedAt = :at WHERE id = :id")
    suspend fun setResolved(id: Long, resolved: Boolean, at: Long?)

    @Query("UPDATE issues SET explanation = :explanation WHERE id = :id")
    suspend fun setExplanation(id: Long, explanation: String?)

    @Query("DELETE FROM issues WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT layer, COUNT(*) AS n FROM issues GROUP BY layer")
    fun observeCountsByLayer(): Flow<List<LayerCount>>

    @Query("SELECT unitId, COUNT(*) AS n FROM issues WHERE materialId = :materialId AND resolved = 0 GROUP BY unitId")
    fun observeOpenByUnit(materialId: String): Flow<List<UnitIssueCount>>

    @Query("SELECT * FROM issues WHERE resolved = 0 ORDER BY createdAt DESC LIMIT :limit")
    suspend fun openIssues(limit: Int = 200): List<IssueEntity>

    @Query("SELECT COUNT(*) FROM issues WHERE resolved = 0")
    fun observeOpenCount(): Flow<Int>

    @Query("UPDATE issues SET resolved = 1, resolvedAt = :at WHERE id IN (:ids)")
    suspend fun resolveAll(ids: List<Long>, at: Long)
}

@Dao
interface PracticeSetDao {
    @Query("SELECT * FROM practice_sets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PracticeSetEntity>>

    @Query("SELECT * FROM practice_sets WHERE id = :id")
    suspend fun get(id: String): PracticeSetEntity?

    @Query("SELECT * FROM practice_sets WHERE id = :id")
    fun observe(id: String): Flow<PracticeSetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sets: List<PracticeSetEntity>)

    @Update
    suspend fun update(set: PracticeSetEntity)

    @Query("DELETE FROM practice_sets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM practice_sets WHERE completedAt IS NULL")
    fun observePendingCount(): Flow<Int>
}

data class LayerCount(val layer: Int, val n: Int)
data class UnitIssueCount(val unitId: String, val n: Int)

@Dao
interface VocabDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: VocabEntity): Long

    @Update
    suspend fun update(entry: VocabEntity)

    @Delete
    suspend fun delete(entry: VocabEntity)

    @Query("SELECT * FROM vocab WHERE word = :word LIMIT 1")
    suspend fun byWord(word: String): VocabEntity?

    @Query("SELECT * FROM vocab ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<VocabEntity>>

    @Query("SELECT word FROM vocab")
    fun observeWords(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM vocab")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM vocab WHERE familiarity < 2 ORDER BY COALESCE(lastReviewedAt, 0) ASC, addedAt ASC LIMIT :limit")
    suspend fun dueForReview(limit: Int = 20): List<VocabEntity>

    @Query("UPDATE vocab SET familiarity = :familiarity, reviewCount = reviewCount + 1, lastReviewedAt = :at WHERE id = :id")
    suspend fun review(id: Long, familiarity: Int, at: Long)
}


@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE unitId = :unitId ORDER BY createdAt ASC")
    fun observeForUnit(unitId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE unitId = :unitId ORDER BY createdAt ASC")
    suspend fun forUnit(unitId: String): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE unitId = :unitId")
    suspend fun clearUnit(unitId: String)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE role = 'user'")
    fun observeQuestionCount(): Flow<Int>
}
