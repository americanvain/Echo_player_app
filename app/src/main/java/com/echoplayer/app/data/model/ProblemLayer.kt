package com.echoplayer.app.data.model

/**
 * Echo Player 的核心概念：听不懂时，问题出在五个处理层面中的哪一层。
 *
 * 五层定义来自 Echo_player/design.md 与 classification.md。
 *
 * 光有"哪一层"不够细，AI 无法据此生成针对性语料，所以每一层还带：
 * - [subtypes]：这一层下面的细分类型，用户点选即可，带英文描述原样发给服务器；
 * - [spanHint]：提示用户划选出具体卡在哪几个词。
 *
 * 一条记录最终是「句子 + 词范围 + 层 + 细分类型 + 程度 + 可选的一句话」，
 * 精确到足以让 AI 生成对应的练习（见 docs/SERVER_API.md `/issues/explain`、`/practice/generate`）。
 */
enum class ProblemLayer(
    val id: Int,
    val shortLabel: String,
    val title: String,
    val titleEn: String,
    val definition: String,
    val symptoms: List<String>,
    val actions: List<LayerAction>,
    val subtypes: List<IssueSubtype>,
    val spanHint: String,
) {
    PHONETIC(
        id = 1,
        shortLabel = "语音",
        title = "语音 / 音系层",
        titleEn = "Phonetic / Phonological Processing",
        definition = "把连续的声音解析成英语的音素序列，并在语流中推断词边界。它只管“这段声音在英语里可以是什么音”，不涉及词是什么、什么意思。这一层错了，后面全部建立在错误的声音表征上，典型结果是从句首就“听歪”。",
        symptoms = listOf(
            "把一个声音听成另一个词，或听到“熟悉但不该出现的词”",
            "连读、弱读、吞音处完全听不出词边界",
            "句子从第一个词就开始崩，越听越乱",
        ),
        actions = listOf(
            LayerAction.SLOW_REPLAY,
            LayerAction.WORD_BY_WORD,
            LayerAction.SHADOW_SCORE,
        ),
        subtypes = listOf(
            IssueSubtype("linking", "连读", "could not hear the linking between words"),
            IssueSubtype("reduction", "弱读、吞音", "weak forms or elided sounds made the words disappear"),
            IssueSubtype("speed", "语速太快", "the passage was too fast to parse"),
            IssueSubtype("unfamiliar_sound", "读音和拼写对不上", "the pronunciation did not match what the spelling led me to expect"),
            IssueSubtype("similar_sound", "相似音混淆", "confused two similar sounds (minimal pair)"),
            IssueSubtype("stress", "重音、语调", "the stress or intonation pattern threw me off"),
            IssueSubtype("boundary", "分不出词边界", "could not tell where one word ended and the next began"),
        ),
        spanHint = "划出听糊的那一两个词",
    ),
    LEXICAL_FORM(
        id = 2,
        shortLabel = "词形",
        title = "词形识别层",
        titleEn = "Lexical Form Access",
        definition = "根据音系层给出的音序，激活并选出具体的词条形式，解决“这些声音对应英语里的哪一个词”。它只管词的形式身份，不管意思；多个候选词可以同时激活、互相竞争。失败时表现为声音听清了，但大脑选中了错误的词，一看文本立刻明白。",
        symptoms = listOf(
            "明明听到了声音，却激活了错误的词",
            "正确的词你“没想到”，看到字幕才恍然大悟",
            "输入经验不足，词的声音形象还没建立",
        ),
        actions = listOf(
            LayerAction.WORD_BY_WORD,
            LayerAction.REVEAL_TEXT,
            LayerAction.ADD_VOCAB,
        ),
        subtypes = listOf(
            IssueSubtype("misheard", "听成了别的词", "heard a different word (see misheard_as)"),
            IssueSubtype("known_not_recognized", "认识但没反应过来", "know the word in writing but did not recognize it by ear"),
            IssueSubtype("proper_noun", "人名、地名", "a proper noun or name"),
            IssueSubtype("contraction_number", "缩写、数字", "a contraction, number, or abbreviation"),
            IssueSubtype("inflection", "词形变化没听出", "missed an inflection such as plural, past tense, or third person -s"),
            IssueSubtype("rare_word", "没在语音里遇到过", "a word never encountered in speech before"),
        ),
        spanHint = "划出没认出来的那个词",
    ),
    LEXICAL_SEMANTICS(
        id = 3,
        shortLabel = "词义",
        title = "词义层",
        titleEn = "Lexical Semantics",
        definition = "在词形已经确定的前提下，为词条激活它的语义内容；多义词还要按语境选出正确义项、抑制其他义项。它回答“这个词在这里是什么意思”。失败时表现为词听对了，但选的意思不合语境，包括不认识的词和熟词生义。",
        symptoms = listOf(
            "不认识这个单词",
            "熟词生义：只知道一个意思，套进去说不通",
            "最常见的一层，容易被误判成“英语不好”",
        ),
        actions = listOf(
            LayerAction.SHOW_TRANSLATION,
            LayerAction.LOOKUP_WORD,
            LayerAction.ADD_VOCAB,
        ),
        subtypes = listOf(
            IssueSubtype("unknown_word", "不认识这个词", "do not know this word at all"),
            IssueSubtype("known_word_new_sense", "熟词生义", "know the word but not the sense used here"),
            IssueSubtype("phrase", "短语、动词搭配", "a phrasal verb or collocation"),
            IssueSubtype("idiom", "习语、俚语", "an idiom or slang expression"),
            IssueSubtype("polysemy", "多义词选错义项", "picked the wrong sense of a polysemous word"),
            IssueSubtype("nuance", "褒贬、语气拿不准", "unsure about the connotation or register"),
        ),
        spanHint = "划出不懂意思的词或短语",
    ),
    SYNTAX(
        id = 4,
        shortLabel = "句法",
        title = "句法解析层",
        titleEn = "Syntactic Parsing",
        definition = "根据句法规则为词序建立结构关系：主谓宾分配、修饰归属、从句嵌套、指代绑定。它处理的是词与词之间的结构位置，不直接生成整体意义。这一层高度依赖在线预测，失败时常表现为长句中途结构走偏，论元关系混乱。",
        symptoms = listOf(
            "谁对谁做了什么，搞乱了",
            "听到一半突然“脑子走歪”，后半句接不上",
            "从句、倒装、省略一多就崩",
        ),
        actions = listOf(
            LayerAction.SHOW_TRANSLATION,
            LayerAction.SLOW_REPLAY,
            LayerAction.NOTE_QUESTION,
        ),
        subtypes = listOf(
            IssueSubtype("svo", "主谓宾对不上", "could not identify the subject, verb, or object"),
            IssueSubtype("clause", "从句嵌套", "an embedded or relative clause"),
            IssueSubtype("modifier", "修饰谁不清楚", "unclear what the modifier attaches to"),
            IssueSubtype("inversion", "倒装、省略", "inversion or ellipsis"),
            IssueSubtype("reference", "指代不清", "an unclear pronoun or reference"),
            IssueSubtype("tense_voice", "时态、被动", "tense, aspect, or passive voice"),
            IssueSubtype("lost_midway", "长句中途走偏", "lost the structure midway through a long sentence"),
        ),
        spanHint = "划出结构走偏的那一段，也可以整句",
    ),
    COMPOSITIONAL(
        id = 5,
        shortLabel = "语义",
        title = "组合语义层",
        titleEn = "Compositional Semantics",
        definition = "在词义和句法结构都已确定的基础上，把二者整合成一个完整的事件或状态的意义，回答“整句话在描述什么”。它不新增语言材料，只负责整合。失败时的体验是：每个词都懂、结构也清楚，但整体意思仍然不成立或很怪。",
        symptoms = listOf(
            "每个词都懂，整句意思却不对或很别扭",
            "习语、比喻、语用含义读不出来",
            "中高级阶段最常见的问题",
        ),
        actions = listOf(
            LayerAction.SHOW_TRANSLATION,
            LayerAction.CONTEXT_REPLAY,
            LayerAction.NOTE_QUESTION,
        ),
        subtypes = listOf(
            IssueSubtype("literal_ok", "字面懂整体不懂", "understood every word but not the overall meaning"),
            IssueSubtype("figurative", "比喻、反讽", "metaphor, irony, or figurative use"),
            IssueSubtype("logic", "逻辑关系没接上", "the logical relation (cause, contrast, condition) was unclear"),
            IssueSubtype("pragmatics", "言外之意", "implied meaning or pragmatics"),
            IssueSubtype("culture", "缺文化背景", "missing cultural or domain background"),
            IssueSubtype("context", "和上下文接不上", "could not connect it with the preceding sentences"),
        ),
        spanHint = "通常是整句；也可以划出最别扭的部分",
    );

    fun subtype(id: String): IssueSubtype? = subtypes.firstOrNull { it.id == id }

    companion object {
        fun fromId(id: Int): ProblemLayer = entries.firstOrNull { it.id == id } ?: PHONETIC
    }
}

