package com.echoplayer.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echoplayer.app.BuildConfig
import com.echoplayer.app.audio.TtsEngine
import com.echoplayer.app.data.model.ProblemLayer
import com.echoplayer.app.ui.common.echoViewModel
import com.echoplayer.app.ui.theme.EchoColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm = echoViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val tts by vm.ttsState.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var url by remember { mutableStateOf(s.serverUrl) }
    LaunchedEffect(s.serverUrl) { if (url.isEmpty() && s.serverUrl.isNotEmpty()) url = s.serverUrl }
    var rate by remember(s.ttsRate) { mutableStateOf(s.ttsRate) }

    Scaffold(topBar = { TopAppBar(title = { Text("设置", fontWeight = FontWeight.Bold) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("服务器")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("评测 / 处理服务器地址") },
                    placeholder = { Text("http://192.168.1.10:8000") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "填 speecheval 服务的地址。跟读评分（/assess）、PDF 处理、在线翻译、AI 讲解都走这里；不填也能听读、盲听、记录问题和生词。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.setServerUrl(url) }, enabled = url.trim() != s.serverUrl) { Text("保存") }
                    OutlinedButton(onClick = { if (url.trim() != s.serverUrl) vm.setServerUrl(url); vm.testConnection() }, enabled = !testing && url.isNotBlank()) {
                        Text(if (testing) "测试中…" else "测试连接")
                    }
                }
                health?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (it.startsWith("连接成功")) EchoColors.Green else MaterialTheme.colorScheme.error) }
            }

            Section("朗读")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("语速", modifier = Modifier.width(48.dp))
                    Slider(value = rate, onValueChange = { rate = it }, onValueChangeFinished = { vm.setTtsRate(rate) }, valueRange = 0.5f..1.5f, steps = 9, modifier = Modifier.weight(1f))
                    Text("${"%.1f".format(rate)}x", modifier = Modifier.width(44.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (tts) {
                            TtsEngine.State.READY -> "本机语音引擎：可用（英语）"
                            TtsEngine.State.INIT -> "本机语音引擎：初始化中"
                            TtsEngine.State.NO_ENGINE -> "本机语音引擎：没有找到，请安装系统 TTS"
                            TtsEngine.State.NO_LANGUAGE -> "本机语音引擎：缺少英语语音包"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { vm.testTts() }, enabled = tts == TtsEngine.State.READY) { Text("试听") }
                }
                if (tts != TtsEngine.State.READY && tts != TtsEngine.State.INIT) {
                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) } }) { Text("打开系统 TTS 设置") }
                }
                Text("服务器合成的句子语音（Echo_player 流水线）下载后会优先于本机引擎播放。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section("听读默认")
            SwitchRow("显示翻译", "每句下方显示中文", s.showTranslation) { vm.setShowTranslation(it) }
            SwitchRow("盲听模式", "先听，点一下才显示原文", s.blindMode) { vm.setBlindMode(it) }
            SwitchRow("自动连播", "一句读完自动播放下一句", s.autoAdvance) { vm.setAutoAdvance(it) }
            SwitchRow("显示音素细节", "评分后点单词展开逐音素判定", s.showPhonemes) { vm.setShowPhonemes(it) }

            Section("五层问题定位")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("听不懂的时候，问题一定出在下面五层之一。定位到具体的层，才能做针对性的练习，而不是笼统地“多听”。", style = MaterialTheme.typography.bodyMedium)
                ProblemLayer.entries.forEach { l ->
                    Row {
                        Text("${l.id}", color = EchoColors.layer(l.id), fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                        Column {
                            Text(l.title, style = MaterialTheme.typography.labelLarge, color = EchoColors.layer(l.id))
                            Text(l.symptoms.first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Section("关于")
            ListItem(
                headlineContent = { Text("Echo Player ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text("AI 语言学习助手：听读 → 跟读评分 → 问题定位 → 记录复习。发音评测由 speecheval 服务提供。") },
            )
            OutlinedButton(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/americanvain/Echo_player"))) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text("项目主页") }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
