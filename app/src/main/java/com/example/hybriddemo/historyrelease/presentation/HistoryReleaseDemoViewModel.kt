package com.example.hybriddemo.historyrelease.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybriddemo.historyrelease.data.FakeHistoryReleaseDemoRepository
import com.example.hybriddemo.historyrelease.data.HistoryReleaseDemoRepository
import com.example.hybriddemo.historyrelease.domain.HistoryReleaseDemoStateFactory
import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

sealed interface HistoryReleaseDemoEvent {
    data class Toast(val message: String) : HistoryReleaseDemoEvent
}

class HistoryReleaseDemoViewModel(
    private val repository: HistoryReleaseDemoRepository = FakeHistoryReleaseDemoRepository(),
) : ViewModel() {

    private val _uiState = MutableLiveData<HistoryReleaseDemoUiState>()
    val uiState: LiveData<HistoryReleaseDemoUiState> = _uiState

    private val _actionLog = MutableLiveData("等待操作")
    val actionLog: LiveData<String> = _actionLog

    private val _events = Channel<HistoryReleaseDemoEvent>(Channel.BUFFERED)
    val events: Flow<HistoryReleaseDemoEvent> = _events.receiveAsFlow()

    init {
        reload()
    }

    fun reload() {
        _uiState.value = HistoryReleaseDemoStateFactory.create(repository.loadHistoryRelease())
        _actionLog.value = "已加载历史招工数据"
        viewModelScope.launch(Dispatchers.IO) {

        }
    }

    fun onCopy() {
        _actionLog.value = "点击了：复制招工"
        sendToastEvent("Channel 一次性事件：复制招工")
    }

    fun onModify() {
        _actionLog.value = "点击了：修改招工"
        sendToastEvent("Channel 一次性事件：修改招工")
    }

    fun onClose() {
        _actionLog.value = "点击了：关闭招工"
        sendToastEvent("Channel 一次性事件：关闭招工")
    }

    fun onFlush() {
        _actionLog.value = "点击了：刷新招工"
        sendToastEvent("Channel 一次性事件：刷新招工")
    }

    fun onTop() {
        _actionLog.value = "点击了：置顶招工"
        sendToastEvent("Channel 一次性事件：置顶招工")
    }

    fun onModifyTop() {
        _actionLog.value = "点击了：修改置顶"
        sendToastEvent("Channel 一次性事件：修改置顶")
    }

    fun onRedo() {
        _actionLog.value = "点击了：重新发布"
        sendToastEvent("Channel 一次性事件：重新发布")
    }

    fun onManageRecruit() {
        _actionLog.value = "点击了：管理招工"
        sendToastEvent("Channel 一次性事件：管理招工")
    }

    private fun sendToastEvent(message: String) {
        _events.trySend(HistoryReleaseDemoEvent.Toast(message))
        Executors.newCachedThreadPool()
        Executors.newWorkStealingPool()
        Executors.newSingleThreadExecutor()
        Executors.newFixedThreadPool(1)
    }
}
