package com.echoplayer.app.data.local

/**
 * 常见混淆音对（中国学习者高频）→ 最小对立词。键是无序的音素对，音素符号与 speecheval 的 41 音素表一致。
 * 用于把跟读评分里的 "θ 读成了 s" 直接变成辨音练习。
 */
object MinimalPairs {
    private val table: Map<Set<String>, List<Pair<String, String>>> = listOf(
        setOf("θ", "s") to listOf("think" to "sink", "thick" to "sick", "thumb" to "sum", "path" to "pass", "mouth" to "mouse"),
        setOf("θ", "t") to listOf("thin" to "tin", "three" to "tree", "thank" to "tank", "bath" to "bat"),
        setOf("θ", "f") to listOf("three" to "free", "thin" to "fin", "thought" to "fought"),
        setOf("ð", "d") to listOf("then" to "den", "they" to "day", "though" to "dough", "breathe" to "breed"),
        setOf("ð", "z") to listOf("then" to "zen", "teethe" to "tease", "clothe" to "close"),
        setOf("v", "w") to listOf("vest" to "west", "vine" to "wine", "veil" to "whale", "vow" to "wow", "vet" to "wet"),
        setOf("v", "f") to listOf("van" to "fan", "vine" to "fine", "leave" to "leaf", "save" to "safe"),
        setOf("v", "b") to listOf("vest" to "best", "vote" to "boat", "very" to "berry"),
        setOf("l", "ɹ") to listOf("light" to "right", "lead" to "read", "low" to "row", "glass" to "grass", "collect" to "correct"),
        setOf("l", "n") to listOf("light" to "night", "lock" to "knock", "low" to "no"),
        setOf("i", "ɪ") to listOf("sheep" to "ship", "seat" to "sit", "leave" to "live", "feel" to "fill", "beat" to "bit"),
        setOf("æ", "ɛ") to listOf("bad" to "bed", "sad" to "said", "man" to "men", "pan" to "pen", "had" to "head"),
        setOf("æ", "ʌ") to listOf("cat" to "cut", "bat" to "but", "hat" to "hut", "fan" to "fun", "match" to "much"),
        setOf("ɑ", "ʌ") to listOf("cop" to "cup", "lock" to "luck", "shot" to "shut", "hot" to "hut"),
        setOf("ɑ", "ɔ") to listOf("cot" to "caught", "stock" to "stalk", "collar" to "caller"),
        setOf("n", "ŋ") to listOf("sin" to "sing", "thin" to "thing", "ran" to "rang", "win" to "wing"),
        setOf("s", "ʃ") to listOf("sip" to "ship", "sea" to "she", "sock" to "shock", "mass" to "mash"),
        setOf("z", "s") to listOf("zip" to "sip", "buzz" to "bus", "eyes" to "ice", "prize" to "price"),
        setOf("ʒ", "ʃ") to listOf("vision" to "fission", "measure" to "mesher"),
        setOf("tʃ", "ʃ") to listOf("chip" to "ship", "cheap" to "sheep", "watch" to "wash", "catch" to "cash"),
        setOf("dʒ", "j") to listOf("jet" to "yet", "jam" to "yam", "juice" to "use"),
        setOf("u", "ʊ") to listOf("pool" to "pull", "fool" to "full", "Luke" to "look", "suit" to "soot"),
        setOf("ɛ", "eɪ") to listOf("pen" to "pain", "wet" to "wait", "test" to "taste", "met" to "mate"),
        setOf("ɛ", "ɪ") to listOf("pen" to "pin", "bet" to "bit", "ten" to "tin", "desk" to "disk"),
        setOf("oʊ", "ɔ") to listOf("coat" to "caught", "low" to "law", "boat" to "bought"),
        setOf("b", "p") to listOf("bat" to "pat", "cab" to "cap", "bin" to "pin", "robe" to "rope"),
        setOf("d", "t") to listOf("bad" to "bat", "do" to "two", "hard" to "heart", "bed" to "bet"),
        setOf("g", "k") to listOf("gold" to "cold", "bag" to "back", "goat" to "coat", "glass" to "class"),
        setOf("ə", "ʌ") to listOf("about" to "a bout", "sofa" to "so fun"),
    ).toMap()

    fun pairsFor(canonical: String, actual: String?): List<Pair<String, String>> {
        if (actual == null || actual == canonical) return emptyList()
        return table[setOf(canonical, actual)].orEmpty()
    }

    /** 音素对里"正确的那个"在前：返回 (含 canonical 的词, 含 actual 的词) 的顺序无所谓，练习只要求听辨。 */
    fun allPairs(): List<Pair<String, String>> = table.values.flatten()
}
