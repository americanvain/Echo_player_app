package com.echoplayer.app.ui.reader

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echoplayer.app.data.model.LayerAction
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.ui.common.Tag
import com.echoplayer.app.ui.common.echoViewModel
import com.echoplayer.app.ui.theme.EchoColors
import com.echoplayer.app.util.Words
import kotlinx.coroutines.launch

private val RATES = listOf(0.6f, 0.75f, 0.9f, 1.0f, 1.15f, 1.3f)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    materialId: String,
    initialUnitIndex: Int?,
    initialUnitId: String?,
    onBack: () -> Unit,
) {
    val vm = echoViewModel(key = "reader-$materialId") { ReaderViewModel(it, materialId, initialUnitIndex, initialUnitId) }
    val st by vm.state.collectAsStateWithLifecycle()
    val attempts by vm.attempts.collectAsStateWithLifecycle()
    val unitIssues by vm.unitIssues.collectAsStateWithLifecycle()
    val best by vm.bestByUnit.collectAsStateWithLifecycle()
    val openIssues by vm.openIssuesByUnit.collectAsStateWithLifecycle()
    val vocab by vm.vocabWords.collectAsStateWithLifecycle()
    val level by vm.recordingLevel.collectAsStateWithLifecycle()
    val chat by vm.chatMessages.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showList by remember { mutableStateOf(false) }
    var layerSheet by remember { mutableStateOf<ProblemLayer?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording() else scope.launch { snackbar.showSnackbar("没有麦克风权限就没法跟读") }
    }
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startRecording()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(st.message) {
        st.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    fun handleAction(a: LayerAction) {
        when (a) {
            LayerAction.SLOW_REPLAY -> { layerSheet = null; if (st.selection != null) vm.playSelection() else vm.playSlow() }
            LayerAction.WORD_BY_WORD -> { layerSheet = null; vm.playWordByWord() }
            LayerAction.SHADOW_SCORE -> { layerSheet = null; startRecording() }
            LayerAction.REVEAL_TEXT -> { layerSheet = null; vm.revealAll() }
            LayerAction.ADD_VOCAB, LayerAction.LOOKUP_WORD -> {
                layerSheet = null; vm.revealAll()
                if (st.selection != null) vm.addSelectionToVocab()
                else scope.launch { snackbar.showSnackbar("点句子里的单词就能看释义、加生词") }
            }
            LayerAction.SHOW_TRANSLATION -> {
                layerSheet = null; vm.revealAll()
                if (!st.showTranslation) vm.toggleTranslation()
                vm.translateCurrent()
            }
            LayerAction.CONTEXT_REPLAY -> { layerSheet = null; vm.playContext() }
            LayerAction.NOTE_QUESTION -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(st.material?.title ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (st.units.isNotEmpty()) {
                            Text("第 ${st.index + 1} / ${st.units.size} 句", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { vm.toggleBlindMode() }) {
                        Icon(
                            if (st.blindMode) Icons.Filled.VisibilityOff else Icons.Outlined.Visibility,
                            if (st.blindMode) "关闭盲听模式" else "盲听模式：先盖住单词",
                            tint = if (st.blindMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showList = true }) { Icon(Icons.AutoMirrored.Filled.List, "目录") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { if (st.units.isEmpty()) 0f else (st.index + 1f) / st.units.size },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
            val unit = st.unit
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (unit == null) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    ContextLines(
                        lines = st.context.map { it.text },
                        onJump = { i -> vm.jumpTo(st.index - st.context.size + i) },
                    )
                    SentenceView(
                        text = unit.text,
                        words = st.result?.words,
                        vocab = vocab,
                        selection = st.selection,
                        maskedWords = st.maskedWords,
                        onWordTap = { vm.tapWord(it) },
                        onSelectionChange = { vm.setSelection(it) },
                    )
                    st.wordCard?.let { card ->
                        WordCard(
                            word = card.word,
                            translation = card.translation,
                            phonetic = card.phonetic,
                            loading = card.loading,
                            hint = card.hint,
                            inVocab = Words.normalize(card.word) in vocab,
                            onSpeak = { vm.speakWord(card.word) },
                            onToggleVocab = { vm.toggleVocab(card.word) },
                            onDismiss = { vm.dismissWordCard() },
                        )
                    }
                    if (st.selection != null) {
                        SelectionBar(
                            text = vm.selectionLabel().orEmpty(),
                            onPlay = { vm.playSelection() },
                            onClear = { vm.setSelection(null) },
                        )
                    } else if (st.maskedWords.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "盲听中：点一个词揭开它",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { vm.revealAll() }) { Text("全部显示") }
                        }
                    } else {
                        Text(
                            "点词看释义，长按划过可以选出没听懂的一段",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (st.translationVisible) {
                        val t = unit.translation
                        if (!t.isNullOrBlank()) {
                            Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            TextButton(onClick = { vm.translateCurrent() }, enabled = !st.translating, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                if (st.translating) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Translate, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (st.serverConfigured) "翻译这一句" else "这一句还没有翻译")
                            }
                        }
                    }
                    if (st.maskedWords.isEmpty()) {
                        SentenceGlossRow(items = st.gloss, onTap = { vm.tapWord(it) })
                    }
                    if (unitIssues.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            unitIssues.forEach { i ->
                                val layer = ProblemLayer.fromId(i.layer)
                                Tag(
                                    (if (i.resolved) "✓ " else "") + layer.shortLabel + (i.spanText?.let { "：$it" } ?: ""),
                                    color = EchoColors.layer(layer.id),
                                )
                            }
                        }
                    }
                    if (st.assessing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("正在评分…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    st.result?.let { r ->
                        AssessResultView(
                            result = r,
                            attempts = attempts,
                            showPhonemes = st.showPhonemes,
                            canReplay = st.recordingPath != null,
                            onPlayWord = { vm.playWordClip(it) },
                            onPlayAll = { vm.playMyRecording() },
                            onShowAttempt = { vm.showAttempt(it) },
                        )
                    }
                    if (st.result == null && st.recordingPath != null && !st.assessing) {
                        OutlinedButton(onClick = { vm.playMyRecording() }) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("听我的录音")
                        }
                    }
                    AiPanel(
                        messages = chat,
                        suggestions = vm.chatSuggestions,
                        busy = st.chatBusy,
                        serverConfigured = st.serverConfigured,
                        onAsk = { vm.askAi(it) },
                        onClear = { vm.clearChat() },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ---------------- 底部控制区 ----------------
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RateChip(st.rate) { vm.setRate(nextRate(st.rate)) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.prev() }, enabled = st.hasPrev) { Icon(Icons.Default.SkipPrevious, "上一句") }
                        FilledIconButton(
                            onClick = { vm.togglePlay() },
                            modifier = Modifier.size(56.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(),
                        ) {
                            Icon(if (st.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, if (st.isPlaying) "停止" else "播放", Modifier.size(30.dp))
                        }
                        IconButton(onClick = { vm.next() }, enabled = st.hasNext) { Icon(Icons.Default.SkipNext, "下一句") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.toggleTranslation() }) {
                            Icon(Icons.Default.Translate, "显示翻译", tint = if (st.showTranslation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.toggleLoop() }) {
                            Icon(Icons.Default.Repeat, "单句循环", tint = if (st.loop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.toggleAutoAdvance() }) {
                            Icon(Icons.Outlined.PlaylistPlay, "自动连播", tint = if (st.autoAdvance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        RecordButton(
                            recording = st.recording,
                            seconds = st.recordingSec,
                            level = level,
                            enabled = unit != null && !st.assessing,
                            onClick = { if (st.recording) vm.stopRecording() else startRecording() },
                            modifier = Modifier.weight(1.4f),
                        )
                        OutlinedButton(onClick = { vm.playMyRecording() }, enabled = st.recordingPath != null && !st.recording, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Hearing, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("我的录音", maxLines = 1)
                        }
                        OutlinedButton(onClick = { vm.playCurrent() }, enabled = unit != null && !st.recording, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.VolumeUp, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("原音", maxLines = 1)
                        }
                    }
                    Text(
                        st.selection?.let { "「${vm.selectionLabel()}」是哪一层的问题？" } ?: "哪里没听懂？",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (st.selection != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        ProblemLayer.entries.forEach { layer ->
                            LayerButton(layer, enabled = unit != null, modifier = Modifier.weight(1f)) { layerSheet = layer }
                        }
                    }
                }
            }
        }
    }

    if (showList) {
        UnitListSheet(units = st.units, current = st.index, best = best, openIssues = openIssues, onJump = { vm.jumpTo(it) }, onDismiss = { showList = false })
    }
    layerSheet?.let { layer ->
        val unit = st.unit
        if (unit != null) {
            ProblemLayerSheet(
                layer = layer,
                sentence = unit.text,
                initialSpan = st.selection,
                maskedWords = st.maskedWords,
                serverConfigured = st.serverConfigured,
                onAction = { handleAction(it) },
                onRecord = { draft -> vm.recordIssue(draft)?.id },
                onExplain = { draft, id -> vm.explain(draft, id) },
                onDismiss = { layerSheet = null; vm.setSelection(null) },
            )
        }
    }
}

@Composable
private fun SelectionBar(text: String, onPlay: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已选：$text",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onPlay, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.VolumeUp, "只听这一段", Modifier.size(18.dp)) }
        IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Close, "取消选择", Modifier.size(18.dp)) }
    }
}

private fun nextRate(r: Float): Float {
    val i = RATES.indexOfFirst { kotlin.math.abs(it - r) < 0.01f }
    return RATES[if (i < 0) 3 else (i + 1) % RATES.size]
}

@Composable
private fun RateChip(rate: Float, onClick: () -> Unit) {
    Text(
        "${"%.2f".format(rate).trimEnd('0').trimEnd('.')}x",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
private fun RecordButton(recording: Boolean, seconds: Double, level: Float, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (recording) EchoColors.Red else MaterialTheme.colorScheme.primary, label = "rec-bg")
    val pulse by animateFloatAsState(if (recording) 0.25f + level * 0.75f else 0f, label = "rec-pulse")
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            Box(Modifier.fillMaxWidth(pulse).height(44.dp).background(Color.White.copy(alpha = 0.18f)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (recording) "停止 ${"%.1f".format(seconds)}s" else "跟读",
                color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun LayerButton(layer: ProblemLayer, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = EchoColors.layer(layer.id)
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = if (enabled) 0.12f else 0.05f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text("${layer.id}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        Text(layer.shortLabel, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}
