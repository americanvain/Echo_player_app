package com.echoplayer.app.data.local

import com.echoplayer.app.data.db.IssueEntity
import com.echoplayer.app.data.db.PracticeRecordEntity
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.db.VocabEntity
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.data.remote.AssessResult
import com.echoplayer.app.data.remote.PracticeItemDto
import com.echoplayer.app.data.remote.PracticeSetDto
import com.echoplayer.app.data.remote.PracticeTypes
import com.echoplayer.app.util.Words
import kotlin.random.Random

/**
 * 服务器不可用时的本地练习生成：从用户自己的问题记录、生词、跟读评分里按规则出题。
 *
 * 题型对照商用产品：闪卡（Anki / 百词斩）、选词填空与听写填空（每日英语听力 / 多邻国）、
 * 句子重组（多邻国）、辨音对（ELSA）、跟读评分（流利说）、选译文（多邻国）。
 * 服务器上线后由 AI 依据同一份记录生成，结构完全一致（PracticeSetDto）。
 */
class LocalPracticeGenerator(private val random: Random = Random.Default) {

    data class Input(
        val issues: List<IssueEntity>,
        val vocab: List<VocabEntity>,
        val scores: List<Pair<PracticeRecordEntity, AssessResult>>,
        val unitsById: Map<String, UnitEntity>,
        val unitsByMaterial: Map<String, List<UnitEntity>>,
    )

    private var counter = 0
    private fun nextId(prefix: String) = "$prefix-${System.currentTimeMillis() % 100000}-${counter++}"

    fun generate(input: Input, maxPerSet: Int = 12): List<PracticeSetDto> {
        counter = 0
        val now = System.currentTimeMillis()
        val sets = mutableListOf<PracticeSetDto>()
        val byLayer = input.issues.groupBy { it.layer }

        // ① 语音层：听写填空 + 辨音 + 跟读
        run {
            val items = mutableListOf<PracticeItemDto>()
            byLayer[1].orEmpty().forEach { issue -> clozeListen(issue, input)?.let { items += it } }
            items += minimalPairs(input.scores)
            items += shadowItems(input.scores, input)
            if (items.isNotEmpty()) sets += set("听音辨词", "针对你标记的连读、弱读和读错的音", listOf(1), items.take(maxPerSet), now)
        }
        // ② 词形层：听音选词
        run {
            val items = byLayer[2].orEmpty().mapNotNull { listenChoice(it, input) }
            if (items.isNotEmpty()) sets += set("听出是哪个词", "把听成别的词、没反应过来的词再听几遍", listOf(2), items.take(maxPerSet), now)
        }
        // ③ 词义层：闪卡 + 选词填空
        run {
            val items = mutableListOf<PracticeItemDto>()
            input.vocab.filter { it.familiarity < 2 }.forEach { items += flashcard(it) }
            byLayer[3].orEmpty().forEach { issue -> wordChoice(issue, input)?.let { items += it } }
            if (items.isNotEmpty()) sets += set("生词与词义", "生词本 + 你标记为不懂意思的词和短语", listOf(3), items.take(maxPerSet), now)
        }
        // ④ 句法层：句子重组 + 选译文
        run {
            val items = mutableListOf<PracticeItemDto>()
            byLayer[4].orEmpty().forEach { issue ->
                reorder(issue)?.let { items += it }
                translationMatch(issue, input)?.let { items += it }
            }
            if (items.isNotEmpty()) sets += set("句子结构", "把走偏的长句拆开再拼回去", listOf(4), items.take(maxPerSet), now)
        }
        // ⑤ 语义层：选译文 + 听写整句里的关键片段
        run {
            val items = mutableListOf<PracticeItemDto>()
            byLayer[5].orEmpty().forEach { issue ->
                translationMatch(issue, input)?.let { items += it }
                if (!issue.isWholeSentence) clozeListen(issue, input)?.let { items += it }
            }
            if (items.isNotEmpty()) sets += set("整句意思", "每个词都懂但整句不对的那些句子", listOf(5), items.take(maxPerSet), now)
        }
        return sets
    }

    private fun set(title: String, desc: String, layers: List<Int>, items: List<PracticeItemDto>, now: Long) =
        PracticeSetDto(id = nextId("local"), title = title, description = desc, source = "local", layers = layers, items = items, created_at = now)

    // ---- 题目生成 ------------------------------------------------------------------

    private fun spanOf(issue: IssueEntity): Pair<Int, Int>? {
        val words = Words.tokenize(issue.unitText)
        if (issue.isWholeSentence || words.isEmpty()) return null
        val s = issue.spanStart.coerceIn(0, words.size - 1)
        val e = issue.spanEnd.coerceIn(s, words.size - 1)
        return s to e
    }

    private fun spanText(issue: IssueEntity, span: Pair<Int, Int>): String =
        Words.tokenize(issue.unitText).subList(span.first, span.second + 1).joinToString(" ") { it.display }

