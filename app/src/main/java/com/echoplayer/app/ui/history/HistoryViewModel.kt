package com.echoplayer.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoplayer.app.EchoApp
import com.echoplayer.app.data.db.IssueEntity
import com.echoplayer.app.data.db.LayerCount
import com.echoplayer.app.data.db.PracticeRecordEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val app: EchoApp) : ViewModel() {
    val issues: StateFlow<List<IssueEntity>> = app.issues.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val layerCounts: StateFlow<List<LayerCount>> = app.issues.countsByLayer.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val practices: StateFlow<List<PracticeRecordEntity>> = app.practice.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val practiceCount: StateFlow<Int> = app.practice.count.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val averageAccuracy: StateFlow<Double?> = app.practice.averageAccuracy.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setResolved(issue: IssueEntity, resolved: Boolean) = viewModelScope.launch { app.issues.setResolved(issue.id, resolved) }
    fun deleteIssue(issue: IssueEntity) = viewModelScope.launch { app.issues.delete(issue.id) }
    fun deletePractice(record: PracticeRecordEntity) = viewModelScope.launch { app.practice.delete(record) }

    fun playRecording(record: PracticeRecordEntity) {
        val path = record.recordingPath ?: return
        val f = java.io.File(path)
        if (f.exists()) app.clipPlayer.playFile(f)
    }
}
