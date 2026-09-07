package com.example.widget.titlebar.ktx

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 信号触发流
 */
fun <T> signalFlow(isReplay: Boolean = true) = MutableSharedFlow<T>(
    replay = if (isReplay) 1 else 0,
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)