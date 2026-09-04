package com.echoplayer.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echoplayer.app.data.db.PracticeSetEntity
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.ui.common.EmptyState
import com.echoplayer.app.ui.common.Tag
import com.echoplayer.app.ui.common.echoViewModel
import com.echoplayer.app.ui.common.relativeTime
import com.echoplayer.app.ui.theme.EchoColors

/**
 * 练习区。Echo_player 的第二种练习模式：
 * 把记录下来的问题、生词、跟读评分交给服务器 AI 分析，按问题产生的原因生成针对性语料；
 * 服务器没上线时用本机规则从同一份记录出题，题型和结构完全一致。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(onOpenSet: (String) -> Unit) {
    val vm = echoViewModel { PracticeViewModel(it) }
    val sets by vm.sets.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val openIssues by vm.openIssues.collectAsStateWithLifecycle()
    val vocabCount by vm.vocabCount.collectAsStateWithLifecycle()
    val practiceCount by vm.practiceCount.collectAsStateWithLifecycle()
    val serverConfigured by vm.serverConfigured.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("针对性练习", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.generate() },
                icon = {
                    if (generating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    else Icon(Icons.Default.AutoAwesome, contentDescription = null)
                },
                text = { Text(if (generating) "生成中…" else "生成练习") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SourceSummary(openIssues, vocabCount, practiceCount, serverConfigured)
            analysis?.let {
                Column(
                    Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp),
                ) {
                    Text("AI 分析", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (sets.isEmpty()) {
                EmptyState(
                    Icons.Default.FitnessCenter,
                    "还没有练习集",
                    "先去听读、标记没听懂的地方、收几个生词或跟读几句，然后点「生成练习」。\n" +
                        "配置了服务器就由 AI 分析你的记录出题，没配置也能用本机规则出题。",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sets, key = { it.id }) { set ->
                        SetCard(set, onOpen = { onOpenSet(set.id) }, onDelete = { vm.delete(set.id) }, onReset = { vm.reset(set) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceSummary(issues: Int, vocab: Int, scores: Int, serverConfigured: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("待解决问题", issues, Modifier.weight(1f))
            Chip("生词", vocab, Modifier.weight(1f))
            Chip("跟读记录", scores, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (serverConfigured) "点「生成练习」把这些记录交给服务器 AI，按问题原因生成对应的语料。"
            else "还没配置服务器，将用本机规则从你的记录出题；配置后由 AI 生成更贴合的语料。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Chip(label: String, n: Int, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
        Text("$n", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetCard(set: PracticeSetEntity, onOpen: () -> Unit, onDelete: () -> Unit, onReset: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val done = set.completedAt != null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(set.title, style = MaterialTheme.typography.titleMedium)
                    if (set.description.isNotBlank()) {
                        Text(set.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.Refresh, "更多") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("重新开始") }, onClick = { menu = false; onReset() })
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tag(if (set.source == "server") "AI 生成" else "本机生成", color = if (set.source == "server") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                set.layers.split(',').mapNotNull { it.trim().toIntOrNull() }.forEach { id ->
                    val l = ProblemLayer.fromId(id)
                    Tag(l.shortLabel, color = EchoColors.layer(l.id))
                }
                Tag("${set.total} 题", color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (set.total == 0) 0f else set.lastIndex.toFloat() / set.total },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when {
                        done -> "已完成 · 对 ${set.correct} / ${set.total}"
                        set.lastIndex > 0 -> "进行到第 ${set.lastIndex + 1} 题 · 已对 ${set.correct}"
                        else -> "还没开始"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done) EchoColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(relativeTime(set.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