    private fun blanked(text: String, span: Pair<Int, Int>): String {
        val words = Words.tokenize(text)
        return words.joinToString(" ") { if (it.index in span.first..span.second) "____" else it.display }
    }

    /** 听整句，填上划选的那一段。 */
    fun clozeListen(issue: IssueEntity, input: Input): PracticeItemDto? {
        val span = spanOf(issue) ?: return null
        val answer = spanText(issue, span)
        val distractors = spanDistractors(issue, span, input, 3)
        return PracticeItemDto(
            id = nextId("cloze"),
            type = PracticeTypes.CLOZE_LISTEN,
            prompt = "听一遍，填上空缺的部分",
            text = blanked(issue.unitText, span),
            speak = issue.unitText,
            translation = issue.translation,
            blank_start = span.first, blank_end = span.second,
            options = (distractors + answer).shuffled(random),
            answer = answer,
            explanation = issue.note,
            layer = issue.layer,
            issue_ids = listOf(issue.id),
        )
    }

    /** 词形层：听音选词，"听成了 X" 直接进干扰项。 */
    fun listenChoice(issue: IssueEntity, input: Input): PracticeItemDto? {
        val span = spanOf(issue) ?: return null
        val answer = spanText(issue, span)
        val distractors = mutableListOf<String>()
        issue.misheardAs?.takeIf { it.isNotBlank() && Words.normalize(it) != Words.normalize(answer) }?.let { distractors += it }
        distractors += spanDistractors(issue, span, input, 3 - distractors.size)
        return PracticeItemDto(
            id = nextId("choice"),
            type = PracticeTypes.CHOICE,
            prompt = "听到的是哪个？",
            text = blanked(issue.unitText, span),
            speak = issue.unitText,
            translation = issue.translation,
            blank_start = span.first, blank_end = span.second,
            options = (distractors.distinct() + answer).shuffled(random),
            answer = answer,
            explanation = issue.misheardAs?.let { "你当时听成了「$it」，实际是「$answer」" },
            layer = issue.layer,
            issue_ids = listOf(issue.id),
        )
    }

    /** 词义层：看句子选词（不放音，考意义）。 */
    fun wordChoice(issue: IssueEntity, input: Input): PracticeItemDto? {
        val span = spanOf(issue) ?: return null
        val answer = spanText(issue, span)
        val distractors = spanDistractors(issue, span, input, 3)
        if (distractors.isEmpty()) return null
        return PracticeItemDto(
            id = nextId("wchoice"),
            type = PracticeTypes.CHOICE,
            prompt = "哪个词放进去意思通顺？",
            text = blanked(issue.unitText, span),
            translation = issue.translation,
            blank_start = span.first, blank_end = span.second,
            options = (distractors + answer).shuffled(random),
            answer = answer,
            explanation = issue.translation?.let { "整句意思：$it" },
            layer = issue.layer,
            issue_ids = listOf(issue.id),
        )
    }

    fun flashcard(v: VocabEntity): PracticeItemDto = PracticeItemDto(
        id = nextId("card"),
        type = PracticeTypes.FLASHCARD,
        prompt = "想一想意思，再翻开",
        text = v.displayWord.ifBlank { v.word },
        speak = v.word,
        translation = listOfNotNull(v.translation, v.contextSentence, v.contextTranslation).joinToString("\n"),
        explanation = v.note,
        layer = 3,
        vocab_word = v.word,
    )

    /** 句法层：按标点和短语切块、打乱。 */
    fun reorder(issue: IssueEntity): PracticeItemDto? {
        val chunks = chunk(issue.unitText)
        if (chunks.size < 3) return null
        var shuffled = chunks.shuffled(random)
        var guard = 0
        while (shuffled == chunks && guard++ < 5) shuffled = chunks.shuffled(random)
        return PracticeItemDto(
            id = nextId("reorder"),
            type = PracticeTypes.REORDER,
            prompt = "把句子按正确顺序拼回去",
            text = issue.unitText,
            translation = issue.translation,
            speak = issue.unitText,
            chunks = shuffled,
            answer_chunks = chunks,
            explanation = issue.note,
            layer = issue.layer,
            issue_ids = listOf(issue.id),
        )
    }

    /** 选出正确的中文译文；干扰项来自同一素材的其他句子。 */
    fun translationMatch(issue: IssueEntity, input: Input): PracticeItemDto? {
        val correct = issue.translation?.takeIf { it.isNotBlank() } ?: return null
        val unit = input.unitsById[issue.unitId]
        val pool = (unit?.let { input.unitsByMaterial[it.materialId] } ?: input.unitsById.values.toList())
            .mapNotNull { it.translation }.filter { it.isNotBlank() && it != correct }.distinct()
        if (pool.size < 2) return null
        val distractors = pool.shuffled(random).take(2)
        return PracticeItemDto(
            id = nextId("tm"),
            type = PracticeTypes.TRANSLATION_MATCH,
            prompt = "这句话是什么意思？",
            text = issue.unitText,
            speak = issue.unitText,
            options = (distractors + correct).shuffled(random),
            answer = correct,
            explanation = issue.note,
            layer = issue.layer,
            issue_ids = listOf(issue.id),
        )
    }

