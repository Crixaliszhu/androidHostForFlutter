package com.example.hybriddemo.performance

import android.app.Activity
import android.os.Handler
import android.view.View

/**
 * Demo-only static holder used to create an obvious Activity leak for Memory Profiler.
 */
object MemoryLeakRegistry {
    val leakedActivities = mutableListOf<Activity>()
    val leakedClickListeners = mutableListOf<View.OnClickListener>()
    private val delayedCallbacks = mutableListOf<Pair<Handler, Runnable>>()

    fun leak(
        activity: Activity,
        listener: View.OnClickListener,
        handler: Handler,
        delayedTask: Runnable,
    ) {
        leakedActivities += activity
        leakedClickListeners += listener
        delayedCallbacks += handler to delayedTask
    }

    fun clear() {
        delayedCallbacks.forEach { (handler, runnable) ->
            handler.removeCallbacks(runnable)
        }
        delayedCallbacks.clear()
        leakedActivities.clear()
        leakedClickListeners.clear()
    }
}
