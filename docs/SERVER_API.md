# 服务器契约

客户端把所有网络请求集中在 `EchoServerApi.kt`。地址在「设置」里填，形如 `http://192.168.1.10:8000`。

分两部分：**已经存在的**（speecheval 服务，仓库 `speech_evaluating`）和**尚未实现的**（Echo_player 流水线）。
后者是客户端已经按之写好的契约，服务端照着实现即可，客户端不用改。

## 一、speecheval 现有端点（已接通）

| 端点 | 用途 | 客户端调用点 |
|---|---|---|
| `GET /health` | 设置页「测试连接」 | `SettingsViewModel.testConnection` |
| `POST /assess` multipart `audio`(wav 16k mono) + `text` | 跟读评分 | `PracticeRepository.assess` |
| `GET /articles` | 同步服务器题库到书架 | `MaterialRepository.syncRemoteArticles` |

`/assess` 的响应结构见 `speech_evaluating/src/speecheval/schema.py`，客户端 DTO 在 `data/remote/Dtos.kt`（`AssessResult`），
解析时 `ignoreUnknownKeys = true`，服务端加字段不会弄坏旧客户端。

## 二、Echo_player 流水线（契约，待实现）

对应 Echo_player 设计里的五个部分：PDF → TextSegment → CandidateSentence → TTS → UtteranceUnit；
以及第四/五部分需要的翻译与教学 Agent。

### 2.1 导入素材

```
POST /materials/import
multipart/form-data:
  file      二进制（application/pdf 或 text/plain）
  title     字符串
  language  "en"（默认）
→ 202
{ "material_id": "m_20260904_ab12", "status": "processing", "message": "已入队" }
```

服务器异步执行：OCR / 文本提取 → 分段（300~800 词，段落优先，不重不漏）→ 切句（只用原文，不改写）→ 每句 TTS → 落盘。

### 2.2 查询状态与拉取结果

```
GET /materials/{material_id}
→ 200
{
  "material_id": "m_20260904_ab12",
  "status": "processing" | "ready" | "failed",
  "message": "正在识别第 12/80 页",      // 可选
  "progress": 0.15,                      // 可选，0~1
  "title": "Harry Potter and the Philosopher's Stone",
  "language": "en",
  "segments": [                          // ready 时才有
    {
      "segment_id": 1,                   // 从 1 起严格递增
      "source_ref": "1.txt",             // 对应 TextSegment 文件；将来可换成 SRT 时间戳
      "text": "整段原文（可选）",
      "units": [
        {
          "unit_id": "1",                // 素材内唯一，客户端存为 "<material>#u<unit_id>"
          "text": "Mr. and Mrs. Dursley, of number four, Privet Drive, were proud to say that they were perfectly normal, thank you very much.",
          "translation": "……",           // 可选；没有时客户端可按需调 /translate
          "source_ref": "1.txt",
          "audio": { "path": "audio/1.wav", "duration": 5.8 }   // 可选；有则客户端下载并优先播放
        }
      ]
    }
  ]
}
```

客户端行为：`status=processing` 时书架上显示进度并每 5 秒轮询；`ready` 时一次性写入本地库并逐句下载语音；`failed` 时显示 `message`，可长按删除或重试。

### 2.3 下载句子语音

```
GET /materials/{material_id}/audio/{unit_id}
→ 200 audio/wav（或 audio/mpeg）
```

### 2.4 翻译

```
POST /translate
{ "text": "…", "source": "en", "target": "zh" }
→ { "translation": "…" }
```

### 2.5 教学 Agent（问题定位 → 讲解）

这是 Echo_player 第四部分。定位的**精度**是关键：客户端不只发"哪一层"，还发**卡在哪几个词**、
**是这一层的哪一种问题**、**到什么程度**，这样 AI 才能生成对得上的讲解和语料。

```
POST /issues/explain
{
  "unit_text": "The harder he blew, the tighter the traveler held his cloak around him.",
  "context": ["…前两句…"],
  "translation": "…",
  "layer": 4,
  "layer_name": "Syntactic Parsing",

  "span_text": "the tighter the traveler held",   // 用户划出来的片段，null = 整句
  "span_start": 5,                                  // 词索引（按空白切分），闭区间
  "span_end": 9,
  "subtypes": [                                     // 该层下的细分，可多选
    { "id": "pattern", "description": "comparative, conditional, or another structural pattern" }
  ],
  "misheard_as": null,                              // 词形层专有：听成了什么
  "severity": 2,                                    // 1 看原文才懂 / 2 听出词没懂 / 3 完全没听出
  "note": "the harder ... the tighter 这个结构没反应过来",
  "history": ["上次在这句记的疑问"]
}
→
{
  "explanation": "这是 'the + 比较级, the + 比较级' 结构……",
  "examples": ["The more you practice, the easier it gets."],
  "quiz": [ { "question": "…", "options": ["…"], "answer": "…" } ]
}
```

