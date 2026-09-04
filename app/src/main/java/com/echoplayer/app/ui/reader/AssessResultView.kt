package com.echoplayer.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echoplayer.app.data.db.PracticeRecordEntity
import com.echoplayer.app.data.remote.AssessResult
import com.echoplayer.app.data.remote.PhoneResult
import com.echoplayer.app.data.remote.WordResult
import com.echoplayer.app.ui.common.MetricBar
import com.echoplayer.app.ui.common.ScoreRing
import com.echoplayer.app.ui.common.relativeTime
import com.echoplayer.app.ui.theme.EchoColors

/**
 * 评分结果。呈现规则沿用 speecheval 网页 demo 的保守策略：
 * 「读成了 X」只在 verdict=error 且服务器给出 actual 时显示；warn 只给颜色和"不太稳定"，
 * 绝不在两个信号没对上时编造。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssessResultView(
    result: AssessResult,
    attempts: List<PracticeRecordEntity>,
    showPhonemes: Boolean,
    canReplay: Boolean,
    onPlayWord: (WordResult) -> Unit,
    onPlayAll: () -> Unit,
    onShowAttempt: (PracticeRecordEntity) -> Unit,
) {
    var expandedWord by remember(result) { mutableStateOf<Int?>(null) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(result.overall.accuracy, "准确度")
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricBar("完整度", result.overall.completeness)
                    MetricBar("流利度", result.overall.fluency)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                summaryText(result),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                result.words.forEachIndexed { i, w ->
                    val color = EchoColors.score(w.score)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(EchoColors.scoreSoft(w.score))
                            .clickable {
                                expandedWord = if (expandedWord == i) null else i
                                if (canReplay) onPlayWord(w)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(w.word, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(4.dp))
                        Text("${w.score}", color = color, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            AnimatedVisibility(visible = showPhonemes && expandedWord != null) {
                val w = expandedWord?.let { result.words.getOrNull(it) }
                if (w != null) {
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(
                            if (w.oov) "${w.word} · 词典里没有这个词，发音按规则推断" else w.word,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        w.phones.forEach { p -> PhoneRow(p) }
                    }
                }
            }
            if (result.insertions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "多读了 ${result.insertions.size} 个音：" + result.insertions.joinToString(" ") { it.actual },
                    style = MaterialTheme.typography.bodySmall,
                    color = EchoColors.Amber,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                if (canReplay) {
                    TextButton(onClick = onPlayAll) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("听我的录音")
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "点单词可回放你读的那一段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (attempts.size > 1) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    attempts.take(8).forEach { a ->
                        Text(
                            "${a.accuracy} · ${relativeTime(a.createdAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = EchoColors.score(a.accuracy),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onShowAttempt(a) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneRow(p: PhoneResult) {
    val color = EchoColors.verdict(p.verdict)
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            p.canonical,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(40.dp),
        )
        Text(
            when (p.verdict) {
                "good" -> "✓"
                "warn" -> "⚠"
                else -> "✗"
            },
            color = color,
            modifier = Modifier.width(22.dp),
        )
        Column(Modifier.weight(1f)) {
            when (p.verdict) {
                "good" -> Text("${p.score}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                "warn" -> Text("这里不太稳定", style = MaterialTheme.typography.bodySmall, color = color)
                else -> {
                    val actual = p.actual
                    Text(
                        when {
                            actual == null -> "漏读了"
                            actual != p.canonical -> "读成了 $actual"
                            else -> "发音不到位"
                        },
                        style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold,
                    )
                    p.hint?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun summaryText(r: AssessResult): String {
    val errors = r.words.sumOf { w -> w.phones.count { it.verdict == "error" } }
    val warns = r.words.sumOf { w -> w.phones.count { it.verdict == "warn" } }
    val worst = r.words.filter { it.score < 60 }.map { it.word }
    return when {
        errors == 0 && warns == 0 -> "全部音素都过关，读得很稳。"
        errors == 0 -> "没有明显错误，$warns 处不太稳定，多读两遍就好。"
        worst.isEmpty() -> "$errors 个音素需要注意。"
        else -> "$errors 个音素需要注意，重点看：${worst.take(4).joinToString("、")}。"
    }
}
