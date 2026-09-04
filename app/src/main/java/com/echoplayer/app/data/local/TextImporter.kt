package com.echoplayer.app.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.echoplayer.app.data.db.MaterialEntity
import com.echoplayer.app.data.db.SegmentEntity
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.model.MaterialStatus
import com.echoplayer.app.data.model.SourceType
import java.util.UUID

/** 本地 TXT 导入：读文件 → 分段 → 切句，全部离线完成。 */
object TextImporter {

    fun displayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    fun readText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("读不到这个文件")
        return decode(bytes)
    }

    /** UTF-8 优先；解不开就退回 GBK（中文 Windows 上的 txt 常见）。 */
    fun decode(bytes: ByteArray): String {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes.copyOfRange(3, bytes.size)
        } else bytes
        val utf8 = String(body, Charsets.UTF_8)
        if (!utf8.contains('�')) return utf8
        return runCatching { String(body, charset("GBK")) }.getOrDefault(utf8)
    }

    fun build(text: String, title: String, sourceName: String?, sourceUri: String?): MaterialBundle {
        val id = "txt-" + UUID.randomUUID().toString().take(8)
        val now = System.currentTimeMillis()
        val paragraphs = SentenceSplitter.paragraphs(text)
        val segments = mutableListOf<SegmentEntity>()
        val units = mutableListOf<UnitEntity>()
        var order = 0
        paragraphs.forEachIndexed { pIdx, para ->
            val sentences = SentenceSplitter.sentences(para)
            if (sentences.isEmpty()) return@forEachIndexed
            val segId = "$id#s${pIdx + 1}"
            segments += SegmentEntity(id = segId, materialId = id, segmentIndex = pIdx + 1, text = para, sourceRef = sourceName)
            sentences.forEach { s ->
                units += UnitEntity(
                    id = "$id#u${order + 1}",
                    materialId = id,
                    segmentId = segId,
                    orderIndex = order,
                    text = s,
                    sourceRef = sourceName,
                )
                order++
            }
        }
        val material = MaterialEntity(
            id = id,
            title = title,
            sourceType = SourceType.TXT.id,
            sourceName = sourceName,
            sourceUri = sourceUri,
            status = if (units.isEmpty()) MaterialStatus.FAILED.id else MaterialStatus.READY.id,
            statusMessage = if (units.isEmpty()) "文件里没有可以学习的句子" else null,
            unitCount = units.size,
            createdAt = now,
        )
        return MaterialBundle(material, segments, units)
    }

    fun titleFromName(name: String?): String {
        val n = name?.substringBeforeLast('.')?.replace('_', ' ')?.replace('-', ' ')?.trim().orEmpty()
        return n.ifEmpty { "导入的文本" }
    }
}
