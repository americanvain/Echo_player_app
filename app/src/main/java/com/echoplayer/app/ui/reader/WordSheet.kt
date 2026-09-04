package com.echoplayer.app.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.echoplayer.app.data.remote.WordResult
import com.echoplayer.app.ui.theme.EchoColors
import androidx.compose.material.icons.filled.Hearing
import com.echoplayer.app.util.Words

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(
    word: String,
    inVocab: Boolean,
    onSpeak: () -> Unit,
    onToggleVocab: () -> Unit,
    onDismiss: () -> Unit,
    scored: WordResult? = null,
    onPlayMine: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val key = Words.normalize(word)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(key.ifEmpty { word }, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            if (scored != null) {
                Text(
                    "跟读得分 ${scored.score}" + scored.phones.filter { it.verdict == "error" }.takeIf { it.isNotEmpty() }
                        ?.let { errs -> " · 注意 " + errs.joinToString(" ") { it.canonical } }.orEmpty(),
                    color = EchoColors.score(scored.score),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            ListItem(
                headlineContent = { Text("听标准读法") },
                leadingContent = { Icon(Icons.Default.VolumeUp, null) },
                modifier = Modifier.clickable { onSpeak() },
            )
            if (scored != null && onPlayMine != null) {
                ListItem(
                    headlineContent = { Text("听我读的这个词") },
                    leadingContent = { Icon(Icons.Default.Hearing, null) },
                    modifier = Modifier.clickable { onPlayMine() },
                )
            }
            ListItem(
                headlineContent = { Text(if (inVocab) "从生词本移除" else "加入生词本") },
                supportingContent = { if (!inVocab) Text("会带上这句话作为例句") },
                leadingContent = { Icon(if (inVocab) Icons.Default.BookmarkRemove else Icons.Default.BookmarkAdd, null) },
                modifier = Modifier.clickable { onToggleVocab(); onDismiss() },
            )
            ListItem(
                headlineContent = { Text("查词典") },
                supportingContent = { Text("在浏览器里打开有道词典") },
                leadingContent = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.clickable {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Words.dictionaryUrl(word)))) }
                    onDismiss()
                },
            )
            ListItem(
                headlineContent = { Text("复制") },
                leadingContent = { Icon(Icons.Default.ContentCopy, null) },
                modifier = Modifier.clickable { clipboard.setText(AnnotatedString(key.ifEmpty { word })); onDismiss() },
            )
        }
    }
}
