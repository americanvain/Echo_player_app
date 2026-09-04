package com.echoplayer.app.data.remote

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// speecheval 服务已有的契约（speech_evaluating/src/speecheval/schema.py）
// ---------------------------------------------------------------------------

@Serializable
data class HealthDto(
    val status: String = "",
    val model_id: String = "",
    val device: String = "",
    val scorer: String? = null,
    val torch_threads: Int = 0,
    val n_prompts: Int = 0,
    val startup_sec: Double = 0.0,
)

@Serializable
data class PhoneResult(
    val canonical: String,
    val actual: String? = null,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val gop: Double = 0.0,
    val score: Int = 0,
    val verdict: String = "good",
    val hint: String? = null,
    val stress: Int = 0,
)

@Serializable
data class Insertion(val actual: String, val after_phone_index: Int? = null)

@Serializable
data class WordResult(
    val word: String,
    val score: Int = 0,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val phones: List<PhoneResult> = emptyList(),
    val oov: Boolean = false,
)

@Serializable
data class Overall(val accuracy: Int = 0, val completeness: Int = 0, val fluency: Int = 0)

@Serializable
data class AssessResult(
    val text: String = "",
    val overall: Overall = Overall(),
    val words: List<WordResult> = emptyList(),
    val insertions: List<Insertion> = emptyList(),
    val duration: Double = 0.0,
    val timing_ms: Map<String, Double> = emptyMap(),
)

/** speecheval `/articles` 的结构，用于从服务器同步题库。 */
@Serializable
data class ArticleDto(
    val id: String,
    val title: String,
    val title_zh: String = "",
    val description: String = "",
    val description_zh: String = "",
    val level: String = "",
    val topic: String = "",
    val minutes: Int = 0,
    val paragraphs: List<ArticleParagraphDto> = emptyList(),
)

@Serializable
data class ArticleParagraphDto(val id: String, val text: String, val translation: String = "")

// ---------------------------------------------------------------------------
// Echo_player 流水线（尚未实现，契约见 docs/SERVER_API.md）
// ---------------------------------------------------------------------------

@Serializable
data class ImportResponse(val material_id: String, val status: String = "processing", val message: String? = null)

@Serializable
data class RemoteAudio(val path: String, val duration: Double = 0.0)

@Serializable
data class RemoteUnit(
    val unit_id: String,
    val text: String,
    val translation: String? = null,
    val source_ref: String? = null,
    val audio: RemoteAudio? = null,
)

@Serializable
data class RemoteSegment(
    val segment_id: Int,
    val source_ref: String? = null,
    val text: String? = null,
    val units: List<RemoteUnit> = emptyList(),
)

@Serializable
data class RemoteMaterial(
    val material_id: String,
    val status: String,
    val message: String? = null,
    val title: String? = null,
    val language: String = "en",
    val progress: Double? = null,
    val segments: List<RemoteSegment> = emptyList(),
)

@Serializable
data class TranslateRequest(val text: String, val source: String = "en", val target: String = "zh")

@Serializable
data class TranslateResponse(val translation: String)

@Serializable
data class ExplainRequest(
    val unit_text: String,
    val context: List<String> = emptyList(),
    val translation: String? = null,
    val layer: Int,
    val layer_name: String,
    /** 划选的片段（词索引闭区间）；为空表示整句。 */
    val span_text: String? = null,
    val span_start: Int? = null,
    val span_end: Int? = null,
    /** 细分类型：id + 英文描述，见 ProblemLayer.subtypes。 */
    val subtypes: List<SubtypeDto> = emptyList(),
    val misheard_as: String? = null,
    /** 1 看原文才懂 / 2 听出词没懂 / 3 完全没听出来 */
    val severity: Int? = null,
    val note: String? = null,
    val history: List<String> = emptyList(),
)

@Serializable
data class SubtypeDto(val id: String, val description: String)

@Serializable
data class ExplainResponse(
    val explanation: String,
    val examples: List<String> = emptyList(),
    val quiz: List<QuizItem> = emptyList(),
)

@Serializable
data class QuizItem(val question: String, val answer: String, val options: List<String> = emptyList())


