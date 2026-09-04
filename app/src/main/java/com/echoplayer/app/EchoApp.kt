package com.echoplayer.app

import android.app.Application
import com.echoplayer.app.audio.ClipPlayer
import com.echoplayer.app.audio.TtsEngine
import com.echoplayer.app.audio.WavRecorder
import com.echoplayer.app.data.SettingsStore
import com.echoplayer.app.data.db.EchoDatabase
import com.echoplayer.app.data.remote.EchoServerApi
import com.echoplayer.app.data.repo.IssueRepository
import com.echoplayer.app.data.repo.MaterialRepository
import com.echoplayer.app.data.repo.PracticeRepository
import com.echoplayer.app.data.repo.VocabRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 手写的依赖容器：项目规模不需要 DI 框架。 */
class EchoApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsStore(this) }
    val db by lazy { EchoDatabase.build(this) }
    val api by lazy { EchoServerApi { settings.cachedServerUrl } }
    val materials by lazy { MaterialRepository(this, db.materialDao(), api) }
    val practice by lazy { PracticeRepository(db.practiceDao(), api) }
    val issues by lazy { IssueRepository(db.issueDao(), api) }
    val vocab by lazy { VocabRepository(db.vocabDao()) }

    val tts by lazy { TtsEngine(this) }
    val recorder by lazy { WavRecorder() }
    val clipPlayer by lazy { ClipPlayer() }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            settings.warm()
            materials.seedBundled()
        }
    }
}
