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

这是 Echo_player 第四部分。客户端把**句子、前两句上下文、译文、用户按下的层、用户写的疑问、这句上历史疑问**一起发过去。

```
POST /issues/explain
{
  "unit_text": "The harder he blew, the tighter the traveler held his cloak around him.",
  "context": ["…前两句…"],
  "translation": "…",
  "layer": 4,
  "layer_name": "Syntactic Parsing",
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
