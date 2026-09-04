package com.echoplayer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.echoplayer.app.ui.navigation.EchoNavHost
import com.echoplayer.app.ui.theme.EchoTheme

/** 外部分享 / 打开的文件，交给书架页导入。 */
data class IncomingFile(val uri: Uri?, val text: String?, val mime: String?, val nonce: Long = System.nanoTime())

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<IncomingFile?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            EchoTheme {
                EchoNavHost(incoming = incoming, onIncomingConsumed = { incoming = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { incoming = IncomingFile(it, null, intent.type) }
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (uri != null || !text.isNullOrBlank()) incoming = IncomingFile(uri, text, intent.type)
            }
        }
    }
}
