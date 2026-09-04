# 客户端架构

目标：**先把"显示 + 跟读 + 评分 + 问题定位 + 记录"跑起来**，同时把 Echo_player 完整设计需要的接口位置都留好，服务器上线时客户端不用重构。

## 分层

```
ui/            Jetpack Compose 页面 + ViewModel（每个页面一个 VM，只依赖 EchoApp 容器）
  library/     书架：内置资源、导入 TXT/PDF/粘贴、进度、删除、轮询服务器状态
  reader/      听读 + 跟读页：上下文 → 当前句 → 翻译 → 评分结果；底部播放 / 跟读 / 五层定位
  vocab/       生词本：搜索、熟悉度、笔记、复习卡片
  history/     记录：问题时间线（按层筛选、已解决）、跟读评分时间线、五层分布
  settings/    服务器地址、TTS、听读默认项
data/
  db/          Room：materials / segments / units / practice_records / issues / vocab
  local/       内置资源加载、TXT 导入、本地切句（离线兜底）
  remote/      EchoServerApi + DTO（speecheval 已有端点 + Echo_player 流水线契约）
  repo/        Material / Practice / Issue / Vocab 四个仓库，UI 只和它们说话
audio/         TtsEngine（本机朗读）、WavRecorder（16k 单声道 WAV）、ClipPlayer（回放录音 / 片段 / 服务器语音）
```

依赖注入是手写的 `EchoApp`（Application）里的 lazy 单例，没有引入 Hilt。

## 数据模型与 Echo_player 设计的对应

| Echo_player 概念 | 实体 | 说明 |
|---|---|---|
| 一本 PDF / 一篇文章 | `MaterialEntity` | 带 `sourceType`（bundled/txt/pdf/remote）、`status`（ready/processing/failed）、`remoteId` |
| TextSegment | `SegmentEntity` | `segmentIndex` 严格递增；本地导入时一个自然段一个 segment |
| UtteranceUnit | `UnitEntity` | `text` / `translation` / `sourceRef` / `audioPath` / `audioDuration`；`audioPath` 为空时用本机 TTS |
| 录音跟读 + 评分 | `PracticeRecordEntity` | 保存完整 `AssessResult` JSON 与录音路径，可回放任意一次 |
| 问题定位（五层） | `IssueEntity` | `layer` 1~5、用户疑问 `note`、Agent 讲解 `explanation`、是否已解决 |
| 记录 → 复习 | `VocabEntity` + 上面两张表 | 生词带原句与译文；复习按熟悉度和上次复习时间排序 |

## 播放链路

`ReaderViewModel.playCurrent()`：
1. `UnitEntity.audioPath` 存在 → `ClipPlayer.playFile`（MediaPlayer，支持倍速）；
2. 否则 → `TtsEngine.speak`（系统 TTS，en-US，倍速）。

播放完成回调统一进 `onUtteranceDone()`，在那里处理单句循环、自动连播、逐词播放队列、联系上下文连读。

## 跟读评分链路

`WavRecorder`（AudioRecord，16 kHz / mono / 16-bit → 标准 WAV）→ `EchoServerApi.assess`（multipart）→ `AssessResult`
→ `PracticeRecordEntity` 落库 → `AssessResultView` 呈现。

呈现规则沿用 speecheval 网页 demo：
- 词按分数着色：≥80 绿，≥60 黄，否则红；
- 音素 `error` 且服务器给了 `actual` 才写「读成了 X」；`warn` 只写「不太稳定」；
- 点单词 / 词块回放自己录音里的那一段（`ClipPlayer.playWavRange`，前后各留 40 ms）。

## 问题定位链路（最重要）

跟读页底部五个按钮 → `ProblemLayerSheet`：
1. 展示该层的定义、典型表现（来自 Echo_player `design.md` / `classification.md`）；
2. 「现在可以做的」动作直接驱动播放器：慢速重听、逐词播放、跟读评分、显示原文、查看翻译、联系上下文连读、查词 / 加生词；
3. 用户写疑问 → 「记录这个问题」→ `IssueEntity`；
4. 「AI 讲解」→ `POST /issues/explain`（句子 + 上下文 + 层 + 疑问 + 历史）；服务器不可用时退回离线模板。

「记录」页把问题按层统计成分布图，是 Echo_player 第五部分"用于给大模型输入、生成复习方案"的原料。

## 后续接入点（不用改现有代码）

- 服务器 PDF 流水线：`MaterialRepository.importPdf` / `refreshRemote` 已按契约实现，只等服务器；
- 服务器合成语音：`applyRemote` 下载到 `files/audio/<material>/<unit>.wav` 并写入 `audioPath`；
- 在线翻译：`MaterialRepository.translateUnit`；
- 教学 Agent：`IssueRepository.explain`；
- 复习计划生成：把 `IssueDao.observeAll()` + `PracticeDao` 的数据交给 LLM，是新页面，不影响现有表。
