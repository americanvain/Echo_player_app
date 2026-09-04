package com.echoplayer.app.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echoplayer.app.IncomingFile
import com.echoplayer.app.data.db.MaterialWithProgress
import com.echoplayer.app.data.model.MaterialStatus
import com.echoplayer.app.data.model.SourceType
import com.echoplayer.app.ui.common.EmptyState
import com.echoplayer.app.ui.common.Tag
import com.echoplayer.app.ui.common.echoViewModel
import com.echoplayer.app.ui.common.relativeTime
import com.echoplayer.app.ui.theme.EchoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    incoming: IncomingFile?,
    onIncomingConsumed: () -> Unit,
    onOpen: (materialId: String, unitIndex: Int?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = echoViewModel { LibraryViewModel(it) }
    val library by vm.library.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showImport by remember { mutableStateOf(false) }
    var pasteMode by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MaterialWithProgress?>(null) }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importFile(uri, null)
    }

    LaunchedEffect(Unit) { vm.pollProcessing() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    LaunchedEffect(incoming?.nonce) {
        val inc = incoming ?: return@LaunchedEffect
        when {
            inc.uri != null -> vm.importFile(inc.uri, inc.mime)
            !inc.text.isNullOrBlank() -> vm.importText(inc.text, inc.text.lineSequence().firstOrNull()?.take(40) ?: "分享的文本")
        }
        onIncomingConsumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书架", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { if (settings.serverUrl.isBlank()) onOpenSettings() else vm.syncArticles() }) {
                        Icon(Icons.Default.CloudSync, contentDescription = "同步服务器题库")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showImport = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("导入") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (library.isEmpty()) {
                EmptyState(Icons.Default.MenuBook, "书架还是空的", "点右下角「导入」加入 TXT 或 PDF，或者等内置资源加载完成")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(library, key = { it.id }) { m ->
                        MaterialCard(
                            m = m,
                            onOpen = { if (m.status == MaterialStatus.READY.id && m.unitCount > 0) onOpen(m.id, null) },
                            onDelete = { pendingDelete = m },
                            onRefresh = { vm.refresh(m.id) },
                        )
                    }
                }
            }
            busy?.let {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                        .clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showImport) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showImport = false; pasteMode = false }, sheetState = sheetState) {
            if (!pasteMode) {
                Column(Modifier.padding(bottom = 24.dp)) {
                    Text("导入学习素材", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    ListItem(
                        headlineContent = { Text("TXT 文本") },
                        supportingContent = { Text("在本机按段落切句，立即可用") },
                        leadingContent = { Icon(Icons.Default.Description, null) },
                        modifier = Modifier.combinedClickable(onClick = { showImport = false; openDoc.launch(arrayOf("text/plain", "text/*")) }),
                    )
                    ListItem(
                        headlineContent = { Text("PDF 文档") },
                        supportingContent = {
                            Text(if (settings.serverUrl.isBlank()) "需要先在设置里配置服务器：由服务器识别、切句并合成语音" else "上传到服务器：识别 → 切句 → 合成语音，完成后自动出现在书架")
                        },
                        leadingContent = { Icon(Icons.Default.PictureAsPdf, null) },
                        modifier = Modifier.combinedClickable(onClick = {
                            showImport = false
                            if (settings.serverUrl.isBlank()) onOpenSettings() else openDoc.launch(arrayOf("application/pdf"))
                        }),
                    )
                    ListItem(
                        headlineContent = { Text("粘贴一段文字") },
                        supportingContent = { Text("从别处复制的英文，直接贴进来练") },
                        leadingContent = { Icon(Icons.Outlined.ContentPaste, null) },
                        modifier = Modifier.combinedClickable(onClick = { pasteMode = true }),
                    )
                }
            } else {
                var title by remember { mutableStateOf("") }
                var body by remember { mutableStateOf("") }
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("粘贴文字", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = body, onValueChange = { body = it }, label = { Text("英文内容") },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { pasteMode = false }) { Text("返回") }
                        Button(
                            enabled = body.isNotBlank(),
                            onClick = {
                                vm.importText(body, title.ifBlank { body.trim().take(30) })
                                showImport = false; pasteMode = false
                            },
                        ) { Text("导入") }
                    }
                }
            }
        }
    }

    pendingDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除《${m.title}》？") },
            text = { Text("这本素材的练习记录、问题记录也会一起删除。生词本不受影响。") },
            confirmButton = { TextButton(onClick = { vm.delete(m.id); pendingDelete = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaterialCard(m: MaterialWithProgress, onOpen: () -> Unit, onDelete: () -> Unit, onRefresh: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val status = MaterialStatus.fromId(m.status)
    val source = SourceType.fromId(m.sourceType)
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    m.titleZh?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    when (status) {
                        MaterialStatus.PROCESSING -> IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "刷新状态", tint = MaterialTheme.colorScheme.primary) }
                        MaterialStatus.FAILED -> Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                        MaterialStatus.READY -> {}
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (m.remoteId() ) {
                            DropdownMenuItem(text = { Text("刷新服务器状态") }, onClick = { menu = false; onRefresh() })
                        }
                        DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                m.level?.takeIf { it.isNotBlank() }?.let { Tag(it) }
                m.topic?.takeIf { it.isNotBlank() }?.let { Tag(it, color = MaterialTheme.colorScheme.secondary) }
                Tag(source.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.issueCount > 0) Tag("${m.issueCount} 个待解决", color = EchoColors.Amber)
            }
            m.descriptionZh?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            when (status) {
                MaterialStatus.READY -> {
                    val progress = if (m.unitCount > 0) (m.lastUnitIndex + if (m.lastOpenedAt != null) 1 else 0).toFloat() / m.unitCount else 0f
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (m.lastOpenedAt == null) "${m.unitCount} 句 · 还没开始" else "第 ${m.lastUnitIndex + 1}/${m.unitCount} 句 · 跟读过 ${m.practicedUnits} 句",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        m.lastOpenedAt?.let { Text(relativeTime(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                MaterialStatus.PROCESSING -> Text(m.statusMessage ?: "处理中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                MaterialStatus.FAILED -> Text(m.statusMessage ?: "失败", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun MaterialWithProgress.remoteId(): Boolean = sourceType == SourceType.PDF.id