// ---------------------------------------------------------------------------
// 针对性练习（契约见 docs/SERVER_API.md §2.6）
// ---------------------------------------------------------------------------

/** 上传给服务器做分析的一条问题记录。 */
@Serializable
data class IssueUploadDto(
    val id: Long,
    val unit_text: String,
    val context: List<String> = emptyList(),
    val translation: String? = null,
    val layer: Int,
    val layer_name: String,
    val span_text: String? = null,
    val span_start: Int? = null,
    val span_end: Int? = null,
    val subtypes: List<SubtypeDto> = emptyList(),
    val misheard_as: String? = null,
    val severity: Int? = null,
    val note: String? = null,
    val created_at: Long,
)

@Serializable
data class VocabUploadDto(
    val word: String,
    val context: String? = null,
    val translation: String? = null,
    val familiarity: Int = 0,
    val review_count: Int = 0,
)

@Serializable
data class PhoneErrorDto(val word: String, val canonical: String, val actual: String? = null)

@Serializable
data class ScoreUploadDto(
    val unit_text: String,
    val accuracy: Int,
    val fluency: Int,
    val errors: List<PhoneErrorDto> = emptyList(),
    val created_at: Long,
)

@Serializable
data class GeneratePracticeRequest(
    val issues: List<IssueUploadDto> = emptyList(),
    val vocab: List<VocabUploadDto> = emptyList(),
    val scores: List<ScoreUploadDto> = emptyList(),
    val max_sets: Int = 5,
    val language: String = "en",
)

@Serializable
data class GeneratePracticeResponse(val sets: List<PracticeSetDto> = emptyList(), val analysis: String? = null)

/**
 * 一组练习。服务器生成与本地生成共用；items 按 type 决定用哪些字段：
 *
 * | type | 用到的字段 |
 * |---|---|
 * | flashcard | text(词) translation speak explanation vocab_word |
 * | choice | text(含空的句子) speak options answer explanation |
 * | cloze_listen | speak(整句) text(挖空后) blank_start/blank_end options answer |
 * | reorder | chunks(打乱) answer_chunks(正确顺序) translation |
 * | minimal_pair | pair speak(=answer) answer |
 * | shadow | text speak translation |
 * | translation_match | text speak options(中文) answer |
 * | explain | text(讲解) |
 */
@Serializable
data class PracticeSetDto(
    val id: String,
    val title: String,
    val description: String = "",
    val source: String = "local",
    val layers: List<Int> = emptyList(),
    val items: List<PracticeItemDto> = emptyList(),
    val created_at: Long = 0,
)

@Serializable
data class PracticeItemDto(
    val id: String,
    val type: String,
    val prompt: String? = null,
    val text: String? = null,
    val translation: String? = null,
    val speak: String? = null,
    val blank_start: Int? = null,
    val blank_end: Int? = null,
    val options: List<String> = emptyList(),
    val answer: String? = null,
    val chunks: List<String> = emptyList(),
    val answer_chunks: List<String> = emptyList(),
    val pair: List<String> = emptyList(),
    val explanation: String? = null,
    val layer: Int? = null,
    val issue_ids: List<Long> = emptyList(),
    val vocab_word: String? = null,
)

@Serializable
data class PracticeReportRequest(
    val set_id: String,
    val results: List<PracticeItemResultDto>,
    val completed: Boolean,
)

@Serializable
data class PracticeItemResultDto(val item_id: String, val correct: Boolean, val answer: String? = null)

object PracticeTypes {
    const val FLASHCARD = "flashcard"
    const val CHOICE = "choice"
    const val CLOZE_LISTEN = "cloze_listen"
    const val REORDER = "reorder"
    const val MINIMAL_PAIR = "minimal_pair"
    const val SHADOW = "shadow"
    const val TRANSLATION_MATCH = "translation_match"
    const val EXPLAIN = "explain"

    fun label(type: String) = when (type) {
        FLASHCARD -> "闪卡"
        CHOICE -> "选词"
        CLOZE_LISTEN -> "听写填空"
        REORDER -> "句子重组"
        MINIMAL_PAIR -> "辨音"
        SHADOW -> "跟读"
        TRANSLATION_MATCH -> "选译文"
        EXPLAIN -> "讲解"
        else -> type
    }
}