/** 某一层下的细分类型。[promptEn] 是发给 AI 的英文描述，决定了它能生成什么样的语料。 */
data class IssueSubtype(val id: String, val label: String, val promptEn: String)

/** 没听懂的程度。0 = 未选。 */
enum class Severity(val id: Int, val label: String, val promptEn: String) {
    AFTER_TEXT(1, "看原文才懂", "understood only after seeing the text"),
    HEARD_NOT_UNDERSTOOD(2, "听出词没懂", "heard the words but did not understand them"),
    MISSED(3, "完全没听出", "did not catch it at all");

    companion object {
        fun fromId(id: Int): Severity? = entries.firstOrNull { it.id == id }
    }
}

/** 定位面板上"现在就能做"的动作。UI 决定每个动作怎么呈现。 */
enum class LayerAction(val label: String, val description: String) {
    SLOW_REPLAY("慢速重听", "以 0.7 倍速再听一遍，注意连读和弱读的位置"),
    WORD_BY_WORD("逐词播放", "把这句话一个词一个词地读给你听"),
    SHADOW_SCORE("跟读评分", "自己读一遍，看哪个音素和标准发音对不上"),
    REVEAL_TEXT("显示原文", "看文本确认刚才听到的是哪个词"),
    ADD_VOCAB("加入生词本", "把没反应过来的词收进生词本，带着这句话一起复习"),
    SHOW_TRANSLATION("查看翻译", "对照中文，确认词义和整句意思"),
    LOOKUP_WORD("查词", "点句子里的单词查释义"),
    CONTEXT_REPLAY("联系上下文", "回看前两句，整句意义往往依赖上下文"),
    NOTE_QUESTION("记下疑问", "写下你卡住的地方，交给 AI 讲解或以后复习"),
}
