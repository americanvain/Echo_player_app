package com.echoplayer.app.ui.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echoplayer.app.data.remote.PracticeItemDto
import com.echoplayer.app.data.remote.PracticeTypes
import com.echoplayer.app.ui.common.ScoreRing
import com.echoplayer.app.ui.common.Tag
import com.echoplayer.app.ui.common.echoViewModel
import com.echoplayer.app.ui.theme.EchoColors

/**
 * 答题器。题型参考成熟产品：闪卡（Anki / 百词斩）、选词与听写填空（多邻国 / 每日英语听力）、
 * 句子重组（多邻国）、辨音对（ELSA）、跟读评分（英语流利说）、选译文（多邻国）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PracticeSessionScreen(setId: String, onBack: () -> Unit) {
    val vm = echoViewModel(key = "session-$setId") { PracticeSessionViewModel(it, setId) }
    val st by vm.state.collectAsStateWithLifecycle()
    val level by vm.recordingLevel.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startRecording()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(st.message) { st.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    DisposableEffect(Unit) { onDispose { vm.saveAndExit() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(st.set?.title ?: "练习", style = MaterialTheme.typography.titleMedium)
                        if (st.total > 0 && !st.finished) {
                            Text("第 ${st.index + 1} / ${st.total} 题 · 已对 ${st.correctCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = { vm.saveAndExit(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { if (!st.finished) TextButton(onClick = { vm.skip() }) { Text("跳过") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { if (st.total == 0) 0f else st.index.toFloat() / st.total },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
            if (st.finished) {
                FinishedView(correct = st.correctCount, total = st.total, onBack = onBack)
                return@Column
            }
            val item = st.item
            if (item == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(PracticeTypes.label(item.type), color = item.layer?.let { EchoColors.layer(it) } ?: MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    item.prompt?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                when (item.type) {
                    PracticeTypes.FLASHCARD -> Flashcard(item, st.revealed, onReveal = { vm.reveal() }, onSpeak = { vm.speak(item.speak ?: item.text.orEmpty(), 0.9f) })
                    PracticeTypes.MINIMAL_PAIR -> ListenPrompt(onPlay = { vm.replay() }, onSlow = { vm.replay(slow = true) }, big = true)
                    PracticeTypes.CLOZE_LISTEN -> {
                        ListenPrompt(onPlay = { vm.replay() }, onSlow = { vm.replay(slow = true) }, big = false)
                        item.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    }
                    PracticeTypes.SHADOW -> {
                        item.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        ListenPrompt(onPlay = { vm.replay() }, onSlow = { vm.replay(slow = true) }, big = false)
                    }
                    PracticeTypes.EXPLAIN -> item.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    else -> {
                        item.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        if (item.speak != null) ListenPrompt(onPlay = { vm.replay() }, onSlow = { vm.replay(slow = true) }, big = false)
                    }
                }

                when (item.type) {
                    PracticeTypes.FLASHCARD -> {
                        if (st.revealed && st.answered == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { vm.flashcardAnswer(false) }, modifier = Modifier.weight(1f)) { Text("还不认识") }
                                Button(onClick = { vm.flashcardAnswer(true) }, modifier = Modifier.weight(1f)) { Text("认识") }
                            }
                        }
                    }
                    PracticeTypes.REORDER -> Reorder(item, st.picked, onPick = { vm.pickChunk(it) }, onUnpick = { vm.unpickChunk(it) }, answered = st.answered != null)
                    PracticeTypes.SHADOW -> {
                        ShadowControls(
                            recording = st.recording, seconds = st.recordingSec, level = level, assessing = st.assessing,
                            onToggle = { if (st.recording) vm.stopRecording() else startRecording() },
                            onPlayMine = { vm.playMyRecording() },
                        )
                        st.shadowResult?.let { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ScoreRing(r.overall.accuracy, "准确度", size = 68.dp)
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    val bad = r.words.filter { it.score < 60 }.map { it.word }
                                    Text(if (bad.isEmpty()) "读得不错" else "还要注意：${bad.joinToString("、")}", style = MaterialTheme.typography.bodyMedium)
                                    Text("完整 ${r.overall.completeness} · 流利 ${r.overall.fluency}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    PracticeTypes.EXPLAIN -> {
                        if (st.answered == null) Button(onClick = { vm.markExplainDone() }) { Text("明白了") }
                    }
                    else -> {
                        val options = if (item.type == PracticeTypes.MINIMAL_PAIR) item.pair else item.options
                        Options(options, item.answer, st.answered, onPick = { vm.answer(it) }, onSpeakOption = if (item.type == PracticeTypes.MINIMAL_PAIR) { o -> vm.speak(o, 0.9f) } else null)
                    }
                }

                if (st.revealed) {
                    st.correct?.let { ok ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (ok) Icons.Default.Check else Icons.Default.Close, null, tint = if (ok) EchoColors.Green else EchoColors.Red)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (ok) "对了" else "正确答案：${item.answer ?: item.answer_chunks.joinToString(" ")}",
                                color = if (ok) EchoColors.Green else EchoColors.Red,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    item.translation?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item.explanation?.takeIf { it.isNotBlank() && item.type != PracticeTypes.EXPLAIN }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.speak != null && item.type != PracticeTypes.FLASHCARD) {
                        OutlinedButton(onClick = { vm.replay() }) { Icon(Icons.Default.VolumeUp, null, Modifier.size(18.dp)) }
                    }
                    Button(
                        onClick = { vm.next() },
                        enabled = st.answered != null,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (st.index == st.total - 1) "完成" else "下一题") }
                }
            }
        }
    }
}

@Composable
private fun ListenPrompt(onPlay: () -> Unit, onSlow: () -> Unit, big: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(if (big) 72.dp else 48.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.VolumeUp, "再听一遍", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(if (big) 34.dp else 24.dp))
        }
        OutlinedButton(onClick = onSlow) { Icon(Icons.Default.Slideshow, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("慢速") }
    }
}

@Composable
private fun Flashcard(item: PracticeItemDto, revealed: Boolean, onReveal: () -> Unit, onSpeak: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !revealed, onClick = onReveal).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(item.text.orEmpty(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        IconButton(onClick = onSpeak) { Icon(Icons.Default.VolumeUp, "朗读") }
        if (revealed) {
            item.translation?.split('\n')?.filter { it.isNotBlank() }?.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        } else {
            Text("点一下翻开", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Options(options: List<String>, answer: String?, answered: String?, onPick: (String) -> Unit, onSpeakOption: ((String) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { opt ->
            val isAnswer = answered != null && opt.equals(answer, ignoreCase = true)
            val isPicked = answered == opt
            val border by animateColorAsState(
                when {
                    isAnswer -> EchoColors.Green
                    isPicked -> EchoColors.Red
                    else -> MaterialTheme.colorScheme.outline
                },
                label = "opt-border",
            )
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.5.dp, border, RoundedCornerShape(10.dp))
                    .background(
                        when {
                            isAnswer -> EchoColors.Green.copy(alpha = 0.10f)
                            isPicked -> EchoColors.Red.copy(alpha = 0.10f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable(enabled = answered == null) { onPick(opt) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(opt, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (answered != null && onSpeakOption != null) {
                    IconButton(onClick = { onSpeakOption(opt) }) { Icon(Icons.Default.VolumeUp, "朗读", Modifier.size(18.dp)) }
                }
                if (isAnswer) Icon(Icons.Default.Check, null, tint = EchoColors.Green)
                else if (isPicked) Icon(Icons.Default.Close, null, tint = EchoColors.Red)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reorder(item: PracticeItemDto, picked: List<Int>, onPick: (Int) -> Unit, onUnpick: (Int) -> Unit, answered: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 已经拼好的
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp),
        ) {
            if (picked.isEmpty()) {
                Text("按顺序点下面的词块", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    picked.forEach { i ->
                        Text(
                            item.chunks.getOrElse(i) { "" },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable(enabled = !answered) { onUnpick(i) }.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        // 待选的
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item.chunks.forEachIndexed { i, chunk ->
                if (i in picked) return@forEachIndexed
                Text(
                    chunk,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .clickable(enabled = !answered) { onPick(i) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ShadowControls(recording: Boolean, seconds: Double, level: Float, assessing: Boolean, onToggle: () -> Unit, onPlayMine: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.height(46.dp).weight(1f).clip(RoundedCornerShape(23.dp))
                .background(if (recording) EchoColors.Red else MaterialTheme.colorScheme.primary)
                .clickable(enabled = !assessing, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (recording) Box(Modifier.fillMaxWidth(0.25f + level * 0.75f).height(46.dp).background(Color.White.copy(alpha = 0.18f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        assessing -> "评分中…"
                        recording -> "停止 ${"%.1f".format(seconds)}s"
                        else -> "跟读"
                    },
                    color = Color.White, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        OutlinedButton(onClick = onPlayMine) { Icon(Icons.Default.Hearing, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("我的") }
    }
}

@Composable
private fun FinishedView(correct: Int, total: Int, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ScoreRing(if (total == 0) 0 else correct * 100 / total, "正确率", size = 110.dp)
            Text("做完了：$correct / $total", style = MaterialTheme.typography.titleLarge)
            Text(
                "做对的题所对应的问题记录已经标记为已解决。还想再练可以在练习集菜单里「重新开始」。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onBack) { Text("返回练习区") }
        }
    }
}
