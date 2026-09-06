package com.example.hybriddemo.storage.demo

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybriddemo.storage.datastore.RecruitPreferenceLds
import com.example.hybriddemo.storage.mmkv.RecruitDraftKvLds
import com.example.hybriddemo.storage.room.RecruitHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 存储示例的页面状态，由 Hilt 提供存储依赖以避免页面自行组装对象。 */
@HiltViewModel
class StorageBestPracticeViewModel @Inject constructor(
    private val kvLds: RecruitDraftKvLds,
    private val preferenceLds: RecruitPreferenceLds,
    private val historyRepository: RecruitHistoryRepository,
) : ViewModel() {

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
