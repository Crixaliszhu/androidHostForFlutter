package com.example.hybriddemo.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

fun <T> Flow<T>.noneNullStateIn(
    scope: CoroutineScope,
    value: T,
    started: SharingStarted = SharingStarted.Lazily
): StateFlow<T> {
    return this.stateIn(scope, started, value)
}

fun <T> Flow<T?>.nullStateIn(
    scope: CoroutineScope,
    value: T? = null,
    started: SharingStarted = SharingStarted.Lazily
): StateFlow<T?> {
    return this.stateIn(scope, started, value)
}

/**
 * 一次性事件
 */
fun <T> signalFlow(replay: Boolean = true) = MutableSharedFlow<T>(
    replay = if (replay) 1 else 0,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)

fun <T> singleEvent() = Channel<T>(Channel.BUFFERED)