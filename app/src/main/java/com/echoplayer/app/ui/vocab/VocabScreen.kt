package com.echoplayer.app.ui.vocab

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echoplayer.app.data.db.VocabEntity
import com.echoplayer.app.data.model.Familiarity
import com.echoplayer.app.ui.common.EmptyState
import com.echoplayer.app.ui.common.Tag
import com.echoplayer.app.ui.common.echoViewModel
import com.echoplayer.app.ui.common.relativeTime
import com.echoplayer.app.ui.theme.EchoColors
import com.echoplayer.app.util.Words

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabScreen(onOpenUnit: (materialId: String, unitId: String) -> Unit) {
    val vm = echoViewModel { VocabViewModel(it) }
    val all by vm.all.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val reviewIndex by vm.reviewIndex.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(-1) }
    var expanded by remember { mutableStateOf<Long?>(null) }

    if (review.isNotEmpty()) {
        ReviewScreen(entries = review, index = reviewIndex, onSpeak = { vm.speak(it) }, onAnswer = { vm.answer(it) }, onEnd = { vm.endReview() })
        return
    }

    val filtered = all.filter { (filter < 0 || it.familiarity == filter) && (query.isBlank() || it.word.contains(query.trim().lowercase()) || it.contextSentence?.lowercase()?.contains(query.trim().lowercase()) == true) }
    val dueCount = all.count { it.familiarity < Familiarity.KNOWN }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("生词本 · ${all.size}", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { vm.startReview() }, enabled = dueCount > 0) { Text("复习 ($dueCount)") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索单词或例句") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter < 0, onClick = { filter = -1 }, label = { Text("全部") })
                (0..2).forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(Familiarity.label(f)) }) }
            }
            if (filtered.isEmpty()) {
                EmptyState(Icons.Default.Style, if (all.isEmpty()) "生词本是空的" else "没有匹配的词", "在跟读页点句子里的单词，或长按单词，就能加进来")
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { e ->
                        VocabCard(
                            e = e,
                            expanded = expanded == e.id,
                            onToggle = { expanded = if (expanded == e.id) null else e.id },
                            onSpeak = { vm.speak(e.word) },
                            onDelete = { vm.delete(e) },
                            onFamiliarity = { vm.setFamiliarity(e, it) },
                            onSave = { note, tr -> vm.saveNote(e, note, tr) },
                            onOpen = { if (e.materialId != null && e.unitId != null) onOpenUnit(e.materialId, e.unitId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabCard(
    e: VocabEntity, expanded: Boolean, onToggle: () -> Unit, onSpeak: () -> Unit, onDelete: () -> Unit,
    onFamiliarity: (Int) -> Unit, onSave: (String?, String?) -> Unit, onOpen: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.displayWord.ifBlank { e.word }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Tag(Familiarity.label(e.familiarity), color = when (e.familiarity) { 2 -> EchoColors.Green; 1 -> EchoColors.Amber; else -> EchoColors.Red })
                IconButton(onClick = onSpeak) { Icon(Icons.Default.VolumeUp, "朗读") }
            }
            e.translation?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            e.contextSentence?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    e.contextTranslation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    var tr by remember(e.id) { mutableStateOf(e.translation ?: "") }
                    var note by remember(e.id) { mutableStateOf(e.note ?: "") }
                    OutlinedTextField(value = tr, onValueChange = { tr = it }, label = { Text("释义") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("笔记") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0..2).forEach { f -> FilterChip(selected = e.familiarity == f, onClick = { onFamiliarity(f) }, label = { Text(Familiarity.label(f)) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Words.dictionaryUrl(e.word)))) } }) {
                            Icon(Icons.Outlined.OpenInNew, null, Modifier.width(16.dp)); Spacer(Modifier.width(4.dp)); Text("查词典")
                        }
                        if (e.unitId != null) TextButton(onClick = onOpen) { Text("回到原句") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                        Button(onClick = { onSave(note, tr) }) { Text("保存") }
                    }
                    Text("${e.materialTitle ?: ""} · 加入于 ${relativeTime(e.addedAt)} · 复习 ${e.reviewCount} 次", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewScreen(entries: List<VocabEntity>, index: Int, onSpeak: (String) -> Unit, onAnswer: (Boolean) -> Unit, onEnd: () -> Unit) {
    var revealed by remember(index) { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("复习 ${minOf(index + 1, entries.size)} / ${entries.size}") }, actions = { TextButton(onClick = onEnd) { Text("结束") } }) }) { padding ->
        val e = entries.getOrNull(index)
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            if (e == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("这一轮复习完了", style = MaterialTheme.typography.headlineMedium)
                    Text("记不住的词会更早再次出现。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onEnd) { Text("返回生词本") }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(e.displayWord.ifBlank { e.word }, style = MaterialTheme.typography.headlineMedium)
                    IconButton(onClick = { onSpeak(e.word) }) { Icon(Icons.Default.VolumeUp, "朗读") }
                    if (revealed) {
                        e.translation?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        e.contextSentence?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        e.contextTranslation?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { onAnswer(false) }) { Text("还不认识") }
                            Button(onClick = { onAnswer(true) }) { Text("认识") }
                        }
                    } else {
                        Text("想一想意思，再翻开看", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { revealed = true }) { Text("翻开") }
                    }
                }
            }
        }
    }
}
