package com.example.hybriddemo.historyrelease.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hybriddemo.historyrelease.data.FakeHistoryReleaseDemoRepository
import com.example.hybriddemo.historyrelease.data.HistoryReleaseDemoRepository
import com.example.hybriddemo.historyrelease.domain.HistoryReleaseDemoStateFactory
import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoUiState
import com.example.hybriddemo.historyrelease.model.HistorySecondUS
import com.example.hybriddemo.historyrelease.model.HistorySecondUiState
import com.example.hybriddemo.util.noneNullStateIn
import com.example.hybriddemo.util.nullStateIn
import com.example.hybriddemo.util.signalFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
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

    var secondIndex = 0

    private val _secondSign = signalFlow<Int>()
    val secondUiState1 = _secondSign.flatMapLatest {
        repository.loadSecondHistory(it).map { item ->
            HistorySecondUiState(title = item.title, subTitle = item.subTitle)
        }.onEach {
            println("HistoryReleaseDemoViewModel-每次成功打印")
            secondUiState2.value = it
        }
    }.nullStateIn(viewModelScope)

    private val secondUiState2: MutableStateFlow<HistorySecondUiState?> = MutableStateFlow(null)

    val us = HistorySecondUS(
        data = secondUiState2,
        scope = viewModelScope
    )

    init {
        secondUiState1.launchIn(viewModelScope)
        println("HistoryReleaseDemoViewModel-init")
        reload()
    }

    fun secondFetch() {
        println("HistoryReleaseDemoViewModel-secondFetch")
        _secondSign.tryEmit(secondIndex++)
    }

    fun reload() {
        _uiState.value = HistoryReleaseDemoStateFactory.create(repository.loadHistoryRelease())
        _actionLog.value = "已加载历史招工数据"
    }

    fun onCopy() {
        _actionLog.value = "点击了：复制招工"
        sendToastEvent("Channel 一次性事件：复制招工")
        secondFetch()
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
    }
}
