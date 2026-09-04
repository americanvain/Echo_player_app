package com.echoplayer.app

import com.echoplayer.app.data.local.TextImporter
import com.echoplayer.app.data.model.MaterialStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextImporterTest {
    @Test
    fun decodesUtf8WithBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Hello".toByteArray(Charsets.UTF_8)
        assertEquals("Hello", TextImporter.decode(bytes))
    }

    @Test
    fun fallsBackToGbk() {
        val bytes = "你好 world".toByteArray(charset("GBK"))
        assertEquals("你好 world", TextImporter.decode(bytes))
    }

    @Test
    fun buildsUnitsInOrder() {
        val b = TextImporter.build("First. Second.\n\nThird.", "T", "t.txt", null)
        assertEquals(MaterialStatus.READY.id, b.material.status)
        assertEquals(3, b.material.unitCount)
        assertEquals(listOf("First.", "Second.", "Third."), b.units.map { it.text })
        assertEquals(listOf(0, 1, 2), b.units.map { it.orderIndex })
        assertEquals(2, b.segments.size)
        assertTrue(b.units.all { it.materialId == b.material.id })
        assertEquals(b.segments[0].id, b.units[0].segmentId)
        assertEquals(b.segments[1].id, b.units[2].segmentId)
    }

    @Test
    fun emptyTextFails() {
        val b = TextImporter.build("   \n\n  ", "T", null, null)
        assertEquals(MaterialStatus.FAILED.id, b.material.status)
    }

    @Test
    fun titleFromFileName() {
        assertEquals("harry potter 1", TextImporter.titleFromName("harry_potter-1.txt"))
        assertEquals("导入的文本", TextImporter.titleFromName(null))
    }
}
