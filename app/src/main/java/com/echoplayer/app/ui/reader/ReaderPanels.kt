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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoplayer.app.data.db.ChatMessageEntity
import com.echoplayer.app.data.repo.GlossItem
import com.echoplayer.app.ui.common.Tag

/**
 * 本句词汇：离线词典从这句里挑出的难词，一行一个词加一句释义。点它等于点句子里的那个词。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceGlossRow(items: List<GlossItem>, onTap: (Int) -> Unit) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("本句词汇", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { g ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onTap(g.index) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .widthIn(max = 260.dp),
                ) {
                    Text(g.word, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        g.brief,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 问 AI（Echo_player 第四部分"对话式教学"在听读页里的形态）。
 * 快捷问题 + 输入框 + 这句上的问答记录。离线时由本机规则回答，答案带"离线"标记。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiPanel(
    messages: List<ChatMessageEntity>,
    suggestions: List<String>,
    busy: Boolean,
    serverConfigured: Boolean,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    fun send(q: String) {
        val t = q.trim()
        if (t.isEmpty() || busy) return
        onAsk(t); input = ""
    }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("问 AI", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            if (!serverConfigured) Tag("离线", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (messages.isNotEmpty()) TextButton(onClick = onClear) { Text("清空", style = MaterialTheme.typography.labelMedium) }
        }
        messages.forEach { m -> ChatBubble(m) }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("思考中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            suggestions.forEach { q -> SuggestionChip(onClick = { send(q) }, label = { Text(q) }, enabled = !busy) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(if (serverConfigured) "关于这句话，想问什么？" else "问某个词、这句怎么读、什么意思…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(input) }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy) {
                Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ChatBubble(m: ChatMessageEntity) {
    val user = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(m.text, style = MaterialTheme.typography.bodyMedium)
            if (!user && !m.fromServer) {
                Spacer(Modifier.height(2.dp))
                Text("离线回答", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
