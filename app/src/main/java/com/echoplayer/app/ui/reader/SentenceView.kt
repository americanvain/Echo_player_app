package com.echoplayer.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.echoplayer.app.data.remote.WordResult
import com.echoplayer.app.ui.theme.EchoColors
import com.echoplayer.app.util.Words
import kotlinx.coroutines.withTimeoutOrNull

/** 划选出来的词范围（闭区间）。 */
data class WordSpan(val start: Int, val end: Int) {
    operator fun contains(i: Int) = i in start..end
    val size: Int get() = end - start + 1

    companion object {
        fun of(a: Int, b: Int) = WordSpan(minOf(a, b), maxOf(a, b))
    }
}

/**
 * 当前句子。
 *
 * 整句是**一个** [Text]，词的样式用 `AnnotatedString` 的 span 表示，手势只有一处
 * `pointerInput`，命中测试走文字布局（`TextLayoutResult.getOffsetForPosition`）。
 * 早先的写法是每个词一个带 `combinedClickable` 的 Text 放进 FlowRow：
 * 子组件的长按会把父层的长按拖动吃掉（划不动），几十个可点击组件也拖慢了滑动。
 *
 * 交互：
 * - **点词** → [onWordTap]，弹出小卡片显示释义；
 * - **长按后划过** → 选出一段，用于精确定位问题；
 * - 盲听模式下未揭开的词盖成色块（[maskedWords]），点一下揭开那一个词，长按划选照常可用。
 */
@Composable
fun SentenceView(
    text: String,
    words: List<WordResult>?,
    vocab: Set<String>,
    selection: WordSpan?,
    maskedWords: Set<Int> = emptySet(),
    onWordTap: (index: Int) -> Unit,
    onSelectionChange: (WordSpan?) -> Unit,
) {
    val tokens = remember(text) { Words.tokenize(text) }
    val scored = remember(text, words) { alignScores(tokens.map { it.key }, words) }
    val baseColor = MaterialTheme.colorScheme.onSurface
    val selectionColor = MaterialTheme.colorScheme.primary
    val maskColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 词 i 在整串里的字符区间
    val ranges = remember(text) { Words.charRanges(tokens) }

    val annotated = remember(text, scored, vocab, selection, maskedWords, baseColor, selectionColor) {
        val builder = AnnotatedString.Builder(Words.display(tokens))
        tokens.forEachIndexed { i, tok ->
            val r = ranges[i]
            val w = scored[i]
            val isSelected = selection?.contains(i) == true
            val masked = i in maskedWords
            val fg = when {
                masked -> Color.Transparent
                isSelected -> selectionColor
                w != null -> EchoColors.score(w.score)
                else -> baseColor
            }
            val bg = when {
                masked -> Color.Transparent
                isSelected -> selectionColor.copy(alpha = 0.20f)
                w != null -> EchoColors.scoreSoft(w.score)
                else -> Color.Transparent
            }
            builder.addStyle(
                SpanStyle(
                    color = fg,
                    background = bg,
                    fontWeight = if (!masked && (isSelected || (w != null && w.score < 60))) FontWeight.SemiBold else null,
                    textDecoration = if (!masked && tok.key in vocab) TextDecoration.Underline else null,
                ),
                r.first, r.last + 1,
            )
        }
        builder.toAnnotatedString()
    }

    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    // 手势块只在 text 变化时重建，回调和选区通过 State 读到最新值，避免拖动中途重建手势
    val currentSelection = rememberUpdatedState(selection)
    val onWordTapState = rememberUpdatedState(onWordTap)

    fun wordAt(pos: Offset): Int? {
        val l = layout ?: return null
        val offset = l.getOffsetForPosition(pos).coerceIn(0, maxOf(0, l.layoutInput.text.length - 1))
        val idx = Words.wordIndexAt(ranges, offset)
        if (idx < 0) return null
        // 点在行尾空白处时不要误伤最后一个词
        return if (boxOf(l, ranges[idx]).inflate(6f).contains(pos)) idx else null
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            // 点击、长按划选、以及"让父层滚动"三种情况在同一个手势循环里分开处理。
            // 用现成的 detectTapGestures + detectDragGesturesAfterLongPress 两个检测器会打架：
            // 长按选中后抬手，点击检测器还会再报一次 tap，把刚选好的选区清掉。
            .pointerInput(text) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var res = "cancel"
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) { res = "tap"; break }
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) { res = "scroll"; break }
                        }
                        res
                    }
                    when (outcome) {
                        // 超时没抬手也没滑动 = 长按，进入划选
                        null -> {
                            val anchor = wordAt(down.position)
                            if (anchor != null) {
                                onSelectionChange(WordSpan(anchor, anchor))
                                drag(down.id) { change ->
                                    wordAt(change.position)?.let { onSelectionChange(WordSpan.of(anchor, it)) }
                                    change.consume()
                                }
                            }
                        }
                        "tap" -> {
                            if (currentSelection.value != null) onSelectionChange(null)
                            else wordAt(down.position)?.let { onWordTapState.value(it) }
                        }
                        // "scroll" / "cancel"：一律不消费，交给父层滚动
                        else -> Unit
                    }
                }
            }
            .drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
                maskedWords.forEach { i ->
                    val r = ranges.getOrNull(i) ?: return@forEach
                    val box = boxOf(l, r)
                    val selected = selection?.contains(i) == true
                    // 被盖住的词选中时，色块换成主题色，否则看不出选了什么
                    drawRoundRect(
                        color = if (selected) selectionColor.copy(alpha = 0.85f) else maskColor.copy(alpha = 0.75f),
                        topLeft = Offset(box.left, box.top + 1f),
                        size = Size(box.width, box.height - 2f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            },
    )
}

/** 一个词占的矩形；跨行时取并集。 */
private fun boxOf(l: TextLayoutResult, range: IntRange): Rect {
    var rect: Rect? = null
    for (i in range) {
        val b = runCatching { l.getBoundingBox(i) }.getOrNull() ?: continue
        rect = rect?.let {
            Rect(minOf(it.left, b.left), minOf(it.top, b.top), maxOf(it.right, b.right), maxOf(it.bottom, b.bottom))
        } ?: b
    }
    return rect ?: Rect.Zero
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

/**
 * 上文。留足行距和间隔，和当前句拉开距离；点一句可以跳回去。
 */
@Composable
fun ContextLines(lines: List<String>, onJump: (Int) -> Unit) {
    if (lines.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        lines.forEachIndexed { i, line ->
            Row(Modifier.fillMaxWidth()) {
                Spacer(
                    Modifier.width(3.dp).height(if (line.length > 60) 44.dp else 22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(line) { detectTapGestures { onJump(i) } },
                )
            }
        }
    }
}
