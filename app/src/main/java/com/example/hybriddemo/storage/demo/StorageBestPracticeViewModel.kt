package com.example.hybriddemo.storage.demo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybriddemo.storage.datastore.DataStorePreferenceStore
import com.example.hybriddemo.storage.datastore.RecruitPreferenceLds
import com.example.hybriddemo.storage.mmkv.MmkvKvStore
import com.example.hybriddemo.storage.mmkv.RecruitDraftKvKeys
import com.example.hybriddemo.storage.mmkv.RecruitDraftKvLds
import com.example.hybriddemo.storage.room.RecruitHistoryRepository
import com.example.hybriddemo.storage.room.StorageDemoDatabaseProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StorageBestPracticeViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val kvLds = RecruitDraftKvLds(
        MmkvKvStore(RecruitDraftKvKeys.NAMESPACE)
    )
    private val preferenceLds = RecruitPreferenceLds(
        DataStorePreferenceStore(
            context = application,
            storeName = "demo_recruit_preferences",
        )
    )
    private val historyRepository = RecruitHistoryRepository(
        StorageDemoDatabaseProvider.get(application).recruitHistoryDao()
    )

    private val _uiState = MutableStateFlow(StorageDemoUiState())
    val uiState: StateFlow<StorageDemoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferenceLds.observeAutoSaveDraft().collectLatest { enabled ->
                _uiState.update {
                    it.copy(
                        autoSaveDraft = enabled,
                        dataStoreResult = "observe auto_save_draft = $enabled",
                    )
                }
            }
        }
    }

    fun runMmkvDemo() {
        kvLds.saveLastDraft(id = "draft_10086", title = "招聘木工师傅")
        _uiState.update {
            it.copy(mmkvResult = kvLds.readLastDraft())
        }
    }

    fun toggleDataStoreDemo() {
        viewModelScope.launch {
            val current = preferenceLds.readAutoSaveDraft()
            preferenceLds.setAutoSaveDraft(!current)
        }
    }

    fun runRoomDemo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            historyRepository.saveHistory(
                id = "history_10001",
                title = "历史发布：招聘泥瓦工",
                city = "成都",
            )
            historyRepository.saveHistory(
                id = "history_10002",
                title = "历史发布：招聘水电工",
                city = "重庆",
            )
            _uiState.update {
                it.copy(
                    roomResult = historyRepository.readSummary(),
                    isLoading = false,
                )
            }
        }
    }

    fun clearAllDemoData() {
        viewModelScope.launch {
            kvLds.clearLastDraft()
            preferenceLds.resetAutoSaveDraft()
            historyRepository.clear()
            _uiState.update {
                it.copy(
                    mmkvResult = kvLds.readLastDraft(),
                    roomResult = "empty",
                )
            }
        }
    }
}
