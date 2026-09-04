package com.echoplayer.app.util

/** 句子 → 可点击的词块。保留原始写法用于显示，去掉标点后的 key 用于查生词本与匹配评分。 */
data class WordToken(val display: String, val key: String, val index: Int)

object Words {
    fun tokenize(text: String): List<WordToken> =
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.mapIndexed { i, w -> WordToken(w, normalize(w), i) }

    fun normalize(word: String): String = word.lowercase().trim { !it.isLetterOrDigit() && it != '\'' }

    fun countWords(text: String): Int = text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }

    /** 词典查询 URL。有道词典网页版对中文用户最友好，也能在浏览器里直接打开。 */
    fun dictionaryUrl(word: String): String =
        "https://dict.youdao.com/result?word=" + java.net.URLEncoder.encode(normalize(word), "UTF-8") + "&lang=en"
}
