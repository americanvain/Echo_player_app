package com.echoplayer.app.util

/** 句子 → 可点击的词块。保留原始写法用于显示，去掉标点后的 key 用于查生词本与匹配评分。 */
data class WordToken(val display: String, val key: String, val index: Int)

object Words {
    fun tokenize(text: String): List<WordToken> =
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.mapIndexed { i, w -> WordToken(w, normalize(w), i) }

    fun normalize(word: String): String = word.lowercase().trim { !it.isLetterOrDigit() && it != '\'' }

    fun countWords(text: String): Int = text.split(Regex("\\s+")).count { it.any(Char::isLetterOrDigit) }

    /** 词在 [display] 拼回的整串里的字符区间，用于文字布局上的命中测试与遮盖。 */
    fun display(tokens: List<WordToken>): String = tokens.joinToString(" ") { it.display }

    fun charRanges(tokens: List<WordToken>): List<IntRange> {
        val out = ArrayList<IntRange>(tokens.size)
        var pos = 0
        tokens.forEachIndexed { i, tok ->
            if (i > 0) pos += 1
            out.add(pos until pos + tok.display.length)
            pos += tok.display.length
        }
        return out
    }

    /** 字符偏移落在第几个词上；不在任何词上返回 -1。 */
    fun wordIndexAt(ranges: List<IntRange>, offset: Int): Int = ranges.indexOfFirst { offset in it }

    /** 词典查询 URL。有道词典网页版对中文用户最友好，也能在浏览器里直接打开。 */
    fun dictionaryUrl(word: String): String =
        "https://dict.youdao.com/result?word=" + java.net.URLEncoder.encode(normalize(word), "UTF-8") + "&lang=en"
}
