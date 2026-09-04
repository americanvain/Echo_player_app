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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    onOpenSettings: () -> Unit,
) {
    val vm = echoViewModel(key = "reader-$materialId") { ReaderViewModel(it, materialId, initialUnitIndex, initialUnitId) }
    val st by vm.state.collectAsStateWithLifecycle()
    val attempts by vm.attempts.collectAsStateWithLifecycle()
    val unitIssues by vm.unitIssues.collectAsStateWithLifecycle()
    val best by vm.bestByUnit.collectAsStateWithLifecycle()
    val openIssues by vm.openIssuesByUnit.collectAsStateWithLifecycle()
    val vocab by vm.vocabWords.collectAsStateWithLifecycle()
    val level by vm.recordingLevel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showList by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var wordSheet by remember { mutableStateOf<Pair<String, com.echoplayer.app.data.remote.WordResult?>?>(null) }
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
            LayerAction.SLOW_REPLAY -> { layerSheet = null; vm.playSlow() }
            LayerAction.WORD_BY_WORD -> { layerSheet = null; vm.playWordByWord() }
            LayerAction.SHADOW_SCORE -> { layerSheet = null; startRecording() }
            LayerAction.REVEAL_TEXT -> { layerSheet = null; vm.reveal() }
            LayerAction.ADD_VOCAB, LayerAction.LOOKUP_WORD -> {
                layerSheet = null; vm.reveal()
                scope.launch { snackbar.showSnackbar("点句子里的单词即可查词 / 加入生词本") }
            }
            LayerAction.SHOW_TRANSLATION -> {
                layerSheet = null; vm.reveal()
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
                        if (st.units.isNotEmpty()) Text("第 ${st.index + 1} / ${st.units.size} 句", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { showList = true }) { Icon(Icons.AutoMirrored.Filled.List, "目录") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MenuToggle("盲听模式（先听后看）", st.blindMode) { vm.toggleBlindMode(); menu = false }
                            MenuToggle("显示翻译", st.showTranslation) { vm.toggleTranslation(); menu = false }
                            MenuToggle("自动连播", st.autoAdvance) { vm.toggleAutoAdvance(); menu = false }
                            MenuToggle("单句循环", st.loop) { vm.toggleLoop(); menu = false }
                            DropdownMenuItem(text = { Text("设置") }, onClick = { menu = false; onOpenSettings() })
                        }
                    }
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
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (unit == null) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    ContextLines(st.context.map { it.text })
                    if (st.blindMode && !st.textRevealed) {
                        HiddenSentence(onReveal = { vm.reveal() })
                    } else {
                        SentenceView(
                            text = unit.text,
                            words = st.result?.words,
                            vocab = vocab,
                            onWordTap = { w, scored -> wordSheet = w to scored },
                            onWordLongPress = { vm.toggleVocab(it) },
                        )
                    }
                    if (st.showTranslation && st.textRevealed) {
                        val t = unit.translation
                        if (!t.isNullOrBlank()) {
                            Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            TextButton(onClick = { vm.translateCurrent() }, enabled = !st.translating) {
                                if (st.translating) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Translate, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (st.serverConfigured) "在线翻译这一句" else "这一句还没有翻译")
                            }
                        }
                    }
                    if (unitIssues.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            unitIssues.forEach { i ->
                                val layer = ProblemLayer.fromId(i.layer)
                                Tag(
                                    (if (i.resolved) "✓ " else "") + layer.shortLabel + (i.note?.let { "：" + it.take(12) } ?: ""),
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
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ---------------- 底部控制区 ----------------
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 播放行
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
                        IconButton(onClick = { vm.toggleLoop() }) {
                            Icon(Icons.Default.Repeat, "单句循环", tint = if (st.loop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.toggleAutoAdvance() }) {
                            Icon(Icons.Outlined.PlaylistPlay, "自动连播", tint = if (st.autoAdvance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // 跟读行
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
                    // 问题定位行
                    Text("哪里没听懂？", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
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
    wordSheet?.let { (w, scored) ->
        WordSheet(
            word = w,
            inVocab = Words.normalize(w) in vocab,
            onSpeak = { vm.speakWord(w) },
            onToggleVocab = { vm.toggleVocab(w) },
            onDismiss = { wordSheet = null },
            scored = scored,
            onPlayMine = scored?.let { sw -> { vm.playWordClip(sw) } },
        )
    }
    layerSheet?.let { layer ->
        val unit = st.unit
        if (unit != null) {
            ProblemLayerSheet(
                layer = layer,
                sentence = unit.text,
                serverConfigured = st.serverConfigured,
                onAction = { handleAction(it) },
                onRecord = { note -> vm.recordIssue(layer, note)?.id },
                onExplain = { note, id -> vm.explain(layer, note, id) },
                onDismiss = { layerSheet = null },
            )
        }
    }
}

@Composable
private fun MenuToggle(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { if (checked) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) },
        onClick = onClick,
    )
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
