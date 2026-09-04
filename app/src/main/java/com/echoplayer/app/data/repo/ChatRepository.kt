package com.echoplayer.app.data.repo

import com.echoplayer.app.data.db.ChatDao
import com.echoplayer.app.data.db.ChatMessageEntity
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.local.Dictionary
import com.echoplayer.app.data.local.OfflineDictionary
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.data.remote.ChatRequest
import com.echoplayer.app.data.remote.ChatTurn
import com.echoplayer.app.data.remote.EchoServerApi
import com.echoplayer.app.util.Words
import kotlinx.coroutines.flow.Flow

/**
 * 听读页的 AI 问答。有服务器就走 `/chat`（教学 Agent），没有就用离线规则兜底：
 * 查词、整句音标、难词、译文这几类问题本机就能答，其余明确说答不了。
 * 每一问一答都挂在句子上存起来（Echo_player 第五部分"记录疑问"）。
 */
class ChatRepository(
    private val dao: ChatDao,
    private val api: EchoServerApi,
    private val dictionary: Dictionary,
) {
    fun observe(unitId: String): Flow<List<ChatMessageEntity>> = dao.observeForUnit(unitId)
    val questionCount: Flow<Int> = dao.observeQuestionCount()

    suspend fun clear(unitId: String) = dao.clearUnit(unitId)

    /** 常用问题，放在输入框上方当快捷入口。 */
    fun suggestions(): List<String> = listOf("这句什么意思", "这句怎么读", "难词有哪些", "解释一下结构")

    suspend fun ask(unit: UnitEntity, context: List<String>, materialTitle: String?, question: String): ChatMessageEntity {
        val q = question.trim()
        val now = System.currentTimeMillis()
        dao.insert(ChatMessageEntity(unitId = unit.id, materialId = unit.materialId, role = "user", text = q, createdAt = now))

        var answer: String? = null
        var fromServer = false
        if (api.isConfigured) {
            val history = dao.forUnit(unit.id).takeLast(10).map { ChatTurn(it.role, it.text) }
            runCatching {
                api.chat(ChatRequest(unit_text = unit.text, context = context, translation = unit.translation, question = q, history = history, material_title = materialTitle))
            }.onSuccess { answer = it.answer.trim().ifBlank { null }; fromServer = answer != null }
        }
        if (answer == null) answer = offlineAnswer(unit, q)

        val reply = ChatMessageEntity(
            unitId = unit.id, materialId = unit.materialId, role = "assistant",
            text = answer!!, createdAt = System.currentTimeMillis(), fromServer = fromServer,
        )
        val id = dao.insert(reply)
        return reply.copy(id = id)
    }

    // ---- 离线兜底 ------------------------------------------------------------------

    suspend fun offlineAnswer(unit: UnitEntity, question: String): String {
        val tokens = Words.tokenize(unit.text)
        val sentenceKeys = tokens.map { it.key }.filter { it.isNotEmpty() }.toSet()
        val q = question.lowercase()

        // 1) 问题里提到了句子里的某个词 → 直接给释义
        val asked = Words.tokenize(question).map { it.key }.filter { it.length > 1 && it in sentenceKeys }.distinct()
        if (asked.isNotEmpty()) {
            val lines = asked.mapNotNull { k -> dictionary.lookup(k)?.let { formatEntry(it) } }
            if (lines.isNotEmpty()) return lines.joinToString("\n\n")
        }

        fun any(vararg keys: String) = keys.any { q.contains(it) }

        // 2) 怎么读 → 整句音标
        if (any("怎么读", "发音", "读音", "音标", "连读", "pronounce", "pronunciation")) {
            val found = dictionary.lookupMany(tokens.map { it.key })
            // 标签用去掉标点的词，否则会出现 "cloak. /klәuk/" 这种别扭的写法
            val parts = tokens.map { t ->
                val label = t.display.trim { !it.isLetterOrDigit() && it != '\'' }.ifEmpty { t.display }
                val ph = found[t.key]?.phonetic
                if (ph.isNullOrBlank()) label else "$label /$ph/"
            }
            return "逐词音标：\n" + parts.joinToString("  ") + "\n\n连读、弱读这类语流现象需要服务器上的 AI 才能具体分析；可以先用底部的「逐词播放」和慢速重听对照着听。"
        }

        // 3) 难词
        if (any("难词", "生词", "单词", "词汇", "vocab", "word")) {
            val hard = hardWords(unit)
            return if (hard.isEmpty()) "这句里没有超出常用词范围的词。" else "这句里的难词：\n" + hard.joinToString("\n") { "• ${it.word}${it.phonetic?.let { p -> " /$p/" } ?: ""}  ${it.brief}" }
        }

        // 4) 意思 / 翻译
        if (any("意思", "翻译", "含义", "mean", "translate")) {
            return unit.translation?.takeIf { it.isNotBlank() }?.let { "整句意思：$it" }
                ?: "这句还没有译文。配置服务器后可以在线翻译，也可以点句子里的单词逐个看释义。"
        }

        // 5) 结构 / 语法
        if (any("结构", "语法", "句法", "主语", "谓语", "从句", "grammar", "structure")) {
            val syntax = ProblemLayer.SYNTAX
            return buildString {
                append("句子结构分析需要服务器上的 AI。离线能做的：\n")
                append("• 长按划出你觉得结构走偏的那一段，按底部「句法」按钮，选具体是哪一种（")
                append(syntax.subtypes.joinToString("、") { it.label })
                append("），记录下来以后会生成句子重组练习。\n")
                unit.translation?.takeIf { it.isNotBlank() }?.let { append("• 对照译文：").append(it) }
            }
        }

        return "没有连接服务器时，我只能回答这几类问题：某个词的意思（直接写出那个词）、这句怎么读、难词有哪些、这句什么意思。配置服务器后可以问任何关于这句话的问题。"
    }

    /** 句子里超出常用词范围的词，按出现顺序，去重。 */
    suspend fun hardWords(unit: UnitEntity, limit: Int = 8): List<GlossItem> {
        val tokens = Words.tokenize(unit.text)
        val found = dictionary.lookupMany(tokens.map { it.key })
        val seen = HashSet<String>()
        val out = ArrayList<GlossItem>()
        for (t in tokens) {
            val lk = found[t.key] ?: continue
            val entry = lk.base ?: lk.form
            if (!entry.isHard) continue
            if (!seen.add(entry.word)) continue
            out += GlossItem(index = t.index, word = t.display.trim { !it.isLetterOrDigit() && it != '\'' }, phonetic = lk.phonetic, brief = entry.brief)
            if (out.size >= limit) break
        }
        return out
    }

    private fun formatEntry(lk: OfflineDictionary.Lookup): String {
        val ph = lk.phonetic?.let { " /$it/" } ?: ""
        return "${lk.form.word}$ph\n${lk.translation}"
    }
}

/** "本句词汇"里的一条。 */
data class GlossItem(val index: Int, val word: String, val phonetic: String?, val brief: String)
