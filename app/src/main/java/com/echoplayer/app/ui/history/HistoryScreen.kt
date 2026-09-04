package com.echoplayer.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echoplayer.app.data.db.IssueEntity
import com.echoplayer.app.data.db.PracticeRecordEntity
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.data.model.Severity
import com.echoplayer.app.ui.common.EmptyState
import com.echoplayer.app.ui.common.Tag
import com.echoplayer.app.ui.common.echoViewModel
import com.echoplayer.app.ui.common.formatTime
import com.echoplayer.app.ui.theme.EchoColors
import com.echoplayer.app.ui.vocab.VocabPane

/**
 * Echo_player 第五部分"记录"：问题时间线 + 跟读评分时间线。
 * 顶部的五层分布让用户一眼看出自己主要卡在哪一层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenUnit: (materialId: String, unitId: String) -> Unit) {
    val vm = echoViewModel { HistoryViewModel(it) }
    val issues by vm.issues.collectAsStateWithLifecycle()
    val counts by vm.layerCounts.collectAsStateWithLifecycle()
    val practices by vm.practices.collectAsStateWithLifecycle()
    val practiceCount by vm.practiceCount.collectAsStateWithLifecycle()
    val avg by vm.averageAccuracy.collectAsStateWithLifecycle()
    val vocabCount by vm.vocabCount.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var layerFilter by remember { mutableStateOf<Int?>(null) }
    var showResolved by remember { mutableStateOf(true) }

    Scaffold(topBar = { TopAppBar(title = { Text("学习记录", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StatsHeader(counts = counts, totalIssues = issues.size, openIssues = issues.count { !it.resolved }, practiceCount = practiceCount, avg = avg)
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("问题 ${issues.size}") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("生词 $vocabCount") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("跟读 $practiceCount") })
            }
            if (tab == 1) {
                VocabPane(onOpenUnit = onOpenUnit)
            } else if (tab == 0) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = layerFilter == null, onClick = { layerFilter = null }, label = { Text("全部") })
                    ProblemLayer.entries.forEach { l ->
                        FilterChip(selected = layerFilter == l.id, onClick = { layerFilter = if (layerFilter == l.id) null else l.id }, label = { Text(l.shortLabel) })
                    }
                }
                val list = issues.filter { (layerFilter == null || it.layer == layerFilter) && (showResolved || !it.resolved) }
                if (list.isEmpty()) {
                    EmptyState(Icons.Default.History, "还没有问题记录", "在跟读页按下「哪里没听懂」的五个按钮之一，就会记在这里")
                } else {
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(list, key = { it.id }) { i ->
                            IssueCard(i, onOpen = { onOpenUnit(i.materialId, i.unitId) }, onResolved = { vm.setResolved(i, it) }, onDelete = { vm.deleteIssue(i) })
                        }
                    }
                }
            } else {
                if (practices.isEmpty()) {
                    EmptyState(Icons.Default.History, "还没有跟读记录", "在跟读页按「跟读」录一句，评分会记在这里")
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(practices, key = { it.id }) { p ->
                            PracticeCard(p, onOpen = { onOpenUnit(p.materialId, p.unitId) }, onPlay = { vm.playRecording(p) }, onDelete = { vm.deletePractice(p) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(counts: List<com.echoplayer.app.data.db.LayerCount>, totalIssues: Int, openIssues: Int, practiceCount: Int, avg: Double?) {
    val max = counts.maxOfOrNull { it.n } ?: 0
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("问题", "$totalIssues", "待解决 $openIssues", Modifier.weight(1f))
            StatTile("跟读", "$practiceCount", "次", Modifier.weight(1f))
            StatTile("平均准确度", avg?.let { "${it.toInt()}" } ?: "–", "分", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text("问题分布", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(54.dp)) {
            ProblemLayer.entries.forEach { l ->
                val n = counts.firstOrNull { it.layer == l.id }?.n ?: 0
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("$n", style = MaterialTheme.typography.labelMedium, color = EchoColors.layer(l.id))
                    Box(
                        Modifier.fillMaxWidth(0.7f).height((if (max == 0) 2f else 2f + 24f * n / max).dp)
                            .clip(RoundedCornerShape(3.dp)).background(EchoColors.layer(l.id)),
                    )
                    Text(l.shortLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueCard(i: IssueEntity, onOpen: () -> Unit, onResolved: (Boolean) -> Unit, onDelete: () -> Unit) {
    val layer = ProblemLayer.fromId(i.layer)
    val color = EchoColors.layer(layer.id)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row {
            Box(Modifier.width(5.dp).fillMaxHeight().height(60.dp).background(color))
            Column(Modifier.padding(12.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(layer.title, color = color)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(i.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Text(i.unitText, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (!i.isWholeSentence && !i.spanText.isNullOrBlank()) {
                    Text(
                        "卡在：${i.spanText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                val detail = buildList {
                    addAll(i.subtypeIds.mapNotNull { id -> layer.subtype(id)?.label })
                    Severity.fromId(i.severity)?.let { add(it.label) }
                    i.misheardAs?.takeIf { it.isNotBlank() }?.let { add("听成了 $it") }
                }
                if (detail.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 4.dp)) {
                        detail.forEach { Tag(it, color = color) }
                    }
                }
                i.note?.let { Text("疑问：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                i.explanation?.let { Text("讲解：${it.take(120)}", style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 4.dp), maxLines = 3, overflow = TextOverflow.Ellipsis) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = i.resolved, onCheckedChange = onResolved)
                    Text(if (i.resolved) "已解决" else "标记为已解决", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun PracticeCard(p: PracticeRecordEntity, onOpen: () -> Unit, onPlay: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp)) {
                Text("${p.accuracy}", style = MaterialTheme.typography.headlineMedium, color = EchoColors.score(p.accuracy))
                Text("准确度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.unitText, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("完整 ${p.completeness} · 流利 ${p.fluency} · ${formatTime(p.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.recordingPath != null) IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, "播放录音") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
