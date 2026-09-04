package com.echoplayer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

/** 直接打开打进 APK 的词典文件，确认内容符合预期。 */
class DictionaryAssetTest {
    private val db = listOf("app/src/main/assets/dict/ecdict.db", "src/main/assets/dict/ecdict.db")
        .map { File(it) }.first { it.exists() }

    private fun <T> query(sql: String, vararg args: Any, map: (java.sql.ResultSet) -> T): T? =
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            c.prepareStatement(sql).use { ps ->
                args.forEachIndexed { i, a -> ps.setObject(i + 1, a) }
                ps.executeQuery().use { rs -> if (rs.next()) map(rs) else null }
            }
        }

    @Test
    fun hasTheExpectedShape() {
        val n = query("select count(*) from words") { it.getInt(1) }!!
        assertTrue("至少 7 万条，实际 $n", n > 70_000)
        assertTrue("体积要控制在 8MB 内，实际 ${db.length() / 1048576}MB", db.length() < 8L * 1048576)
        assertEquals("ECDICT 1.0.28 (MIT) https://github.com/skywind3000/ECDICT", query("select value from meta where key='source'") { it.getString(1) })
    }

    @Test
    fun inflectionsPointToTheirLemma() {
        assertEquals("blow", query("select lemma from words where word='blew'") { it.getString(1) })
        assertEquals("tight", query("select lemma from words where word='tighter'") { it.getString(1) })
        assertEquals("shine", query("select lemma from words where word='shone'") { it.getString(1) })
        assertNotNull("原形本身要在表里", query("select 1 from words where word='blow'") { it.getInt(1) })
    }

    @Test
    fun commonAndHardWordsHaveRanks() {
        val theRank = query("select rank from words where word='the'") { it.getInt(1) }!!
        val cloakRank = query("select rank from words where word='cloak'") { it.getInt(1) }!!
        assertTrue(theRank in 1..50)
        assertTrue(cloakRank > 2500)
    }

    @Test
    fun everyWordInBundledMaterialsResolves() {
        val dir = listOf("app/src/main/assets/materials", "src/main/assets/materials").map { File(it) }.first { it.exists() }
        val words = HashSet<String>()
        dir.listFiles { f -> f.name.endsWith(".json") && f.name != "index.json" }!!.forEach { f ->
            Regex("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(f.readText()).forEach { m ->
                Regex("[A-Za-z][A-Za-z'\\-]*").findAll(m.groupValues[1]).forEach { w ->
                    words += w.value.lowercase().trim('\'', '-').removeSuffix("'s")
                }
            }
        }
        val missing = words.filter { w -> w.isNotEmpty() && query("select 1 from words where word=?", w) { it.getInt(1) } == null }
        assertTrue("内置素材里查不到的词：$missing", missing.isEmpty())
    }
}
