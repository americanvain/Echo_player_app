# Echo Player · Android

[Echo_player](https://github.com/americanvain/Echo_player) 的手机客户端：**听读 → 跟读评分 → 问题定位 → 记录复习**。

- 导入 TXT / PDF（或直接粘贴），按句列表播放、显示、跟读
- 跟读录音交给 [speecheval](https://github.com/americanvain) 服务逐音素评分（准确度 / 完整度 / 流利度，点单词回放自己读的那一段）
- **五层问题定位**：听不懂时按一下「语音 / 词形 / 词义 / 句法 / 语义」，看这一层的定义与典型表现，立刻做针对性动作（慢速重听、逐词播放、显示原文、看翻译、联系上下文、跟读评分），写下疑问，交给 AI 讲解
- 生词本（带原句、熟悉度、复习卡片）、翻译、学习记录（问题时间线 + 评分时间线 + 五层分布）
- 内置 10 篇 A1 ~ C1 阅读资源，离线可用；盲听模式、单句循环、自动连播、倍速

服务器端（PDF 识别切句、TTS、翻译、教学 Agent）**尚未实现**，客户端已按 [docs/SERVER_API.md](docs/SERVER_API.md) 的契约写好接口，服务器上线时不用改客户端。

## 安装

到 [Releases](../../releases) 下载 `app-release.apk`，Android 8.0+。首次跟读会申请麦克风权限。

要用跟读评分，先在「设置」里填 speecheval 服务地址（例如 `http://192.168.1.10:8000`），点「测试连接」。不填也能听读、盲听、记录问题、记生词。

## 页面

| 页面 | 内容 |
|---|---|
| 书架 | 内置资源 + 导入的素材，进度、待解决问题数；右下角导入 TXT / PDF / 粘贴；长按删除 |
| 听读 | 上面是前两句上下文和当前句（点词：听读法 / 加生词 / 查词典；评分后显示每词得分）；下面是播放控制、跟读区、五层定位按钮 |
| 生词本 | 搜索、熟悉度筛选、释义与笔记、回到原句、复习卡片 |
| 记录 | 问题定位时间线（按层筛选、标记已解决、看 AI 讲解）、跟读评分时间线、五层分布 |
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
├── ui/         library · reader · vocab · history · settings · navigation · theme
└── util/
app/src/main/assets/materials/   内置阅读资源（tools/build_bundled_materials.py 生成）
docs/                            SERVER_API · ARCHITECTURE · BUILD
```

## 路线图

- [x] 显示 + 跟读 + 评分 + 五层定位 + 生词本 + 记录（本版本）
- [ ] 服务器：PDF → 句子 → TTS 流水线（客户端已就绪）
- [ ] 服务器：翻译、教学 Agent（`/issues/explain`）
- [ ] 基于记录生成复习计划
- [ ] `source_ref` 扩展到视频字幕时间轴
