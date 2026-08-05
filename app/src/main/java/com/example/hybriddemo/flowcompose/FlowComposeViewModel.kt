package com.example.hybriddemo.flowcompose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FlowComposeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FlowComposeUiState())
    val uiState: StateFlow<FlowComposeUiState> = _uiState.asStateFlow()

    private val _events = Channel<FlowComposeEvent>(Channel.BUFFERED)
    val events: Flow<FlowComposeEvent> = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun increase() {
        _uiState.update { state ->
            state.copy(
                count = state.count + 1,
                latestMessage = "点击了增加按钮",
            )
        }
    }

    fun decrease() {
        _uiState.update { state ->
            state.copy(
                count = state.count - 1,
                latestMessage = "点击了减少按钮",
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, latestMessage = "正在刷新列表") }
            delay(500)
            _uiState.update {
                it.copy(
                    loading = false,
                    latestMessage = "列表刷新完成",
                    jobs = createFakeJobs(it.count),
                )
            }
            _events.send(FlowComposeEvent.Toast("Flow 一次性事件：刷新完成"))
        }
    }

    private fun createFakeJobs(count: Int): List<FlowComposeJobUiState> {
        return listOf(
            FlowComposeJobUiState(
                id = "job-$count-1",
                name = "木工/支模工",
                salary = "330-380元/天",
                city = "成都",
            ),
            FlowComposeJobUiState(
                id = "job-$count-2",
                name = "钢筋工",
                salary = "350-420元/天",
                city = "重庆",
            ),
            FlowComposeJobUiState(
                id = "job-$count-3",
                name = "水电工",
                salary = "300-360元/天",
                city = "西安",
            ),
        )
    }
}
