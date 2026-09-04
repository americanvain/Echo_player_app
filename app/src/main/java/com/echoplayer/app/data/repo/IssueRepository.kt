package com.echoplayer.app.data.repo

import com.echoplayer.app.data.db.IssueDao
import com.echoplayer.app.data.db.IssueEntity
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.data.remote.EchoServerApi
import com.echoplayer.app.data.remote.ExplainRequest
import com.echoplayer.app.data.remote.ExplainResponse
import kotlinx.coroutines.flow.Flow

class IssueRepository(private val dao: IssueDao, private val api: EchoServerApi) {
    val all: Flow<List<IssueEntity>> = dao.observeAll()
    val countsByLayer = dao.observeCountsByLayer()

    fun observeForUnit(unitId: String) = dao.observeForUnit(unitId)
    fun observeOpenByUnit(materialId: String) = dao.observeOpenByUnit(materialId)

    suspend fun record(unit: UnitEntity, layer: ProblemLayer, note: String?): IssueEntity {
        val issue = IssueEntity(
            unitId = unit.id,
            materialId = unit.materialId,
            unitText = unit.text,
            layer = layer.id,
            note = note?.takeIf { it.isNotBlank() },
            createdAt = System.currentTimeMillis(),
        )
        val id = dao.insert(issue)
        return issue.copy(id = id)
    }

    suspend fun setResolved(id: Long, resolved: Boolean) =
        dao.setResolved(id, resolved, if (resolved) System.currentTimeMillis() else null)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun saveExplanation(id: Long, explanation: String) = dao.setExplanation(id, explanation)

    /** 教学 Agent。服务器未接入时抛 ServerException，UI 退回离线讲解模板。 */
    suspend fun explain(unit: UnitEntity, context: List<String>, layer: ProblemLayer, note: String?, history: List<String>): ExplainResponse =
        api.explain(
            ExplainRequest(
                unit_text = unit.text,
                context = context,
                translation = unit.translation,
                layer = layer.id,
                layer_name = layer.titleEn,
                note = note,
                history = history,
            )
        )
}
