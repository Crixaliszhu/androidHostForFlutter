package com.example.hybriddemo.historyrelease.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.hybriddemo.historyrelease.data.FakeHistoryReleaseDemoRepository
import com.example.hybriddemo.historyrelease.data.HistoryReleaseDemoRepository
import com.example.hybriddemo.historyrelease.domain.HistoryReleaseDemoStateFactory
import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoUiState

class HistoryReleaseDemoViewModel(
    private val repository: HistoryReleaseDemoRepository = FakeHistoryReleaseDemoRepository(),
) : ViewModel() {

    private val _uiState = MutableLiveData<HistoryReleaseDemoUiState>()
    val uiState: LiveData<HistoryReleaseDemoUiState> = _uiState

    private val _actionLog = MutableLiveData("等待操作")
    val actionLog: LiveData<String> = _actionLog

    init {
        reload()
    }

    fun reload() {
        _uiState.value = HistoryReleaseDemoStateFactory.create(repository.loadHistoryRelease())
        _actionLog.value = "已加载历史招工数据"
    }

    fun onCopy() {
        _actionLog.value = "点击了：复制招工"
    }

    fun onModify() {
        _actionLog.value = "点击了：修改招工"
    }

    fun onClose() {
        _actionLog.value = "点击了：关闭招工"
    }

    fun onFlush() {
        _actionLog.value = "点击了：刷新招工"
    }

    fun onTop() {
        _actionLog.value = "点击了：置顶招工"
    }

    fun onModifyTop() {
        _actionLog.value = "点击了：修改置顶"
    }

    fun onRedo() {
        _actionLog.value = "点击了：重新发布"
    }

    fun onManageRecruit() {
        _actionLog.value = "点击了：管理招工"
    }
}
