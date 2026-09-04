package com.echoplayer.app.data.local

/**
 * 本地句子切分器（Echo_player 第二部分 TextSegment → CandidateSentence 的离线兜底）。
 *
 * 原则与 first_step.md 一致：只使用原文，不改写、不重排、不合成；
 * 过长的句子完整保留，过短的（拟声词、残句）也按顺序输出，让用户用"下一句"快速跳过。
 * 服务器流水线（LLM 辅助切分）上线后，本地切分只在离线导入 TXT 时使用。
 */
object SentenceSplitter {

    /** 称谓类缩写：后面必然跟名字，句点永远不是句子结束。 */
    private val titles = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "mt", "gen", "col", "capt", "lt", "sgt", "rev", "hon",
        "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec",
    )

    /** 其他缩写：通常不是句子结束，但后面紧跟大写开头的词时按句子结束处理（"at 5 p.m. They left."）。 */
    private val abbreviations = setOf(
        "vs", "etc", "e.g", "i.e", "a.m", "p.m", "no", "vol", "fig", "ch", "sec", "dept", "est", "inc", "ltd", "co",
        "u.s", "u.k", "u.s.a",
    )

    private val closers = "\"'”’)]»"

    /** 把整篇文本切成段落；空行分段，段内的单个换行视为软换行。没有空行时按行分段。 */
    fun paragraphs(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ')
        val blocks = if (normalized.contains(Regex("\n[ \t]*\n"))) {
            normalized.split(Regex("\n[ \t]*\n+"))
        } else {
            normalized.split('\n')
        }
        return blocks
            .map { block -> block.split('\n').joinToString(" ") { it.trim() }.replace(Regex("[ \t]+"), " ").trim() }
            .filter { it.isNotEmpty() }
    }

    /** 把一个段落切成句子，保持原文顺序与内容。 */
    fun sentences(paragraph: String): List<String> {
        val text = paragraph.trim()
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            current.append(c)
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                // 吞掉连续的终止符（"?!", "..."）
                var j = i + 1
                while (j < text.length && (text[j] == '.' || text[j] == '!' || text[j] == '?' || text[j] == '…')) {
                    current.append(text[j]); j++
                }
                // 吞掉紧跟的引号 / 括号
                while (j < text.length && closers.indexOf(text[j]) >= 0) {
                    current.append(text[j]); j++
                }
                val atEnd = j >= text.length
                val followedBySpace = !atEnd && text[j].isWhitespace()
                if (atEnd || (followedBySpace && isBoundary(current, text, j))) {
                    out.add(current.toString().trim())
                    current.setLength(0)
                    // 跳过空白
                    while (j < text.length && text[j].isWhitespace()) j++
                }
                i = j
                continue
            }
            i++
        }
        if (current.isNotBlank()) out.add(current.toString().trim())
        return out.filter { it.isNotBlank() }
    }

    /** 整篇文本 → 段落 → 句子。返回 (段落索引, 句子) 列表。 */
    fun split(text: String): List<Pair<Int, String>> =
        paragraphs(text).flatMapIndexed { idx, para -> sentences(para).map { idx to it } }

    private fun isBoundary(current: StringBuilder, text: String, next: Int): Boolean {
        val s = current.toString()
        // 只有句点结尾时才需要检查缩写 / 数字
        val lastPunctIdx = s.indexOfLast { it == '.' || it == '!' || it == '?' || it == '…' }
        if (lastPunctIdx < 0) return true
        if (s[lastPunctIdx] != '.') return true
        // "3.14" 这类小数：句点前后都是数字
        val before = s.substring(0, lastPunctIdx)
        val lastWord = before.takeLastWhile { !it.isWhitespace() && it != '(' && it != '"' && it != '“' }
        val lower = lastWord.lowercase().trimEnd('.')
        if (lower in titles) return false
        // 单个大写字母缩写（"J. K. Rowling"）
        if (lastWord.length == 1 && lastWord[0].isUpperCase()) return false
        var k = next
        while (k < text.length && text[k].isWhitespace()) k++
        val nextChar = if (k < text.length) text[k] else null
        if (lower in abbreviations) return nextChar != null && nextChar.isUpperCase()
        // 下一个非空字符是小写字母 → 多半不是句子边界（例如 "Wait... what?"）
        if (nextChar != null && nextChar.isLowerCase()) return false
        return true
    }
}
