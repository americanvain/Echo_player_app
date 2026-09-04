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
}
