package com.echoplayer.app.ui.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.echoplayer.app.data.remote.WordResult
import com.echoplayer.app.ui.theme.EchoColors
import com.echoplayer.app.util.Words

/**
 * 当前句子：每个词单独可点。有评分结果时按分数着色，生词本里的词加下划线。
 * 评分结果的词序列与文本词序列按顺序对齐（服务器按参考文本的词切分，两边顺序一致）。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SentenceView(
    text: String,
    words: List<WordResult>?,
    vocab: Set<String>,
    onWordTap: (word: String, scored: WordResult?) -> Unit,
    onWordLongPress: (String) -> Unit,
) {
    val tokens = Words.tokenize(text)
    val scored = alignScores(tokens.map { it.key }, words)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tokens.forEach { tok ->
            val w = scored[tok.index]
            val inVocab = tok.key in vocab
            val bg = w?.let { EchoColors.scoreSoft(it.score) } ?: Color.Transparent
            val fg = w?.let { EchoColors.score(it.score) } ?: MaterialTheme.colorScheme.onSurface
            Text(
                tok.display,
                style = MaterialTheme.typography.bodyLarge,
                color = fg,
                fontWeight = if (w != null && w.score < 60) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = if (inVocab) TextDecoration.Underline else null,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .combinedClickable(
                        onClick = { onWordTap(tok.display, w) },
                        onLongClick = { onWordLongPress(tok.display) },
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** 把评分词按顺序贴回文本词上；数量不一致时按 key 做最近匹配，避免整体错位。 */
private fun alignScores(keys: List<String>, words: List<WordResult>?): Map<Int, WordResult> {
    if (words.isNullOrEmpty()) return emptyMap()
    val out = HashMap<Int, WordResult>()
    var j = 0
    keys.forEachIndexed { i, key ->
        if (j >= words.size) return@forEachIndexed
        if (key.isEmpty()) return@forEachIndexed
        val wk = Words.normalize(words[j].word)
        if (wk == key) {
            out[i] = words[j]; j++
        } else {
            // 向前找最多 2 个位置
            val k = (j + 1..minOf(j + 2, words.size - 1)).firstOrNull { Words.normalize(words[it].word) == key }
            if (k != null) { out[i] = words[k]; j = k + 1 }
        }
    }
    return out
}

@Composable
fun ContextLines(lines: List<String>, dimmed: Boolean = true) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dimmed) 0.8f else 1f),
            )
        }
    }
}

@Composable
fun HiddenSentence(onReveal: () -> Unit) {
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickableNoRipple(onReveal)
            .padding(vertical = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("盲听模式 · 先听，点这里显示原文", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoRipple(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)
