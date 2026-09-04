package com.echoplayer.app.data.repo

import com.echoplayer.app.data.db.IssueDao
import com.echoplayer.app.data.db.MaterialDao
import com.echoplayer.app.data.db.PracticeDao
import com.echoplayer.app.data.db.PracticeSetDao
import com.echoplayer.app.data.db.PracticeSetEntity
import com.echoplayer.app.data.db.VocabDao
import com.echoplayer.app.data.local.LocalPracticeGenerator
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.data.remote.AssessResult
import com.echoplayer.app.data.remote.EchoServerApi
import com.echoplayer.app.data.remote.GeneratePracticeRequest
import com.echoplayer.app.data.remote.IssueUploadDto
import com.echoplayer.app.data.remote.PhoneErrorDto
import com.echoplayer.app.data.remote.PracticeItemDto
import com.echoplayer.app.data.remote.PracticeItemResultDto
import com.echoplayer.app.data.remote.PracticeReportRequest
import com.echoplayer.app.data.remote.PracticeSetDto
import com.echoplayer.app.data.remote.ScoreUploadDto
import com.echoplayer.app.data.remote.SubtypeDto
import com.echoplayer.app.data.remote.VocabUploadDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 针对性练习：把记录（问题 / 生词 / 评分）交给服务器 AI 生成练习集；
 * 服务器不可用时用 [LocalPracticeGenerator] 按规则生成，结构相同。
 */
