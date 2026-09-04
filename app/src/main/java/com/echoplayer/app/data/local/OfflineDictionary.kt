package com.echoplayer.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** 词典查询接口；[OfflineDictionary] 是内置实现，测试里用假的。 */
interface Dictionary {
    suspend fun lookup(word: String): OfflineDictionary.Lookup?
    suspend fun lookupMany(words: Collection<String>): Map<String, OfflineDictionary.Lookup>
}

/**
 * 随 APK 内置的离线英汉词典（tools/build_dictionary.py 从 ECDICT 生成）。
 *
 * 点词直接出释义和音标，不依赖服务器；屈折形式（blew）通过 lemma 指回原形（blow）。
 * 第一次用时把 assets 里的 SQLite 复制到私有目录，之后只读打开。
 * 不走 Room：预打包库不需要它的 schema 校验，裸 SQLite 更省事。
 */
class OfflineDictionary(private val context: Context) : Dictionary {

    data class Entry(
        val word: String,
        val phonetic: String?,
        val translation: String,
        /** 原形；null 表示自己就是原形 */
        val lemma: String?,
        /** 词频名次，0 = 未知 */
        val rank: Int,
    ) {
        /** 第一条释义，给"本句词汇"这种只放得下一行的地方。 */
        val brief: String get() = translation.lineSequence().firstOrNull()?.trim().orEmpty()

        /** 名次靠后（或没有名次）的算"难词"。 */
        val isHard: Boolean get() = rank == 0 || rank > COMMON_RANK
    }

    /** 点词得到的结果：这个形式本身 + 它的原形（如果不同）。 */
    data class Lookup(val form: Entry, val base: Entry?) {
        val phonetic: String? get() = form.phonetic ?: base?.phonetic
        val translation: String
            get() = if (base != null && base.word != form.word) form.translation + "\n" + base.translation else form.translation
    }

    private val mutex = Mutex()
    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var unavailable = false

    val available: Boolean get() = !unavailable

    private suspend fun open(): SQLiteDatabase? {
        db?.let { return it }
        if (unavailable) return null
        return mutex.withLock {
            db?.let { return it }
            withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.filesDir, "dict/$ASSET_NAME")
                    if (!file.exists() || file.length() < 1024) {
                        file.parentFile?.mkdirs()
                        context.assets.open("dict/$ASSET_NAME").use { input -> file.outputStream().use { input.copyTo(it) } }
                    }
                    SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
                }.onFailure { unavailable = true }.getOrNull()?.also { db = it }
            }
        }
    }

    override suspend fun lookup(word: String): Lookup? {
        val key = normalize(word)
        if (key.isEmpty()) return null
        val d = open() ?: return null
        return withContext(Dispatchers.IO) {
            val form = query(d, key)
                ?: key.takeIf { it.endsWith("'s") }?.let { query(d, it.removeSuffix("'s")) }
                ?: key.takeIf { it.endsWith("s'") }?.let { query(d, it.removeSuffix("'")) }
                ?: return@withContext null
            val base = form.lemma?.let { query(d, it) }
            Lookup(form, base)
        }
    }

    /** 一句话里所有词的查询结果，键是规范化后的词。 */
    override suspend fun lookupMany(words: Collection<String>): Map<String, Lookup> {
        val keys = words.map { normalize(it) }.filter { it.isNotEmpty() }.distinct()
        if (keys.isEmpty()) return emptyMap()
        val d = open() ?: return emptyMap()
        return withContext(Dispatchers.IO) {
            val out = HashMap<String, Lookup>()
            keys.forEach { k ->
                val form = query(d, k) ?: return@forEach
                out[k] = Lookup(form, form.lemma?.let { query(d, it) })
            }
            out
        }
    }

    private fun query(d: SQLiteDatabase, key: String): Entry? =
        d.rawQuery("select word, phonetic, translation, lemma, rank from words where word = ? limit 1", arrayOf(key)).use { c ->
            if (!c.moveToFirst()) null
            else Entry(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4))
        }

    companion object {
        const val ASSET_NAME = "ecdict.db"
        /** 词频名次在这之内的算常见词，不出现在"本句词汇"里。 */
        const val COMMON_RANK = 2500

        fun normalize(word: String): String =
            word.trim().lowercase().trim { !it.isLetterOrDigit() && it != '\'' && it != '-' }
    }
}
