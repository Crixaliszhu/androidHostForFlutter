package com.example.hybriddemo.flowcompose

data class FlowComposeUiState(
    val title: String = "ViewModel + Flow + Compose",
    val count: Int = 0,
    val loading: Boolean = false,
    val latestMessage: String = "等待操作",
    val jobs: List<FlowComposeJobUiState> = emptyList(),
) {
    val countText: String = "当前计数：$count"
}

data class FlowComposeJobUiState(
    val id: String,
    val name: String,
    val salary: String,
    val city: String,
)

sealed interface FlowComposeEvent {
    data class Toast(val message: String) : FlowComposeEvent
}
