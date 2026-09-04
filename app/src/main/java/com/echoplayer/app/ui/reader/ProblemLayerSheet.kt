package com.echoplayer.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echoplayer.app.data.model.LayerAction
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.data.model.Severity
import com.echoplayer.app.data.remote.ExplainResponse
import com.echoplayer.app.data.repo.IssueRepository
import com.echoplayer.app.ui.theme.EchoColors
import com.echoplayer.app.util.Words
import kotlinx.coroutines.launch

/**
 * 问题定位面板（Echo_player 的"侧边栏"在手机上的形态）。
 *
 * 三步，全部是点选，最快两下就能记完：
 * 1. **范围**：读入跟读页划拉出来的片段，也可以在这里点词调整，或者选"整句"；
 * 2. **细分类型**：这一层下面 5~7 个选项，多选；词形层还能填"听成了什么"；再选程度；
 * 3. **补充**：可选的一句话，然后「记录」或「AI 讲解」。
 *
 * 记下来的东西精确到「句子 + 词范围 + 层 + 细分类型 + 程度」，
 * 足以让服务器 AI 生成针对性的语料（docs/SERVER_API.md `/practice/generate`）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProblemLayerSheet(
    layer: ProblemLayer,
    sentence: String,
    initialSpan: WordSpan?,
    serverConfigured: Boolean,
    onAction: (LayerAction) -> Unit,
    onRecord: suspend (IssueRepository.Draft) -> Long?,
    onExplain: suspend (IssueRepository.Draft, issueId: Long?) -> Pair<ExplainResponse, Boolean>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val tokens = remember(sentence) { Words.tokenize(sentence) }

    var span by remember { mutableStateOf(initialSpan) }
    val subtypes = remember { mutableListOf<String>().toMutableStateList() }
    var severity by remember { mutableStateOf(0) }
    var misheard by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showDefinition by remember { mutableStateOf(false) }
    var recordedId by remember { mutableStateOf<Long?>(null) }
    var explaining by remember { mutableStateOf(false) }
    var explanation by remember { mutableStateOf<ExplainResponse?>(null) }
    var fromServer by remember { mutableStateOf(false) }
    val color = EchoColors.layer(layer.id)

    val spanText = span?.let { s -> tokens.filter { it.index in s }.joinToString(" ") { it.display } }

    fun draft() = IssueRepository.Draft(
        layer = layer,
        spanStart = span?.start ?: -1,
        spanEnd = span?.end ?: -1,
        spanText = spanText,
        subtypes = subtypes.toList(),
        misheardAs = misheard.takeIf { it.isNotBlank() },
        severity = severity,
        note = note.takeIf { it.isNotBlank() },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- 标题 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Text("${layer.id}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(layer.title, style = MaterialTheme.typography.titleLarge)
                    Text(layer.titleEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showDefinition = !showDefinition }) {
                    Text(if (showDefinition) "收起" else "这层是什么")
                    Icon(if (showDefinition) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (showDefinition) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(layer.definition, style = MaterialTheme.typography.bodyMedium)
                    layer.symptoms.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ---- ① 范围 ----
            SectionLabel("① 卡在哪里", color, layer.spanHint)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                tokens.forEach { tok ->
                    val selected = span?.contains(tok.index) == true
                    Text(
                        tok.display,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) color else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                val cur = span
                                span = when {
                                    cur == null -> WordSpan(tok.index, tok.index)
                                    cur.size == 1 && cur.start == tok.index -> null
                                    else -> WordSpan.of(cur.start, tok.index)
                                }
                            }
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = span == null,
                    onClick = { span = null },
                    label = { Text("整句都卡") },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    spanText?.let { "已选 ${span?.size} 个词" } ?: "点词选起点，再点一个词选到那里",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- ② 是哪一种 ----
            SectionLabel("② 是哪一种", color, "可以多选")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                layer.subtypes.forEach { st ->
                    val selected = st.id in subtypes
                    FilterChip(
                        selected = selected,
                        onClick = { if (selected) subtypes.remove(st.id) else subtypes.add(st.id) },
                        label = { Text(st.label) },
                        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.16f),
                            selectedLabelColor = color,
                            selectedLeadingIconColor = color,
                        ),
                    )
                }
            }
            if (layer == ProblemLayer.LEXICAL_FORM) {
                OutlinedTextField(
                    value = misheard,
                    onValueChange = { misheard = it },
                    label = { Text("你听成了什么？（可选，会变成练习的干扰项）") },
                    placeholder = { Text("例如：听成了 sink") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---- 程度 ----
            SectionLabel("③ 到什么程度", color, null)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Severity.entries.forEach { s ->
                    FilterChip(
                        selected = severity == s.id,
                        onClick = { severity = if (severity == s.id) 0 else s.id },
                        label = { Text(s.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.16f),
                            selectedLabelColor = color,
                        ),
                    )
                }
            }

            // ---- 现在可以做的 ----
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                layer.actions.forEach { a ->
                    AssistChip(
                        onClick = { onAction(a) },
                        label = { Text(a.label) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = 0.10f), labelColor = color),
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("再补一句？（可选）") },
                placeholder = { Text("例如：不知道 would have 连在一起听是这个样子") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            // ---- 记录 / 讲解 ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = recordedId == null,
                    onClick = { scope.launch { recordedId = onRecord(draft()) } },
                ) {
                    if (recordedId != null) {
                        Icon(Icons.Default.Check, null); Spacer(Modifier.width(4.dp)); Text("已记录")
                    } else Text("记录")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !explaining,
                    onClick = {
                        scope.launch {
                            explaining = true
                            val d = draft()
                            if (recordedId == null) recordedId = onRecord(d)
                            val (resp, remote) = onExplain(d, recordedId)
                            explanation = resp; fromServer = remote
                            explaining = false
                        }
                    },
                ) {
                    if (explaining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(4.dp)); Text("AI 讲解") }
                }
            }
            if (recordedId != null) {
                Text(
                    "已记进「记录」页，之后可以在「练习」页据此生成针对性练习。",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }

            explanation?.let { e ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.08f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(if (fromServer) "AI 讲解" else "离线讲解（未连接教学服务器）", style = MaterialTheme.typography.labelLarge, color = color)
                    Text(e.explanation, style = MaterialTheme.typography.bodyMedium)
                    if (e.examples.isNotEmpty()) {
                        Text("例句", style = MaterialTheme.typography.labelLarge, color = color)
                        e.examples.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                    e.quiz.forEach { q ->
                        var show by remember { mutableStateOf(false) }
                        Column {
                            Text(q.question, style = MaterialTheme.typography.bodyMedium)
                            q.options.forEach { Text("  ○ $it", style = MaterialTheme.typography.bodyMedium) }
                            if (show) Text("答案：${q.answer}", style = MaterialTheme.typography.bodyMedium, color = color)
                            else OutlinedButton(onClick = { show = true }) { Text("看答案") }
                        }
                    }
                }
            }
            if (!serverConfigured) {
                Text(
                    "配置教学服务器后，「AI 讲解」会结合这句话、你划的位置和选的类型给出针对性讲解与例句。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(title: String, color: Color, hint: String?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = color)
        if (hint != null) {
            Spacer(Modifier.width(8.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
