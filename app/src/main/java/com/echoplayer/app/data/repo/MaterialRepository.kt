package com.echoplayer.app.data.repo

import android.content.Context
import android.net.Uri
import com.echoplayer.app.data.db.MaterialDao
import com.echoplayer.app.data.db.MaterialEntity
import com.echoplayer.app.data.db.MaterialWithProgress
import com.echoplayer.app.data.db.SegmentEntity
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.local.BundledMaterial
import com.echoplayer.app.data.local.BundledMaterials
import com.echoplayer.app.data.local.BundledSegment
import com.echoplayer.app.data.local.BundledUnit
import com.echoplayer.app.data.local.TextImporter
import com.echoplayer.app.data.model.MaterialStatus
import com.echoplayer.app.data.model.SourceType
import com.echoplayer.app.data.remote.EchoServerApi
import com.echoplayer.app.data.remote.RemoteMaterial
import com.echoplayer.app.data.remote.ServerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MaterialRepository(
    private val context: Context,
    private val dao: MaterialDao,
    private val api: EchoServerApi,
) {
    val library: Flow<List<MaterialWithProgress>> = dao.observeLibrary()

    fun observe(id: String): Flow<MaterialEntity?> = dao.observe(id)
    fun observeUnits(id: String): Flow<List<UnitEntity>> = dao.observeUnits(id)
    suspend fun get(id: String) = dao.get(id)
    suspend fun units(id: String) = dao.units(id)
    suspend fun unit(id: String) = dao.unit(id)

    /** 首次启动把 assets 里的内置素材写入数据库；之后只补缺失的（升级新增资源）。 */
    suspend fun seedBundled() = withContext(Dispatchers.IO) {
        val existing = dao.bundledIds().toSet()
        BundledMaterials.list(context).forEachIndexed { idx, name ->
            val bundle = runCatching { BundledMaterials.load(context, name, idx) }.getOrNull() ?: return@forEachIndexed
            if (bundle.material.id !in existing) {
                dao.replaceContent(bundle.material, bundle.segments, bundle.units)
            }
        }
    }

    suspend fun updateProgress(materialId: String, unitIndex: Int) =
        dao.updateProgress(materialId, unitIndex, System.currentTimeMillis())

    suspend fun delete(materialId: String) = withContext(Dispatchers.IO) {
        val m = dao.get(materialId)
        dao.delete(materialId)
        File(context.filesDir, "audio/$materialId").deleteRecursively()
        m
    }

    // ---- 导入 -------------------------------------------------------------------

    /** TXT：完全本地处理。 */
    suspend fun importTxt(uri: Uri): MaterialEntity = withContext(Dispatchers.IO) {
        val name = TextImporter.displayName(context, uri)
        val text = TextImporter.readText(context, uri)
        val bundle = TextImporter.build(text, TextImporter.titleFromName(name), name, uri.toString())
        dao.replaceContent(bundle.material, bundle.segments, bundle.units)
        bundle.material
    }

    /** 直接从文本创建（分享进来的纯文本、粘贴）。 */
    suspend fun importPlainText(text: String, title: String): MaterialEntity = withContext(Dispatchers.IO) {
        val bundle = TextImporter.build(text, title, null, null)
        dao.replaceContent(bundle.material, bundle.segments, bundle.units)
        bundle.material
    }

    /**
     * PDF：交给服务器流水线（OCR → 分段 → 切句 → TTS）。本地先建一个 PROCESSING 的占位素材，
     * 上传成功后记下 remoteId，之后靠 [refreshRemote] 轮询直到 READY。
     */
    suspend fun importPdf(uri: Uri): MaterialEntity = withContext(Dispatchers.IO) {
        val name = TextImporter.displayName(context, uri) ?: "document.pdf"
        val title = TextImporter.titleFromName(name)
        val id = "pdf-" + UUID.randomUUID().toString().take(8)
        val placeholder = MaterialEntity(
            id = id,
            title = title,
            sourceType = SourceType.PDF.id,
            sourceName = name,
            sourceUri = uri.toString(),
            status = MaterialStatus.PROCESSING.id,
            statusMessage = "正在上传到服务器…",
            createdAt = System.currentTimeMillis(),
        )
        dao.insert(placeholder)
        try {
            val tmp = File(context.cacheDir, "upload-$id.pdf")
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: throw IllegalStateException("读不到这个文件")
            val resp = api.importMaterial(tmp, name, "application/pdf", title)
            tmp.delete()
            val updated = placeholder.copy(
                remoteId = resp.material_id,
                status = MaterialStatus.PROCESSING.id,
                statusMessage = resp.message ?: "服务器正在识别并切分句子…",
            )
            dao.update(updated)
            updated
        } catch (e: Exception) {
            val msg = when (e) {
                is ServerException -> e.message ?: "服务器错误"
                else -> e.message ?: e.javaClass.simpleName
            }
            val failed = placeholder.copy(status = MaterialStatus.FAILED.id, statusMessage = msg)
            dao.update(failed)
            failed
        }
    }

    /** 轮询服务器流水线状态；READY 时把句子与语音拉到本地。 */
    suspend fun refreshRemote(materialId: String): MaterialEntity? = withContext(Dispatchers.IO) {
        val m = dao.get(materialId) ?: return@withContext null
        val remoteId = m.remoteId ?: return@withContext m
        val remote = try {
            api.material(remoteId)
        } catch (e: Exception) {
            dao.updateStatus(m.id, m.status, "查询状态失败：${e.message}", m.unitCount)
            return@withContext dao.get(materialId)
        }
        when (remote.status) {
            "ready" -> {
                applyRemote(m, remote)
                dao.get(materialId)
            }
            "failed" -> {
                dao.updateStatus(m.id, MaterialStatus.FAILED.id, remote.message ?: "服务器处理失败", 0)
                dao.get(materialId)
            }
            else -> {
                val pct = remote.progress?.let { " ${(it * 100).toInt()}%" } ?: ""
                dao.updateStatus(m.id, MaterialStatus.PROCESSING.id, (remote.message ?: "服务器处理中") + pct, 0)
                dao.get(materialId)
            }
        }
    }

    private suspend fun applyRemote(m: MaterialEntity, remote: RemoteMaterial) {
        val segments = mutableListOf<SegmentEntity>()
        val units = mutableListOf<UnitEntity>()
        var order = 0
        remote.segments.forEach { seg ->
            val segId = "${m.id}#s${seg.segment_id}"
            segments += SegmentEntity(
                id = segId, materialId = m.id, segmentIndex = seg.segment_id,
                text = seg.text ?: seg.units.joinToString(" ") { it.text }, sourceRef = seg.source_ref,
            )
            seg.units.forEach { u ->
                val unitId = "${m.id}#u${u.unit_id}"
                var localAudio: String? = null
                var duration: Double? = u.audio?.duration
                if (u.audio != null) {
                    val dest = File(context.filesDir, "audio/${m.id}/${u.unit_id}.wav")
                    runCatching { api.downloadAudio(remote.material_id, u.unit_id, dest) }
                        .onSuccess { localAudio = dest.absolutePath }
                        .onFailure { duration = null }
                }
                units += UnitEntity(
                    id = unitId, materialId = m.id, segmentId = segId, orderIndex = order,
                    text = u.text, translation = u.translation, sourceRef = u.source_ref,
                    audioPath = localAudio, audioDuration = duration,
                )
                order++
            }
        }
        val ready = m.copy(
            title = remote.title ?: m.title,
            language = remote.language,
            status = if (units.isEmpty()) MaterialStatus.FAILED.id else MaterialStatus.READY.id,
            statusMessage = if (units.isEmpty()) "服务器没有返回任何句子" else null,
            unitCount = units.size,
        )
        dao.replaceContent(ready, segments, units)
    }

    /** 从 speecheval `/articles` 同步文章题库。 */
    suspend fun syncRemoteArticles(): Int = withContext(Dispatchers.IO) {
        val articles = api.articles()
        var n = 0
        articles.forEachIndexed { idx, a ->
            val parsed = BundledMaterial(
                id = "remote-${a.id}",
                title = a.title,
                title_zh = a.title_zh.ifBlank { null },
                description = a.description.ifBlank { null },
                description_zh = a.description_zh.ifBlank { null },
                level = a.level.ifBlank { null },
                topic = a.topic.ifBlank { null },
                source = "服务器题库",
                segments = listOf(BundledSegment(units = a.paragraphs.map { BundledUnit(it.text, it.translation.ifBlank { null }) })),
            )
            val bundle = BundledMaterials.toBundle(parsed, 100 + idx)
            val existing = dao.get(bundle.material.id)
            val material = bundle.material.copy(
                sourceType = SourceType.REMOTE.id,
                lastOpenedAt = existing?.lastOpenedAt,
                lastUnitIndex = existing?.lastUnitIndex ?: 0,
                createdAt = existing?.createdAt ?: bundle.material.createdAt,
            )
            dao.replaceContent(material, bundle.segments, bundle.units)
            n++
        }
        n
    }

    // ---- 翻译 -------------------------------------------------------------------

    /** 翻译任意一段文字（点词查释义用），不落库。 */
    suspend fun translateText(text: String): String = api.translate(text).translation

    suspend fun translateUnit(unit: UnitEntity): String {
        val t = api.translate(unit.text).translation
        dao.updateTranslation(unit.id, t)
        return t
    }
}