#### 细分类型全表

`subtypes[].id` 取自下表，`description` 是客户端一并带上的英文说明，服务器可以直接塞进 prompt。

| 层 | id |
|---|---|
| 1 语音 | `linking` `reduction` `speed` `unfamiliar_sound` `similar_sound` `stress` `boundary` |
| 2 词形 | `misheard` `known_not_recognized` `proper_noun` `contraction_number` `inflection` `rare_word` |
| 3 词义 | `unknown_word` `known_word_new_sense` `phrase` `idiom` `polysemy` `nuance` |
| 4 句法 | `svo` `clause` `modifier` `inversion` `reference` `tense_voice` `lost_midway` |
| 5 语义 | `literal_ok` `figurative` `logic` `pragmatics` `culture` `context` |

权威定义在客户端 `data/model/ProblemLayer.kt`，加选项时两边一起改。

### 2.6 生成针对性练习

Echo_player 的第二种练习模式：把记录交给服务器，AI 分析问题产生的原因，生成对应的语料。

```
POST /practice/generate
{
  "issues": [ { …与 /issues/explain 同构，另有 "id" 与 "created_at"… } ],
  "vocab":  [ { "word": "shone", "context": "…", "translation": "…", "familiarity": 0, "review_count": 2 } ],
  "scores": [ { "unit_text": "I think this", "accuracy": 55, "fluency": 40, "created_at": 0,
                "errors": [ { "word": "think", "canonical": "θ", "actual": "s" } ] } ],
  "max_sets": 5,
  "language": "en"
}
→
{
  "analysis": "你的问题集中在连读和 θ/s 的分辨上……",     // 可选，显示在练习区顶部
  "sets": [
    {
      "id": "srv-20260904-1",
      "title": "连读听写",
      "description": "针对你标记的 3 处连读",
      "layers": [1],
      "items": [ … ]
    }
  ]
}
```

#### 题型与字段

`items[].type` 决定用哪些字段。客户端本机生成器出的是同一套结构（`data/local/LocalPracticeGenerator.kt`）。

| type | 界面 | 用到的字段 |
|---|---|---|
| `flashcard` | 闪卡，点开看释义，答"认识 / 不认识" | `text`(词) `translation` `speak` `vocab_word` |
| `choice` | 选词填空 | `text`(含 `____` 的句子) `speak` `options` `answer` |
| `cloze_listen` | 听整句，选出空缺的片段 | `speak`(整句) `text`(挖空后) `blank_start` `blank_end` `options` `answer` |
| `reorder` | 句子重组，按顺序点词块 | `chunks`(打乱) `answer_chunks`(正确顺序) `translation` |
| `minimal_pair` | 辨音，听一个词在两个词里选 | `pair`(两个词) `speak`(=`answer`) `answer` |
| `shadow` | 跟读并打分（走 `/assess`） | `text` `speak` `translation` |
| `translation_match` | 选出正确的中文译文 | `text` `speak` `options`(中文) `answer` |
| `explain` | 只讲解，点"明白了" | `text` |

公共字段：`id`（组内唯一）、`prompt`（题干提示）、`explanation`（答完显示）、`layer`、
`issue_ids`（这题针对哪几条问题记录；全组做完后答对的那些会被标记为已解决）。

### 2.7 回传练习结果

```
POST /practice/report
{ "set_id": "srv-20260904-1",
  "results": [ { "item_id": "i1", "correct": true, "answer": "the tighter" } ],
  "completed": true }
→ 200 {}
```

只有 `source = server` 的练习集会回传，供服务器调整下一次生成。

五层的 id 与名称（与 `ProblemLayer.kt` 一致）：

| id | 中文 | 英文 |
|---|---|---|
| 1 | 语音 / 音系层 | Phonetic / Phonological Processing |
| 2 | 词形识别层 | Lexical Form Access |
| 3 | 词义层 | Lexical Semantics |
| 4 | 句法解析层 | Syntactic Parsing |
| 5 | 组合语义层 | Compositional Semantics |

服务器不可达时客户端退回离线讲解模板（层的定义、典型表现、可做的动作），并把问题记录留在本地，接入后可补讲解。

## 三、错误约定

- 4xx/5xx 时 body 为 `{"detail": "人类可读的原因"}`（FastAPI 默认），客户端直接把 `detail` 显示给用户。
- 客户端连接超时 8 秒、读超时 60 秒（PDF 上传走同一套）。
