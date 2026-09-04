package com.echoplayer.app

import com.echoplayer.app.util.Words
import org.junit.Assert.assertEquals
import org.junit.Test

class WordsTest {
    @Test
    fun tokenizeKeepsDisplayAndNormalizesKey() {
        val t = Words.tokenize("\"Let's race,\" said the hare.")
        assertEquals(listOf("\"Let's", "race,\"", "said", "the", "hare."), t.map { it.display })
        assertEquals(listOf("let's", "race", "said", "the", "hare"), t.map { it.key })
    }

    @Test
    fun countsWordsIgnoringPunctuation() {
        assertEquals(3, Words.countWords("Wait... what?! Really?"))
    }

    @Test
    fun charRangesLineUpWithTheJoinedString() {
        val tokens = Words.tokenize("The North Wind gave up.")
        val display = Words.display(tokens)
        val ranges = Words.charRanges(tokens)
        assertEquals("The North Wind gave up.", display)
        assertEquals(tokens.size, ranges.size)
        tokens.forEachIndexed { i, tok ->
            assertEquals("第 $i 个词的字符区间要对上", tok.display, display.substring(ranges[i].first, ranges[i].last + 1))
        }
    }

    @Test
    fun charRangesCollapseRepeatedWhitespace() {
        val tokens = Words.tokenize("a   b\tc")
        val display = Words.display(tokens)
        val ranges = Words.charRanges(tokens)
        assertEquals("a b c", display)
        assertEquals("c", display.substring(ranges[2].first, ranges[2].last + 1))
    }

    @Test
    fun wordIndexAtFindsTheWordAndRejectsGaps() {
        val tokens = Words.tokenize("Go now please")
        val ranges = Words.charRanges(tokens)
        assertEquals(0, Words.wordIndexAt(ranges, 0))
        assertEquals(0, Words.wordIndexAt(ranges, 1))
        assertEquals(-1, Words.wordIndexAt(ranges, 2))
        assertEquals(1, Words.wordIndexAt(ranges, 3))
        assertEquals(2, Words.wordIndexAt(ranges, 7))
        assertEquals(-1, Words.wordIndexAt(ranges, 99))
    }
}
