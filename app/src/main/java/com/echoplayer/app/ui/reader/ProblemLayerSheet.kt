package com.echoplayer.app.ui.reader

import androidx.compose.foundation.background
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echoplayer.app.data.model.LayerAction
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.data.remote.ExplainResponse
import com.echoplayer.app.ui.theme.EchoColors
import kotlinx.coroutines.launch

/**
 * 问题定位面板（Echo_player 的"侧边栏"在手机上的形态）。
 *
 * 流程：用户在某一句上按下五层之一 → 看到这一层的定义与典型表现 → 立刻可做的动作 →
 * 写下疑问 → 「记录」进时间线；「AI 讲解」把句子、上下文、层级、疑问一起交给教学 Agent。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProblemLayerSheet(
    layer: ProblemLayer,
    sentence: String,
    serverConfigured: Boolean,
    onAction: (LayerAction) -> Unit,
    onRecord: suspend (note: String?) -> Long?,
    onExplain: suspend (note: String?, issueId: Long?) -> Pair<ExplainResponse, Boolean>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf("") }
    var recordedId by remember { mutableStateOf<Long?>(null) }
    var explaining by remember { mutableStateOf(false) }
    var explanation by remember { mutableStateOf<ExplainResponse?>(null) }
    var fromServer by remember { mutableStateOf(false) }
    val color = EchoColors.layer(layer.id)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Text("${layer.id}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(layer.title, style = MaterialTheme.typography.titleLarge)
                    Text(layer.titleEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                sentence,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
            )
            Text(layer.definition, style = MaterialTheme.typography.bodyMedium)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("典型表现", style = MaterialTheme.typography.labelLarge, color = color)
                layer.symptoms.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("现在可以做的", style = MaterialTheme.typography.labelLarge, color = color)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    layer.actions.forEach { a ->
                        AssistChip(
                            onClick = { onAction(a) },
                            label = { Text(a.label) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = 0.10f), labelColor = color),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("你卡在哪里？（可选）") },
                placeholder = { Text("例如：没听出 “would have” 连读；不知道 run 在这里的意思") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = recordedId == null,
                    onClick = { scope.launch { recordedId = onRecord(note.ifBlank { null }) } },
                ) {
                    if (recordedId != null) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(4.dp)); Text("已记录") } else Text("记录这个问题")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !explaining,
                    onClick = {
                        scope.launch {
                            explaining = true
                            if (recordedId == null) recordedId = onRecord(note.ifBlank { null })
                            val (resp, remote) = onExplain(note.ifBlank { null }, recordedId)
                            explanation = resp; fromServer = remote
                            explaining = false
                        }
                    },
                ) {
                    if (explaining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(4.dp)); Text("AI 讲解") }
                }
            }
            explanation?.let { e ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.08f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(if (fromServer) "AI 讲解" else "离线讲解（未连接教学服务器）", style = MaterialTheme.typography.labelLarge, color = color)
                    Text(e.explanation, style = MaterialTheme.typography.bodyMedium)
                    if (fromServer && e.examples.isNotEmpty()) {
                        Text("例句", style = MaterialTheme.typography.labelLarge, color = color)
                        e.examples.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (e.quiz.isNotEmpty()) {
                        Text("小测", style = MaterialTheme.typography.labelLarge, color = color)
                        e.quiz.forEach { q ->
                            var show by remember { mutableStateOf(false) }
                            Column {
                                Text(q.question, style = MaterialTheme.typography.bodyMedium)
                                if (q.options.isNotEmpty()) q.options.forEach { Text("  ○ $it", style = MaterialTheme.typography.bodyMedium) }
                                if (show) Text("答案：${q.answer}", style = MaterialTheme.typography.bodyMedium, color = color)
                                else OutlinedButton(onClick = { show = true }) { Text("看答案") }
                            }
                        }
                    }
                }
            }
            if (!serverConfigured) {
                Text(
                    "提示：配置教学服务器后，「AI 讲解」会结合这句话、上下文和你的疑问给出针对性讲解、例句与小测。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
