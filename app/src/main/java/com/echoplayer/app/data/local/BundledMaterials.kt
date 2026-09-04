package com.echoplayer.app.data.local

import android.content.Context
import com.echoplayer.app.data.db.MaterialEntity
import com.echoplayer.app.data.db.SegmentEntity
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.model.MaterialStatus
import com.echoplayer.app.data.model.SourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 随 APK 内置的阅读资源，放在 assets/materials/ 下，格式与服务器流水线的
 * 输出（docs/SERVER_API.md `GET /materials/{id}`）保持一致，所以两边共用一套实体。
 */
@Serializable
data class BundledMaterial(
    val id: String,
    val title: String,
    val title_zh: String? = null,
    val description: String? = null,
    val description_zh: String? = null,
    val level: String? = null,
    val topic: String? = null,
    val language: String = "en",
    val source: String? = null,
    val segments: List<BundledSegment>,
)

@Serializable
data class BundledSegment(
    val source_ref: String? = null,
    val units: List<BundledUnit>,
)

@Serializable
data class BundledUnit(
    val text: String,
    val translation: String? = null,
)

data class MaterialBundle(
    val material: MaterialEntity,
    val segments: List<SegmentEntity>,
    val units: List<UnitEntity>,
)

object BundledMaterials {
    private val json = Json { ignoreUnknownKeys = true }

    fun list(context: Context): List<String> {
        val names = context.assets.list("materials")?.toList().orEmpty()
        val index = names.firstOrNull { it == "index.json" }
        if (index != null) {
            val order = json.decodeFromString<List<String>>(readAsset(context, "materials/index.json"))
            return order.filter { it in names }
        }
        return names.filter { it.endsWith(".json") }.sorted()
    }

    fun load(context: Context, fileName: String, sortOrder: Int): MaterialBundle {
        val raw = readAsset(context, "materials/$fileName")
        val parsed = json.decodeFromString<BundledMaterial>(raw)
        return toBundle(parsed, sortOrder)
    }

    fun toBundle(parsed: BundledMaterial, sortOrder: Int, now: Long = System.currentTimeMillis()): MaterialBundle {
        val segments = mutableListOf<SegmentEntity>()
        val units = mutableListOf<UnitEntity>()
        var order = 0
        parsed.segments.forEachIndexed { sIdx, seg ->
            val segId = "${parsed.id}#s${sIdx + 1}"
            segments += SegmentEntity(
                id = segId,
                materialId = parsed.id,
                segmentIndex = sIdx + 1,
                text = seg.units.joinToString(" ") { it.text },
                sourceRef = seg.source_ref,
            )
            seg.units.forEach { u ->
                units += UnitEntity(
                    id = "${parsed.id}#u${order + 1}",
                    materialId = parsed.id,
                    segmentId = segId,
                    orderIndex = order,
                    text = u.text.trim(),
                    translation = u.translation?.trim(),
                    sourceRef = seg.source_ref,
                )
                order++
            }
        }
        val material = MaterialEntity(
            id = parsed.id,
            title = parsed.title,
            titleZh = parsed.title_zh,
            description = parsed.description,
            descriptionZh = parsed.description_zh,
            level = parsed.level,
            topic = parsed.topic,
            language = parsed.language,
            sourceType = SourceType.BUNDLED.id,
            sourceName = parsed.source,
            status = MaterialStatus.READY.id,
            unitCount = units.size,
            createdAt = now,
            sortOrder = sortOrder,
        )
        return MaterialBundle(material, segments, units)
    }

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
