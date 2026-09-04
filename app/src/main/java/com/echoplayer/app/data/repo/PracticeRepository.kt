package com.echoplayer.app.data.repo

import com.echoplayer.app.data.db.PracticeDao
import com.echoplayer.app.data.db.PracticeRecordEntity
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.remote.AssessResult
import com.echoplayer.app.data.remote.EchoServerApi
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.io.File

class PracticeRepository(private val dao: PracticeDao, private val api: EchoServerApi) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    val recent: Flow<List<PracticeRecordEntity>> = dao.observeRecent()
    val count: Flow<Int> = dao.observeCount()
    val averageAccuracy: Flow<Double?> = dao.observeAverageAccuracy()

    fun observeForUnit(unitId: String) = dao.observeForUnit(unitId)
    fun observeBestByUnit(materialId: String) = dao.observeBestByUnit(materialId)

    /** 上传录音评分并落库。返回 (记录, 结果)。 */
    suspend fun assess(unit: UnitEntity, wav: File): Pair<PracticeRecordEntity, AssessResult> {
        val result = api.assess(wav, unit.text)
        val record = PracticeRecordEntity(
            unitId = unit.id,
            materialId = unit.materialId,
            unitText = unit.text,
            createdAt = System.currentTimeMillis(),
            accuracy = result.overall.accuracy,
            completeness = result.overall.completeness,
            fluency = result.overall.fluency,
            resultJson = json.encodeToString(AssessResult.serializer(), result),
            recordingPath = wav.absolutePath,
        )
        val id = dao.insert(record)
        return record.copy(id = id) to result
    }

    /** 练习里的跟读题：只要分数，不落库（练习结果由 PracticeSetRepository 记）。 */
    suspend fun assessText(text: String, wav: File): AssessResult = api.assess(wav, text)

    fun decode(record: PracticeRecordEntity): AssessResult? =
        runCatching { json.decodeFromString(AssessResult.serializer(), record.resultJson) }.getOrNull()

    suspend fun delete(record: PracticeRecordEntity) {
        dao.delete(record.id)
        record.recordingPath?.let { runCatching { File(it).delete() } }
    }
}
