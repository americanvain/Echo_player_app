package com.echoplayer.app

import com.echoplayer.app.data.db.IssueEntity
import com.echoplayer.app.data.db.PracticeRecordEntity
import com.echoplayer.app.data.db.UnitEntity
import com.echoplayer.app.data.db.VocabEntity
import com.echoplayer.app.data.local.LocalPracticeGenerator
import com.echoplayer.app.data.local.MinimalPairs
import com.echoplayer.app.data.remote.AssessResult
import com.echoplayer.app.data.remote.Overall
import com.echoplayer.app.data.remote.PhoneResult
import com.echoplayer.app.data.remote.PracticeTypes
import com.echoplayer.app.data.remote.WordResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PracticeGeneratorTest {

    private val units = listOf(
        unit("m#u1", 0, "The harder he blew, the tighter the traveler held his cloak around him.", "他吹得越猛，旅人就把斗篷裹得越紧。"),
        unit("m#u2", 1, "Finally, the North Wind gave up.", "最后，北风放弃了。"),
        unit("m#u3", 2, "Then the Sun came out and shone gently on the traveler.", "接着太阳出来了，温和地照在旅人身上。"),
    )

    private fun unit(id: String, order: Int, text: String, translation: String?) =
        UnitEntity(id = id, materialId = "m", orderIndex = order, text = text, translation = translation)

    private fun issue(layer: Int, unitId: String, text: String, start: Int, end: Int, spanText: String?, subtypes: String = "", misheard: String? = null) =
        IssueEntity(
            id = layer * 100L + start, unitId = unitId, materialId = "m", unitText = text, layer = layer,
            createdAt = 0, spanStart = start, spanEnd = end, spanText = spanText, subtypes = subtypes,
            misheardAs = misheard, translation = units.first { it.id == unitId }.translation,
        )

    private fun input(
        issues: List<IssueEntity> = emptyList(),
        vocab: List<VocabEntity> = emptyList(),
        scores: List<Pair<PracticeRecordEntity, AssessResult>> = emptyList(),
    ) = LocalPracticeGenerator.Input(issues, vocab, scores, units.associateBy { it.id }, mapOf("m" to units))

    @Test
    fun clozeBlanksTheSelectedSpanAndOffersIt() {
        val i = issue(1, "m#u1", units[0].text, 0, 2, "The harder he", subtypes = "linking")
        val item = LocalPracticeGenerator(Random(1)).clozeListen(i, input(listOf(i)))
        assertNotNull(item)
        assertEquals(PracticeTypes.CLOZE_LISTEN, item!!.type)
        assertTrue("挖空后的句子应该有下划线占位", item.text!!.contains("____"))
        assertEquals("The harder he", item.answer)
        assertTrue("正确答案必须在选项里", item.answer in item.options)
        assertEquals("要放整句的音", units[0].text, item.speak)
        assertEquals(listOf(i.id), item.issue_ids)
    }

    @Test
    fun listenChoicePutsMisheardWordIntoOptions() {
        val i = issue(2, "m#u1", units[0].text, 3, 3, "blew,", subtypes = "misheard", misheard = "blue")
        val item = LocalPracticeGenerator(Random(2)).listenChoice(i, input(listOf(i)))
        assertNotNull(item)
        assertTrue("听成的词要作为干扰项", item!!.options.any { it.equals("blue", ignoreCase = true) })
        assertTrue(item.answer in item.options)
    }

    @Test
    fun wholeSentenceIssueMakesNoClozeItem() {
        val i = issue(1, "m#u1", units[0].text, -1, -1, null)
        assertEquals(null, LocalPracticeGenerator(Random(3)).clozeListen(i, input(listOf(i))))
    }

    @Test
    fun reorderKeepsEveryChunkAndShuffles() {
        val i = issue(4, "m#u1", units[0].text, -1, -1, null, subtypes = "clause")
        val item = LocalPracticeGenerator(Random(4)).reorder(i)
        assertNotNull(item)
        assertEquals(item!!.answer_chunks.sorted(), item.chunks.sorted())
        assertEquals("拼回去应当还原原句", units[0].text, item.answer_chunks.joinToString(" "))
        assertTrue(item.chunks.size in 3..8)
    }

    @Test
    fun translationMatchUsesOtherSentencesAsDistractors() {
        val i = issue(5, "m#u1", units[0].text, -1, -1, null, subtypes = "literal_ok")
        val item = LocalPracticeGenerator(Random(5)).translationMatch(i, input(listOf(i)))
        assertNotNull(item)
        assertEquals(units[0].translation, item!!.answer)
        assertEquals(3, item.options.size)
        assertTrue(item.answer in item.options)
    }

    @Test
    fun phoneErrorsBecomeMinimalPairDrills() {
        val record = PracticeRecordEntity(
            id = 1, unitId = "m#u2", materialId = "m", unitText = "I think this",
            createdAt = 0, accuracy = 50, completeness = 100, fluency = 40, resultJson = "{}",
        )
        val result = AssessResult(
            text = "I think this",
            overall = Overall(50, 100, 40),
            words = listOf(
                WordResult(
                    word = "think", score = 40,
                    phones = listOf(PhoneResult(canonical = "θ", actual = "s", verdict = "error", score = 10)),
                )
            ),
        )
        val items = LocalPracticeGenerator(Random(6)).minimalPairs(listOf(record to result))
        assertTrue("θ/s 应当出题", items.isNotEmpty())
        val item = items.first()
        assertEquals(PracticeTypes.MINIMAL_PAIR, item.type)
        assertEquals(2, item.pair.size)
        assertTrue(item.answer in item.pair)
        assertEquals("要读出答案那个词", item.answer, item.speak)
    }

    @Test
    fun minimalPairTableIsSymmetricAndCoversKeyContrasts() {
        assertTrue(MinimalPairs.pairsFor("θ", "s").isNotEmpty())
        assertTrue("音素对无序，反过来也要能查到", MinimalPairs.pairsFor("s", "θ").isNotEmpty())
        assertTrue(MinimalPairs.pairsFor("l", "ɹ").isNotEmpty())
        assertTrue(MinimalPairs.pairsFor("i", "ɪ").isNotEmpty())
        assertTrue("同一个音素不算错", MinimalPairs.pairsFor("θ", "θ").isEmpty())
        assertTrue("漏读没有对立词", MinimalPairs.pairsFor("θ", null).isEmpty())
    }

    @Test
    fun chunkerSplitsAtPunctuationAndStaysInRange() {
        val chunks = LocalPracticeGenerator.chunk("The harder he blew, the tighter the traveler held his cloak around him.")
        assertTrue(chunks.size in 3..8)
        assertEquals("The harder he blew, the tighter the traveler held his cloak around him.", chunks.joinToString(" "))
        assertTrue("逗号应当是一个块的结尾", chunks.any { it.endsWith(",") })
    }

    @Test
    fun chunkerHandlesVeryShortSentences() {
        assertEquals(listOf("Go", "now."), LocalPracticeGenerator.chunk("Go now."))
    }

    @Test
    fun generateGroupsItemsByLayer() {
        val issues = listOf(
            issue(1, "m#u1", units[0].text, 1, 2, "harder he", "linking"),
            issue(3, "m#u3", units[2].text, 5, 5, "shone", "unknown_word"),
            issue(4, "m#u1", units[0].text, -1, -1, null, "clause"),
        )
        val vocab = listOf(
            VocabEntity(id = 1, word = "shone", displayWord = "shone", contextSentence = units[2].text, addedAt = 0, familiarity = 0),
        )
        val sets = LocalPracticeGenerator(Random(7)).generate(input(issues, vocab))
        assertTrue("应当按层分成多组", sets.size >= 3)
        assertTrue(sets.all { it.items.isNotEmpty() })
        assertTrue(sets.all { it.id.isNotBlank() })
        assertEquals("组内的 item id 必须唯一", sets.flatMap { it.items }.map { it.id }.distinct().size, sets.sumOf { it.items.size })
        assertTrue("生词应当变成闪卡", sets.flatMap { it.items }.any { it.type == PracticeTypes.FLASHCARD })
    }

    @Test
    fun generateReturnsNothingWithoutRecords() {
        assertTrue(LocalPracticeGenerator(Random(8)).generate(input()).isEmpty())
    }
}
