package com.echoplayer.app

import com.echoplayer.app.data.local.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceSplitterTest {
    @Test
    fun splitsSimpleSentences() {
        val s = SentenceSplitter.sentences("The market opens early. Farmers arrange bright vegetables! Do you like it?")
        assertEquals(listOf("The market opens early.", "Farmers arrange bright vegetables!", "Do you like it?"), s)
    }

    @Test
    fun keepsAbbreviationsTogether() {
        val s = SentenceSplitter.sentences("Mr. Smith met Dr. Jones at 5 p.m. They talked for an hour.")
        assertEquals(2, s.size)
        assertEquals("Mr. Smith met Dr. Jones at 5 p.m.", s[0])
    }

    @Test
    fun abbreviationFollowedByLowercaseContinues() {
        val s = SentenceSplitter.sentences("Bring fruit, e.g. apples and pears. Then leave.")
        assertEquals(listOf("Bring fruit, e.g. apples and pears.", "Then leave."), s)
    }

    @Test
    fun keepsDecimalsAndInitials() {
        val s = SentenceSplitter.sentences("Pi is about 3.14 in value. J. K. Rowling wrote it.")
        assertEquals(listOf("Pi is about 3.14 in value.", "J. K. Rowling wrote it."), s)
    }

    @Test
    fun attachesClosingQuotes() {
        val s = SentenceSplitter.sentences("\"Let's race and see,\" said the hare. \"This is too easy.\" He lay down.")
        assertEquals(listOf("\"Let's race and see,\" said the hare.", "\"This is too easy.\"", "He lay down."), s)
    }

    @Test
    fun handlesEllipsisAndMultiplePunctuation() {
        val s = SentenceSplitter.sentences("Wait... what?! Really?")
        // 省略号后接小写，视为同一句的延续
        assertEquals(listOf("Wait... what?!", "Really?"), s)
    }

    @Test
    fun keepsTrailingFragment() {
        val s = SentenceSplitter.sentences("First sentence. and a fragment without end")
        assertEquals(listOf("First sentence. and a fragment without end"), s)
    }

    @Test
    fun paragraphsByBlankLines() {
        val text = "Line one of para one\ncontinues here.\n\nPara two.\r\n\r\nPara three."
        assertEquals(listOf("Line one of para one continues here.", "Para two.", "Para three."), SentenceSplitter.paragraphs(text))
    }

    @Test
    fun paragraphsByLinesWhenNoBlankLines() {
        assertEquals(listOf("A.", "B.", "C."), SentenceSplitter.paragraphs("A.\nB.\nC.\n"))
    }

    @Test
    fun splitPreservesOrderAndParagraphIndex() {
        val out = SentenceSplitter.split("One. Two.\n\nThree.")
        assertEquals(listOf(0 to "One.", 0 to "Two.", 1 to "Three."), out)
    }
}
