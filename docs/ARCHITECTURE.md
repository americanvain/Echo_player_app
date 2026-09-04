# 客户端架构

目标：**先把"显示 + 跟读 + 评分 + 问题定位 + 记录"跑起来**，同时把 Echo_player 完整设计需要的接口位置都留好，服务器上线时客户端不用重构。

## 分层

```
ui/            Jetpack Compose 页面 + ViewModel（每个页面一个 VM，只依赖 EchoApp 容器）
  library/     书架：内置资源、导入 TXT/PDF/粘贴、进度、删除、轮询服务器状态
  reader/      听读 + 跟读页：上下文 → 当前句 → 翻译 → 评分结果；划选定位、底部播放 / 跟读 / 五层按钮
  practice/    练习区：把记录生成练习集（服务器 AI 或本机规则），以及八种题型的答题器
  history/     记录：问题 / 生词 / 跟读评分三个标签页 + 五层分布
  vocab/       生词本面板（嵌在记录页里，不单独占导航位）
  settings/    服务器地址、TTS、听读默认项
data/
  db/          Room：materials / segments / units / practice_records / issues / vocab
  local/       内置资源加载、TXT 导入、本地切句、最小对立词表、本机练习生成器
  remote/      EchoServerApi + DTO（speecheval 已有端点 + Echo_player 流水线契约）
  repo/        Material / Practice / PracticeSet / Issue / Vocab 五个仓库，UI 只和它们说话
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
| 问题定位（五层） | `IssueEntity` | `layer` 1~5、**词范围 `spanStart/spanEnd/spanText`**、**细分类型 `subtypes`**、**程度 `severity`**、`misheardAs`、上下文快照、疑问 `note`、Agent 讲解 `explanation`、是否已解决 |
| 针对性练习 | `PracticeSetEntity` | 一组题（`itemsJson`）、来源（server/local）、进度与对错 |
| 记录 → 复习 | `VocabEntity` + 上面两张表 | 生词带原句与译文；复习按熟悉度和上次复习时间排序 |

## 句子的渲染与手势

整句是**一个** `Text`，词的颜色、底色、下划线用 `AnnotatedString` 的 span 表示；命中测试走
`TextLayoutResult.getOffsetForPosition` → 字符偏移 → 词索引（`Words.charRanges` / `Words.wordIndexAt`）。
盲听时未揭开的词用 `drawWithContent` 在词的包围盒上盖色块，文字仍参与排版所以位置不跳。

手势只有一处 `pointerInput`，在 `awaitEachGesture` 里自己分三种情况：

| 情况 | 判据 | 行为 |
|---|---|---|
| 点击 | 长按超时前抬手 | 盲听时揭开这一个词；否则展开释义小卡片 |
| 长按划选 | 到长按超时仍未抬手、未超过 touch slop | 进入 `drag`，消费事件，实时更新选区 |
| 滚动 | 超时前移动超过 touch slop | 不消费，交给父层 `verticalScroll` |

早先用 `detectTapGestures` + `detectDragGesturesAfterLongPress` 两个检测器，
再给每个词套 `combinedClickable`，有两个问题：子组件的长按会把父层的长按拖动吃掉（划不动），
长按选中后抬手还会补一次 tap 把选区清掉；几十个可点击组件的涟漪与语义节点也拖慢了滑动。

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

定位必须**细到 AI 能据此出题**，所以一条记录是「句子 + 词范围 + 层 + 细分类型 + 程度 + 可选的一句话」，
全部靠点选和划拉完成，最快两下：

1. **划范围**：在句子上长按并划过，选出没听懂的那几个词。也可以不划，默认整句。
   选中后有一条操作栏：只听这一段 / 取消。
2. **选层**：底部五个按钮之一 → `ProblemLayerSheet`，带着刚才的范围进来。
3. **选细分类型**（每层 6~7 个，多选）+ **程度**（三档）。词形层多一个"听成了什么"输入框，
   它会直接变成练习里的干扰项。面板里还能再调范围。
4. 「记录」→ `IssueEntity`；「AI 讲解」→ `POST /issues/explain`，服务器不可用时退回离线模板。

细分类型定义在 `ProblemLayer.kt`，每个都带一句英文 `promptEn`，原样发给服务器，
决定了 AI 能生成什么样的语料。

## 练习链路

「练习」页 → `PracticeSetRepository.generate()`：

1. 收集未解决的问题记录、待复习的生词、最近的跟读评分；
2. 配置了服务器就 `POST /practice/generate`，由 AI 分析原因并生成语料；
3. 否则用 `LocalPracticeGenerator` 按规则出题，结构与服务器返回的完全一致；
4. 落库为 `PracticeSetEntity`，在练习区列表里显示进度。

`PracticeSessionScreen` 是八种题型的答题器（闪卡、选词、听写填空、句子重组、辨音、跟读、
选译文、讲解），跟读题复用 `/assess`。一组做完后，**答对的题所关联的问题记录自动标记为已解决**，
结果回传 `POST /practice/report` 供服务器调整下次生成。

## 后续接入点（不用改现有代码）

- 服务器 PDF 流水线：`MaterialRepository.importPdf` / `refreshRemote` 已按契约实现，只等服务器；
- 服务器合成语音：`applyRemote` 下载到 `files/audio/<material>/<unit>.wav` 并写入 `audioPath`；
- 在线翻译：`MaterialRepository.translateUnit`；
- 教学 Agent：`IssueRepository.explain`；
- 复习计划生成：把 `IssueDao.observeAll()` + `PracticeDao` 的数据交给 LLM，是新页面，不影响现有表。
