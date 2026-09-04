package com.echoplayer.app.data.repo

import com.echoplayer.app.data.db.IssueDao
import com.echoplayer.app.data.db.IssueEntity
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.data.remote.EchoServerApi
import com.echoplayer.app.data.remote.ExplainRequest
import com.echoplayer.app.data.remote.ExplainResponse
import com.echoplayer.app.data.remote.SubtypeDto
import kotlinx.coroutines.flow.Flow

class IssueRepository(private val dao: IssueDao, private val api: EchoServerApi) {
    val all: Flow<List<IssueEntity>> = dao.observeAll()
    val countsByLayer = dao.observeCountsByLayer()

    fun observeForUnit(unitId: String) = dao.observeForUnit(unitId)
    fun observeOpenByUnit(materialId: String) = dao.observeOpenByUnit(materialId)

    /** 一条精确定位的问题记录。 */
    data class Draft(
        val layer: ProblemLayer,
        val spanStart: Int = -1,
        val spanEnd: Int = -1,
        val spanText: String? = null,
        val subtypes: List<String> = emptyList(),
        val misheardAs: String? = null,
        val severity: Int = 0,
        val note: String? = null,
    )

    suspend fun record(unit: UnitEntity, context: List<String>, draft: Draft): IssueEntity {
        val issue = IssueEntity(
            unitId = unit.id,
            materialId = unit.materialId,
            unitText = unit.text,
            layer = draft.layer.id,
            note = draft.note?.takeIf { it.isNotBlank() },
            createdAt = System.currentTimeMillis(),
            spanStart = draft.spanStart,
            spanEnd = draft.spanEnd,
            spanText = draft.spanText?.takeIf { it.isNotBlank() },
            subtypes = draft.subtypes.joinToString(","),
            misheardAs = draft.misheardAs?.takeIf { it.isNotBlank() },
            severity = draft.severity,
            contextBefore = context.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            translation = unit.translation,
        )
        val id = dao.insert(issue)
        return issue.copy(id = id)
    }

    suspend fun openIssues(limit: Int = 200) = dao.openIssues(limit)
    val openCount = dao.observeOpenCount()
    suspend fun resolveAll(ids: List<Long>) = dao.resolveAll(ids, System.currentTimeMillis())

    suspend fun setResolved(id: Long, resolved: Boolean) =
        dao.setResolved(id, resolved, if (resolved) System.currentTimeMillis() else null)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun saveExplanation(id: Long, explanation: String) = dao.setExplanation(id, explanation)

    /** 教学 Agent。服务器未接入时抛 ServerException，UI 退回离线讲解模板。 */
    suspend fun explain(unit: UnitEntity, context: List<String>, draft: Draft, history: List<String>): ExplainResponse =
        api.explain(
            ExplainRequest(
                unit_text = unit.text,
                context = context,
                translation = unit.translation,
                layer = draft.layer.id,
                layer_name = draft.layer.titleEn,
                span_text = draft.spanText,
                span_start = draft.spanStart.takeIf { it >= 0 },
                span_end = draft.spanEnd.takeIf { it >= 0 },
                subtypes = draft.subtypes.mapNotNull { id -> draft.layer.subtype(id)?.let { SubtypeDto(it.id, it.promptEn) } },
                misheard_as = draft.misheardAs,
                severity = draft.severity.takeIf { it > 0 },
                note = draft.note,
                history = history,
            )
        )
}
