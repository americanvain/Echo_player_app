package com.echoplayer.app

import com.echoplayer.app.data.db.ChatDao
import com.echoplayer.app.data.db.ChatMessageEntity
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.local.Dictionary
import com.echoplayer.app.data.local.OfflineDictionary
import com.echoplayer.app.data.remote.EchoServerApi
import com.echoplayer.app.data.repo.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 离线兜底回答的规则；词典用一张假表。 */
class OfflineAnswerTest {

    private fun e(word: String, ph: String?, tr: String, lemma: String? = null, rank: Int = 0) =
        OfflineDictionary.Entry(word, ph, tr, lemma, rank)

    private val table = mapOf(
        "the" to e("the", "ðә", "art. 那", rank = 1),
        "harder" to e("harder", "hɑ:dә", "hard的比较级", lemma = "hard"),
        "hard" to e("hard", "hɑ:d", "a. 硬的, 困难的", rank = 500),
        "blew" to e("blew", "blu:", "blow的过去式", lemma = "blow"),
        "blow" to e("blow", "bləu", "vi. 吹, 刮风", rank = 2100),
        "cloak" to e("cloak", "klәuk", "n. 斗篷, 大氅", rank = 6563),
        "traveler" to e("traveler", "'trævlә", "n. 旅行者", rank = 3795),
    )

    private val fakeDict = object : Dictionary {
        override suspend fun lookup(word: String): OfflineDictionary.Lookup? {
            val form = table[OfflineDictionary.normalize(word)] ?: return null
            return OfflineDictionary.Lookup(form, form.lemma?.let { table[it] })
        }

        override suspend fun lookupMany(words: Collection<String>) =
            words.mapNotNull { w -> lookup(w)?.let { OfflineDictionary.normalize(w) to it } }.toMap()
    }

    private val fakeDao = object : ChatDao {
        val rows = mutableListOf<ChatMessageEntity>()
        override fun observeForUnit(unitId: String): Flow<List<ChatMessageEntity>> = flowOf(rows)
        override suspend fun forUnit(unitId: String) = rows.toList()
        override suspend fun insert(message: ChatMessageEntity): Long { rows += message; return rows.size.toLong() }
        override suspend fun clearUnit(unitId: String) { rows.clear() }
        override fun observeQuestionCount(): Flow<Int> = flowOf(rows.count { it.role == "user" })
    }

    private val repo = ChatRepository(fakeDao, EchoServerApi { "" }, fakeDict)
    private val unit = UnitEntity(
        id = "u1", materialId = "m", orderIndex = 0,
        text = "The harder he blew, the tighter the traveler held his cloak.",
        translation = "他吹得越猛，旅人把斗篷裹得越紧。",
    )

    @Test
    fun askingAboutAWordInTheSentenceGivesItsEntryAndLemma() = runTest {
        val a = repo.offlineAnswer(unit, "blew 是什么意思")
        assertTrue(a, a.contains("blew"))
        assertTrue("要带音标", a.contains("/blu:/"))
        assertTrue("屈折形式要连原形释义一起给", a.contains("吹"))
    }

    @Test
    fun pronunciationQuestionGivesPerWordPhonetics() = runTest {
        val a = repo.offlineAnswer(unit, "这句怎么读")
        assertTrue(a.startsWith("逐词音标"))
        assertTrue(a.contains("cloak /klәuk/"))
        assertTrue("词典没有的词原样保留", a.contains("tighter"))
    }

    @Test
    fun hardWordsSkipCommonOnes() = runTest {
        val hard = repo.hardWords(unit)
        val words = hard.map { it.word.lowercase() }
        assertTrue("cloak 名次 6563 是难词", "cloak" in words)
        assertTrue("traveler 名次 3795 是难词", "traveler" in words)
        assertTrue("the 是最常见的词，不算", "the" !in words)
        assertTrue("harder 的原形 hard 名次 500，不算难词", "harder" !in words)
        assertEquals("按出现顺序", listOf("traveler", "cloak"), words.filter { it in setOf("traveler", "cloak") })
    }

    @Test
    fun meaningQuestionReturnsTranslation() = runTest {
        assertTrue(repo.offlineAnswer(unit, "这句什么意思").contains("旅人"))
        val noTr = repo.offlineAnswer(unit.copy(translation = null), "翻译一下")
        assertTrue(noTr.contains("还没有译文"))
    }

    @Test
    fun structureQuestionPointsToSyntaxLayer() = runTest {
        val a = repo.offlineAnswer(unit, "解释一下结构")
        assertTrue(a.contains("句法"))
        assertTrue(a.contains("从句嵌套"))
    }

    @Test
    fun unknownQuestionExplainsLimits() = runTest {
        val a = repo.offlineAnswer(unit, "作者为什么这么写")
        assertTrue(a.contains("没有连接服务器"))
    }

    @Test
    fun askPersistsBothTurnsAndMarksOffline() = runTest {
        val reply = repo.ask(unit, emptyList(), "T", "cloak")
        assertEquals("assistant", reply.role)
        assertEquals(false, reply.fromServer)
        assertEquals(listOf("user", "assistant"), fakeDao.rows.map { it.role })
        assertTrue(reply.text.contains("斗篷"))
    }
}
