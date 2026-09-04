package com.echoplayer.app.data.model

/** 素材来源。bundled = 随 APK 内置；txt/pdf = 用户导入；remote = 服务器题库。 */
enum class SourceType(val id: String, val label: String) {
    BUNDLED("bundled", "内置"),
    TXT("txt", "TXT"),
    PDF("pdf", "PDF"),
    REMOTE("remote", "服务器");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: BUNDLED
    }
}

/**
 * 素材处理状态。导入 PDF 后要等服务器把它切成句子并合成语音，
 * 期间素材以 PROCESSING 显示在书架上；失败保留 FAILED 与原因，可重试。
 */
enum class MaterialStatus(val id: String, val label: String) {
    READY("ready", "可用"),
    PROCESSING("processing", "处理中"),
    FAILED("failed", "失败");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: READY
    }
}

/** 生词熟悉度：0 陌生、1 模糊、2 掌握。 */
object Familiarity {
    const val NEW = 0
    const val FUZZY = 1
    const val KNOWN = 2

    fun label(v: Int) = when (v) {
        KNOWN -> "掌握"
        FUZZY -> "模糊"
        else -> "陌生"
    }
}
