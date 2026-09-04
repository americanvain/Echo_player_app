# Echo Player · Android

[Echo_player](https://github.com/americanvain/Echo_player) 的手机客户端：**听读 → 跟读评分 → 问题定位 → 记录复习**。

两种练习模式：

1. **自由听读**：导入 TXT / PDF（或直接粘贴），按句播放、跟读、标记没听懂的地方。
2. **针对性练习**：把标记下来的记录交给服务器 AI 分析，按问题产生的原因生成对应语料，回来专门练。

- 跟读录音交给 [speecheval](https://github.com/americanvain) 服务逐音素评分（准确度 / 完整度 / 流利度，点单词回放自己读的那一段）
- **五层问题定位，细到能出题**：长按划过句子选出卡住的那几个词 → 按五层之一 → 从该层 6~7 个细分类型里点选（连读 / 弱读吞音 / 熟词生义 / 从句嵌套 / 言外之意…）→ 选程度。整个过程都是点选，最快两下记完。词形层还能填"听成了什么"，它会变成练习里的干扰项
- **八种题型**：闪卡、选词填空、听写填空、句子重组、辨音对、跟读评分、选译文、讲解。一组做完后答对的题所对应的问题记录自动标记为已解决
- 生词本（带原句、熟悉度、复习卡片）、翻译、学习记录（问题 / 生词 / 跟读三份记录 + 五层分布）
- 内置 10 篇 A1 ~ C1 阅读资源，离线可用；**盲听模式把每个词盖成色块**（排版不变，点一个揭一个，盖住时照样能划选标记）、单句循环、自动连播、倍速

服务器端（PDF 识别切句、TTS、翻译、教学 Agent、AI 出题）**尚未实现**，客户端已按 [docs/SERVER_API.md](docs/SERVER_API.md) 的契约写好接口，服务器上线时不用改客户端。没有服务器时，练习由本机规则从同一份记录生成，题型和数据结构完全一致。

## 安装

到 [Releases](../../releases) 下载 `app-release.apk`，Android 8.0+。首次跟读会申请麦克风权限。

要用跟读评分，先在「设置」里填 speecheval 服务地址（例如 `http://192.168.1.10:8000`），点「测试连接」。不填也能听读、盲听、记录问题、记生词。

## 页面

| 页面 | 内容 |
|---|---|
| 书架 | 内置资源 + 导入的素材，进度、待解决问题数；右下角导入 TXT / PDF / 粘贴；长按删除 |
| 听读 | 上面是前两句上下文和当前句（**点词直接出释义**，一排小图标可朗读 / 加生词 / 查词典；**长按划过选出卡住的片段**；评分后显示每词得分）；下面是播放控制、跟读区、五层定位按钮 |
| 练习 | 「生成练习」把记录变成练习集；点开逐题作答，八种题型 |
| 记录 | 三个标签页：问题定位（按层筛选、标记已解决、看讲解）、生词本、跟读评分；顶部是五层分布 |
| 设置 | 服务器地址、语速、盲听 / 翻译 / 连播默认项、五层说明 |

## 与 Echo_player 设计的对应

Echo_player 的五个部分里，**第一到第三部分（PDF → TextSegment → CandidateSentence → TTS）是服务器的活**，客户端只消费 UtteranceUnit；**第四、五部分（交互学习与记录）就是这个 App**。
详细的对应关系与代码结构见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

现在的离线兜底：TXT 在本机按段落切句；句子语音用系统 TTS；翻译只有内置资源自带的；AI 讲解退回该层的离线模板。

## 构建

```bash
export JAVA_HOME=/path/to/jdk-17 ANDROID_HOME=/path/to/android-sdk
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Release 签名与 CI 见 [docs/BUILD.md](docs/BUILD.md)。技术栈：Kotlin、Jetpack Compose (Material 3)、Room、DataStore、OkHttp、kotlinx-serialization；minSdk 26 / targetSdk 35。

## 目录

```
app/src/main/java/com/echoplayer/app/
├── EchoApp.kt / MainActivity.kt
├── audio/      TtsEngine · WavRecorder · ClipPlayer
├── data/       db(Room) · local(内置资源/导入/切句) · remote(服务器契约) · repo
├── ui/         library · reader · practice · history · vocab · settings · navigation · theme
└── util/
app/src/main/assets/materials/   内置阅读资源（tools/build_bundled_materials.py 生成）
docs/                            SERVER_API · ARCHITECTURE · BUILD
```

## 路线图

- [x] 显示 + 跟读 + 评分 + 生词本 + 记录（v0.1.0）
- [x] 五层定位细化到可出题（划选 + 细分类型 + 程度）、练习区与八种题型、本机出题兜底（v0.2.0）
- [x] 听读页交互返工：手势重写、点词出释义、盲听盖词（v0.2.1）
- [ ] 服务器：PDF → 句子 → TTS 流水线（客户端已就绪）
- [ ] 服务器：翻译、教学 Agent（`/issues/explain`）、AI 出题（`/practice/generate`）
- [ ] 间隔重复排期，按遗忘曲线安排练习集
- [ ] `source_ref` 扩展到视频字幕时间轴