    /** 跟读评分里的音素错误 → 辨音对。 */
    fun minimalPairs(scores: List<Pair<PracticeRecordEntity, AssessResult>>): List<PracticeItemDto> {
        val seen = HashSet<Set<String>>()
        val out = mutableListOf<PracticeItemDto>()
        scores.forEach { (_, r) ->
            r.words.forEach { w ->
                w.phones.filter { it.verdict == "error" && it.actual != null && it.actual != it.canonical }.forEach { p ->
                    val key = setOf(p.canonical, p.actual!!)
                    if (!seen.add(key)) return@forEach
                    MinimalPairs.pairsFor(p.canonical, p.actual).shuffled(random).take(2).forEach { (a, b) ->
                        val answer = if (random.nextBoolean()) a else b
                        out += PracticeItemDto(
                            id = nextId("pair"),
                            type = PracticeTypes.MINIMAL_PAIR,
                            prompt = "听到的是哪个词？（${p.canonical} / ${p.actual}）",
                            pair = listOf(a, b),
                            speak = answer,
                            answer = answer,
                            explanation = "你在「${w.word}」里把 ${p.canonical} 读成了 ${p.actual}",
                            layer = 1,
                        )
                    }
                }
            }
        }
        return out
    }

    fun shadowItems(scores: List<Pair<PracticeRecordEntity, AssessResult>>, input: Input): List<PracticeItemDto> =
        scores.filter { it.first.accuracy < 75 }
            .sortedBy { it.first.accuracy }
            .distinctBy { it.first.unitId }
            .take(3)
            .map { (rec, r) ->
                val worst = r.words.filter { it.score < 60 }.map { it.word }
                PracticeItemDto(
                    id = nextId("shadow"),
                    type = PracticeTypes.SHADOW,
                    prompt = if (worst.isEmpty()) "再读一遍这句" else "再读一遍，注意：${worst.joinToString("、")}",
                    text = rec.unitText,
                    speak = rec.unitText,
                    translation = input.unitsById[rec.unitId]?.translation,
                    explanation = "上次准确度 ${rec.accuracy}",
                    layer = 1,
                )
            }

    // ---- 工具 ------------------------------------------------------------------

    /** 与划选片段词数相同、长度相近的其他片段，来自同一素材。 */
    private fun spanDistractors(issue: IssueEntity, span: Pair<Int, Int>, input: Input, n: Int): List<String> {
        if (n <= 0) return emptyList()
        val answer = spanText(issue, span)
        val width = span.second - span.first + 1
        val unit = input.unitsById[issue.unitId]
        val pool = unit?.let { input.unitsByMaterial[it.materialId] } ?: input.unitsById.values.toList()
        val candidates = mutableSetOf<String>()
        pool.forEach { u ->
            val ws = Words.tokenize(u.text)
            for (i in 0..ws.size - width) {
                val cand = ws.subList(i, i + width).joinToString(" ") { it.display }
                val key = Words.normalize(cand)
                if (key.isEmpty() || key == Words.normalize(answer)) continue
                if (width == 1 && kotlin.math.abs(cand.length - answer.length) > 2) continue
                candidates += cand.trim { !it.isLetterOrDigit() && it != '\'' && it != ' ' && it != '-' }
            }
        }
        if (candidates.size < n) input.vocab.map { it.displayWord }.filter { Words.normalize(it) != Words.normalize(answer) }.forEach { candidates += it }
        return candidates.filter { it.isNotBlank() }.shuffled(random).take(n)
    }

    companion object {
        /** 先按标点切短语，再把长短语切成 2~3 词的块；结果 3~8 块。 */
        fun chunk(sentence: String): List<String> {
            val words = sentence.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size < 3) return words
            val phrases = mutableListOf<MutableList<String>>(mutableListOf())
            words.forEach { w ->
                phrases.last().add(w)
                if (w.last() in ",;:—–") phrases.add(mutableListOf())
            }
            val chunks = mutableListOf<String>()
            phrases.filter { it.isNotEmpty() }.forEach { ph ->
                if (ph.size <= 4) chunks += ph.joinToString(" ")
                else {
                    var i = 0
                    while (i < ph.size) {
                        val remaining = ph.size - i
                        val take = when {
                            remaining <= 4 -> remaining
                            remaining == 5 -> 3
                            else -> 3
                        }
                        chunks += ph.subList(i, i + take).joinToString(" ")
                        i += take
                    }
                }
            }
            // 太多块就两两合并，保持可操作
            var out: List<String> = chunks
            while (out.size > 8) out = out.chunked(2).map { it.joinToString(" ") }
            return out
        }
    }
}