class PracticeSetRepository(
    private val dao: PracticeSetDao,
    private val issueDao: IssueDao,
    private val vocabDao: VocabDao,
    private val practiceDao: PracticeDao,
    private val materialDao: MaterialDao,
    private val api: EchoServerApi,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val itemsSerializer = ListSerializer(PracticeItemDto.serializer())
    private val resultsSerializer = MapSerializer(String.serializer(), Boolean.serializer())

    val sets: Flow<List<PracticeSetEntity>> = dao.observeAll()
    val pendingCount: Flow<Int> = dao.observePendingCount()

    fun observe(id: String) = dao.observe(id)
    suspend fun get(id: String) = dao.get(id)
    suspend fun delete(id: String) = dao.delete(id)

    fun items(set: PracticeSetEntity): List<PracticeItemDto> =
        runCatching { json.decodeFromString(itemsSerializer, set.itemsJson) }.getOrDefault(emptyList())

    fun results(set: PracticeSetEntity): Map<String, Boolean> =
        runCatching { json.decodeFromString(resultsSerializer, set.resultsJson) }.getOrDefault(emptyMap())

    data class Outcome(val sets: Int, val items: Int, val fromServer: Boolean, val analysis: String?, val message: String?)

    /** 收集记录 → 服务器（或本地）生成 → 落库。 */
    suspend fun generate(preferLocal: Boolean = false): Outcome = withContext(Dispatchers.IO) {
        val issues = issueDao.openIssues(200)
        val vocab = vocabDao.dueForReview(60)
        val records = practiceDao.recent(80)
        val scores = records.mapNotNull { r -> runCatching { json.decodeFromString(AssessResult.serializer(), r.resultJson) }.getOrNull()?.let { r to it } }
        if (issues.isEmpty() && vocab.isEmpty() && scores.isEmpty()) {
            return@withContext Outcome(0, 0, false, null, "还没有任何记录。先去听读，标记没听懂的地方、收一些生词或跟读几句。")
        }

        var fromServer = false
        var analysis: String? = null
        var message: String? = null
        var generated: List<PracticeSetDto> = emptyList()

        if (!preferLocal && api.isConfigured) {
            val req = GeneratePracticeRequest(
                issues = issues.map { i ->
                    val layer = ProblemLayer.fromId(i.layer)
                    IssueUploadDto(
                        id = i.id, unit_text = i.unitText,
                        context = i.contextBefore?.split('\n').orEmpty(),
                        translation = i.translation, layer = i.layer, layer_name = layer.titleEn,
                        span_text = i.spanText, span_start = i.spanStart.takeIf { it >= 0 }, span_end = i.spanEnd.takeIf { it >= 0 },
                        subtypes = i.subtypeIds.mapNotNull { id -> layer.subtype(id)?.let { SubtypeDto(it.id, it.promptEn) } },
                        misheard_as = i.misheardAs, severity = i.severity.takeIf { it > 0 }, note = i.note, created_at = i.createdAt,
                    )
                },
                vocab = vocab.map { VocabUploadDto(it.word, it.contextSentence, it.translation, it.familiarity, it.reviewCount) },
                scores = scores.map { (r, a) ->
                    ScoreUploadDto(
                        unit_text = r.unitText, accuracy = r.accuracy, fluency = r.fluency, created_at = r.createdAt,
                        errors = a.words.flatMap { w -> w.phones.filter { it.verdict == "error" }.map { PhoneErrorDto(w.word, it.canonical, it.actual) } },
                    )
                },
            )
            runCatching { api.generatePractice(req) }
                .onSuccess { resp ->
                    generated = resp.sets.map { it.copy(source = "server", created_at = if (it.created_at == 0L) System.currentTimeMillis() else it.created_at) }
                    analysis = resp.analysis
                    fromServer = true
                }
                .onFailure { message = "服务器生成失败（${it.message}），已改用本机规则生成" }
        }
        if (!fromServer) {
            val unitsById = HashMap<String, com.echoplayer.app.data.db.UnitEntity>()
            val unitsByMaterial = HashMap<String, List<com.echoplayer.app.data.db.UnitEntity>>()
            val materialIds = (issues.map { it.materialId } + records.map { it.materialId } + vocab.mapNotNull { it.materialId }).toSet()
            materialIds.forEach { mid ->
                val us = materialDao.units(mid)
                unitsByMaterial[mid] = us
                us.forEach { unitsById[it.id] = it }
            }
            generated = LocalPracticeGenerator().generate(LocalPracticeGenerator.Input(issues, vocab, scores, unitsById, unitsByMaterial))
            if (generated.isEmpty()) message = message ?: "记录里还没有可以出题的内容：划选具体位置的问题、生词、或跟读评分都能生成练习"
        }
        val entities = generated.filter { it.items.isNotEmpty() }.map { toEntity(it) }
        if (entities.isNotEmpty()) dao.insertAll(entities)
        Outcome(entities.size, entities.sumOf { it.total }, fromServer, analysis, message)
    }

    private fun toEntity(dto: PracticeSetDto) = PracticeSetEntity(
        id = dto.id,
        title = dto.title,
        description = dto.description,
        source = dto.source,
        layers = dto.layers.joinToString(","),
        itemsJson = json.encodeToString(itemsSerializer, dto.items),
        total = dto.items.size,
        createdAt = if (dto.created_at == 0L) System.currentTimeMillis() else dto.created_at,
    )

    suspend fun saveProgress(set: PracticeSetEntity, index: Int, results: Map<String, Boolean>) {
        val correct = results.count { it.value }
        val wrong = results.size - correct
        val done = index >= set.total
        val updated = set.copy(
            lastIndex = index,
            correct = correct,
            wrong = wrong,
            resultsJson = json.encodeToString(resultsSerializer, results),
            completedAt = if (done) (set.completedAt ?: System.currentTimeMillis()) else set.completedAt,
        )
        dao.update(updated)
        if (done) {
            // 全部答对的题所关联的问题记录 → 标记已解决
            val items = items(set)
            val solved = items.filter { results[it.id] == true }.flatMap { it.issue_ids }.distinct()
            if (solved.isNotEmpty()) issueDao.resolveAll(solved, System.currentTimeMillis())
            report(updated, results, completed = true)
        }
    }

    suspend fun resetProgress(set: PracticeSetEntity) = dao.update(set.copy(lastIndex = 0, correct = 0, wrong = 0, resultsJson = "{}", completedAt = null, reported = false))

    private suspend fun report(set: PracticeSetEntity, results: Map<String, Boolean>, completed: Boolean) {
        if (!api.isConfigured || set.source != "server") return
        runCatching {
            api.reportPractice(PracticeReportRequest(set.id, results.map { PracticeItemResultDto(it.key, it.value) }, completed))
            dao.update(set.copy(reported = true))
        }
    }
}
