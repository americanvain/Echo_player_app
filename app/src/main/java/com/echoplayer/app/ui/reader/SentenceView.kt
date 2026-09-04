package com.echoplayer.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.echoplayer.app.data.remote.WordResult
import com.echoplayer.app.ui.theme.EchoColors
import com.echoplayer.app.util.Words

/** 划选出来的词范围（闭区间）。 */
data class WordSpan(val start: Int, val end: Int) {
    operator fun contains(i: Int) = i in start..end
    val size: Int get() = end - start + 1

    companion object {
        fun of(a: Int, b: Int) = WordSpan(minOf(a, b), maxOf(a, b))
    }
}

/**
 * 当前句子。三种交互：
 * - 点词：打开单词面板（听读法 / 加生词 / 查词典）；
 * - 长按后拖动：**划拉选出一段**，用于精确定位问题的位置；
 * - 有评分结果时按分数着色，生词本里的词加下划线。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceView(
    text: String,
    words: List<WordResult>?,
    vocab: Set<String>,
    selection: WordSpan?,
    onWordTap: (word: String, index: Int, scored: WordResult?) -> Unit,
    onSelectionChange: (WordSpan?) -> Unit,
) {
    val tokens = remember(text) { Words.tokenize(text) }
    val scored = remember(text, words) { alignScores(tokens.map { it.key }, words) }
    val bounds = remember(text) { mutableMapOf<Int, Rect>() }
    val selectionColor = MaterialTheme.colorScheme.primary

    fun indexAt(pos: Offset): Int? =
        bounds.entries.firstOrNull { it.value.contains(pos) }?.key
            // 落在行间空隙时，取同一行里最近的词
            ?: bounds.entries.filter { pos.y >= it.value.top - 6f && pos.y <= it.value.bottom + 6f }
                .minByOrNull { kotlin.math.abs(it.value.center.x - pos.x) }?.key

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(text) {
                var anchor: Int? = null
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        anchor = indexAt(pos)
                        anchor?.let { onSelectionChange(WordSpan(it, it)) }
                    },
                    onDrag = { change, _ ->
                        val a = anchor ?: return@detectDragGesturesAfterLongPress
                        indexAt(change.position)?.let { onSelectionChange(WordSpan.of(a, it)) }
                    },
                    onDragEnd = { anchor = null },
                    onDragCancel = { anchor = null },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tokens.forEach { tok ->
            val w = scored[tok.index]
            val inVocab = tok.key in vocab
            val isSelected = selection?.contains(tok.index) == true
            val bg = when {
                isSelected -> selectionColor.copy(alpha = 0.22f)
                w != null -> EchoColors.scoreSoft(w.score)
                else -> Color.Transparent
            }
            val fg = when {
                isSelected -> selectionColor
                w != null -> EchoColors.score(w.score)
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                tok.display,
                style = MaterialTheme.typography.bodyLarge,
                color = fg,
                fontWeight = if (isSelected || (w != null && w.score < 60)) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = if (inVocab) TextDecoration.Underline else null,
                modifier = Modifier
                    .onGloballyPositioned { bounds[tok.index] = it.boundsInParent() }
                    .clip(RoundedCornerShape(6.dp))
                    .background(bg)
                    .combinedClickable(
                        onClick = {
                            if (selection != null) onSelectionChange(null) else onWordTap(tok.display, tok.index, w)
                        },
                        onLongClick = { onSelectionChange(WordSpan(tok.index, tok.index)) },
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
            .combinedClickable(onClick = onReveal)
            .padding(vertical = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("盲听模式 · 先听，点这里显示原文", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}
