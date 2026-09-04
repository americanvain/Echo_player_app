package com.echoplayer.app

import com.echoplayer.app.data.db.IssueEntity
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.data.model.Severity
import com.echoplayer.app.ui.reader.WordSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueModelTest {

    @Test
    fun everyLayerHasSubtypesWithUniqueIdsAndEnglishPrompts() {
        ProblemLayer.entries.forEach { layer ->
            assertTrue("${layer.name} 至少要有 5 个细分类型", layer.subtypes.size >= 5)
            assertEquals("${layer.name} 的细分 id 必须唯一", layer.subtypes.size, layer.subtypes.map { it.id }.distinct().size)
            layer.subtypes.forEach { st ->
                assertTrue("${st.id} 需要中文标签", st.label.isNotBlank())
                assertTrue("${st.id} 的英文描述是发给 AI 的，必须是英文", st.promptEn.all { it.code < 128 })
            }
            assertTrue(layer.spanHint.isNotBlank())
        }
    }

    @Test
    fun subtypeLookup() {
        assertNotNull(ProblemLayer.PHONETIC.subtype("linking"))
        assertEquals("连读", ProblemLayer.PHONETIC.subtype("linking")!!.label)
        assertEquals(null, ProblemLayer.PHONETIC.subtype("nope"))
    }

    @Test
    fun severityRoundTrip() {
        assertEquals(Severity.MISSED, Severity.fromId(3))
        assertEquals(null, Severity.fromId(0))
    }

    @Test
    fun issueEntityParsesSubtypesAndSpan() {
        val i = IssueEntity(
            unitId = "u", materialId = "m", unitText = "t", layer = 1, createdAt = 0,
            spanStart = 2, spanEnd = 4, subtypes = "linking,reduction",
        )
        assertEquals(listOf("linking", "reduction"), i.subtypeIds)
        assertFalse(i.isWholeSentence)
        assertTrue(IssueEntity(unitId = "u", materialId = "m", unitText = "t", layer = 1, createdAt = 0).isWholeSentence)
        assertEquals(emptyList<String>(), IssueEntity(unitId = "u", materialId = "m", unitText = "t", layer = 1, createdAt = 0).subtypeIds)
    }

    @Test
    fun wordSpanNormalizesDirection() {
        val s = WordSpan.of(5, 2)
        assertEquals(2, s.start)
        assertEquals(5, s.end)
        assertEquals(4, s.size)
        assertTrue(3 in s)
        assertFalse(6 in s)
    }
}
