package com.echoplayer.app

import com.echoplayer.app.data.remote.AssessResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 服务器 AssessResult（speecheval schema.py）→ 客户端 DTO，允许多余字段。 */
class AssessResultParsingTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun parsesServerShape() {
        val raw = """
        {"text":"I think this","overall":{"accuracy":78,"completeness":100,"fluency":32},
         "words":[{"word":"think","score":55,"start":0.42,"end":0.81,"oov":false,
                   "phones":[{"canonical":"θ","actual":"s","start":0.42,"end":0.5,"gop":-3.2,"score":21,"verdict":"error","hint":"舌尖轻触上齿","stress":0},
                             {"canonical":"ɪ","actual":null,"start":0.5,"end":0.6,"gop":0.0,"score":90,"verdict":"good","hint":null,"stress":1}]}],
         "insertions":[{"actual":"ə","after_phone_index":1}],"duration":1.9,"timing_ms":{"total":65.1},"debug":{"x":1}}
        """.trimIndent()
        val r = json.decodeFromString(AssessResult.serializer(), raw)
        assertEquals(78, r.overall.accuracy)
        assertEquals("think", r.words[0].word)
        assertEquals("error", r.words[0].phones[0].verdict)
        assertEquals("s", r.words[0].phones[0].actual)
        assertNull(r.words[0].phones[1].actual)
        assertEquals(1, r.insertions.size)
        assertEquals(65.1, r.timing_ms["total"]!!, 1e-9)
    }

    @Test
    fun roundTripsThroughDatabaseJson() {
        val r = AssessResult(text = "a", words = emptyList())
        val encoded = json.encodeToString(AssessResult.serializer(), r)
        assertEquals(r, json.decodeFromString(AssessResult.serializer(), encoded))
    }
}
