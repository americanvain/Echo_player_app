package com.echoplayer.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一份学习素材（一本书 / 一篇文章）。
 * 对应 Echo_player 里的"一本 PDF"，下面挂 TextSegment 与 UtteranceUnit。
 */
@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey val id: String,
    val title: String,
    val titleZh: String? = null,
    val description: String? = null,
    val descriptionZh: String? = null,
    val level: String? = null,
    val topic: String? = null,
    val language: String = "en",
    val sourceType: String,
    val sourceName: String? = null,
    val sourceUri: String? = null,
    /** 服务器侧的 material_id；导入流水线跑完前用它轮询状态。 */
    val remoteId: String? = null,
    val status: String,
    val statusMessage: String? = null,
    val unitCount: Int = 0,
    val createdAt: Long,
    val lastOpenedAt: Long? = null,
    val lastUnitIndex: Int = 0,
    val sortOrder: Int = 0,
)

/**
 * Echo_player 第一部分的 TextSegment：忠实切分出的连续文本块（段落优先，300~800 词）。
 * 本地导入时一个自然段就是一个 segment；服务器流水线会给出真正的 segment。
 */
@Entity(
    tableName = "segments",
    foreignKeys = [ForeignKey(MaterialEntity::class, ["id"], ["materialId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("materialId")],
)
data class SegmentEntity(
    @PrimaryKey val id: String,
    val materialId: String,
    val segmentIndex: Int,
    val text: String,
    val sourceRef: String? = null,
)

/**
 * Echo_player 第四部分的 UtteranceUnit：最终的学习单元，一句话 + 语音。
 * audioPath 为空时用本机 TTS 朗读；服务器合成的语音下载后填到这里。
 */
@Entity(
    tableName = "units",
    foreignKeys = [ForeignKey(MaterialEntity::class, ["id"], ["materialId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("materialId"), Index(value = ["materialId", "orderIndex"], unique = true)],
)
data class UnitEntity(
    @PrimaryKey val id: String,
    val materialId: String,
    val segmentId: String? = null,
    val orderIndex: Int,
    val text: String,
    val translation: String? = null,
    val sourceRef: String? = null,
    val audioPath: String? = null,
    val audioDuration: Double? = null,
)

/** 一次跟读评分。resultJson 保存服务器返回的完整 AssessResult，便于回看逐音素细节。 */
@Entity(
    tableName = "practice_records",
    foreignKeys = [ForeignKey(UnitEntity::class, ["id"], ["unitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("unitId"), Index("materialId"), Index("createdAt")],
)
data class PracticeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitId: String,
    val materialId: String,
    val unitText: String,
    val createdAt: Long,
    val accuracy: Int,
    val completeness: Int,
    val fluency: Int,
    val resultJson: String,
    val recordingPath: String? = null,
)

/**
 * 问题定位记录：用户在某一句上按下了五层里的哪一层。
 * 这是 Echo_player 第五部分"记录"的最小单元，也是以后喂给大模型生成复习计划的原料。
 */
@Entity(
    tableName = "issues",
    foreignKeys = [ForeignKey(UnitEntity::class, ["id"], ["unitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("unitId"), Index("materialId"), Index("layer"), Index("createdAt")],
)
data class IssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitId: String,
    val materialId: String,
    val unitText: String,
    val layer: Int,
    val note: String? = null,
    /** 教学 Agent 的讲解（接入服务器后写入）。 */
    val explanation: String? = null,
    val resolved: Boolean = false,
    val createdAt: Long,
    val resolvedAt: Long? = null,
    // ---- v2：精确定位 ----
    /** 划选的词范围（闭区间，词索引，按空白切分）；-1 = 整句。 */
    @ColumnInfo(defaultValue = "-1") val spanStart: Int = -1,
    @ColumnInfo(defaultValue = "-1") val spanEnd: Int = -1,
    val spanText: String? = null,
    /** 细分类型 id，逗号分隔（见 ProblemLayer.subtypes）。 */
    @ColumnInfo(defaultValue = "") val subtypes: String = "",
    /** 词形层：听成了什么。 */
    val misheardAs: String? = null,
    /** 程度（Severity.id），0 = 未选择。 */
    @ColumnInfo(defaultValue = "0") val severity: Int = 0,
    /** 前两句快照，脱离素材也能给 AI 提供上下文。 */
    val contextBefore: String? = null,
    val translation: String? = null,
) {
    val subtypeIds: List<String> get() = subtypes.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val isWholeSentence: Boolean get() = spanStart < 0
}

/**
 * 一组针对性练习（Echo_player "根据记录生成特定资源"）。
 * items 以 JSON 保存（PracticeSetDto 的 items），服务器生成与本地生成共用一种结构。
 */
@Entity(tableName = "practice_sets", indices = [Index("createdAt")])
data class PracticeSetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    /** local | server */
    val source: String,
    /** 涉及的层，逗号分隔。 */
    val layers: String = "",
    val itemsJson: String,
    val total: Int,
    val createdAt: Long,
    val lastIndex: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val completedAt: Long? = null,
    /** itemId → 是否答对，JSON 对象。 */
    val resultsJson: String = "{}",
    /** 是否已把结果回传给服务器。 */
    val reported: Boolean = false,
)

/** 生词本条目。word 统一小写做唯一键，displayWord 保留原样。 */
@Entity(
    tableName = "vocab",
    indices = [Index(value = ["word"], unique = true), Index("addedAt")],
)
data class VocabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val displayWord: String,
    val contextSentence: String? = null,
    val contextTranslation: String? = null,
    val unitId: String? = null,
    val materialId: String? = null,
    val materialTitle: String? = null,
    val translation: String? = null,
    val note: String? = null,
    val familiarity: Int = 0,
    val reviewCount: Int = 0,
    val lastReviewedAt: Long? = null,
    val addedAt: Long,
)


/**
 * 听读页里和 AI 的问答（Echo_player 第四、五部分：对话式教学 + 记录疑问）。
 * 每条挂在具体的句子上，也是以后生成复习计划的原料。
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [ForeignKey(UnitEntity::class, ["id"], ["unitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("unitId"), Index("createdAt")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitId: String,
    val materialId: String,
    /** user | assistant */
    val role: String,
    val text: String,
    val createdAt: Long,
    /** 回答是否来自服务器（否则是离线兜底） */
    val fromServer: Boolean = false,
)
