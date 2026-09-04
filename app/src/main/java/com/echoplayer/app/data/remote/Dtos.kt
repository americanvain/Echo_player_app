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
    val note: String? = null,
    val history: List<String> = emptyList(),
)

@Serializable
data class ExplainResponse(
    val explanation: String,
    val examples: List<String> = emptyList(),
    val quiz: List<QuizItem> = emptyList(),
)

@Serializable
data class QuizItem(val question: String, val answer: String, val options: List<String> = emptyList())
