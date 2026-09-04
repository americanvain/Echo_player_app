package com.echoplayer.app.ui.reader

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echoplayer.app.util.Words

/** 点词后就地展开的小卡片：释义直接显示，操作全是小图标。 */
@Composable
fun WordCard(
    word: String,
    translation: String?,
    phonetic: String? = null,
    loading: Boolean,
    hint: String?,
    inVocab: Boolean,
    onSpeak: () -> Unit,
    onToggleVocab: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val key = Words.normalize(word).ifEmpty { word }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!phonetic.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text("/$phonetic/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onSpeak, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.VolumeUp, "朗读", Modifier.size(19.dp))
            }
            IconButton(onClick = onToggleVocab, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (inVocab) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    if (inVocab) "从生词本移除" else "加入生词本",
                    Modifier.size(19.dp),
                    tint = if (inVocab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Words.dictionaryUrl(word)))) } },
                modifier = Modifier.size(34.dp),
            ) { Icon(Icons.Default.Search, "查词典", Modifier.size(19.dp)) }
            IconButton(onClick = { clipboard.setText(AnnotatedString(key)) }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.ContentCopy, "复制", Modifier.size(17.dp))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Close, "关闭", Modifier.size(18.dp))
            }
        }
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("查词中…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            !translation.isNullOrBlank() -> Text(
                translation,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp, end = 8.dp),
            )
            else -> Text(
                hint ?: "查不到释义",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, end = 8.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}
