package com.echoplayer.app.data.model

/**
 * Echo Player 的核心概念：听不懂时，问题出在五个处理层面中的哪一层。
 *
 * 五层定义来自 Echo_player/design.md 与 classification.md。每一层附带
 * "现在就能做的动作"（[actions]），供离线时的定位面板使用；接入教学 Agent 后，
 * 这些动作会作为 prompt 的一部分交给模型（见 docs/SERVER_API.md `/issues/explain`）。
 */
enum class ProblemLayer(
    val id: Int,
    val shortLabel: String,
    val title: String,
    val titleEn: String,
    val definition: String,
    val symptoms: List<String>,
    val actions: List<LayerAction>,
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
    );

    companion object {
        fun fromId(id: Int): ProblemLayer = entries.firstOrNull { it.id == id } ?: PHONETIC
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
