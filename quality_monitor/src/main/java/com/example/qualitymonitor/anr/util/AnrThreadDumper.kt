package com.example.qualitymonitor.anr.util

import android.os.Looper

/**
 * 将线程栈转换为适合 ANR 分析的紧凑文本。
 */
object AnrThreadDumper {
    fun mainThreadStack(): String {
        return Looper.getMainLooper().thread.stackTraceToString()
    }

    fun allThreadStacks(maxChars: Int): String {
        val builder = StringBuilder()
        Thread.getAllStackTraces().forEach { (thread, stack) ->
            if (builder.length >= maxChars) return@forEach
            builder.append('"').append(thread.name).append('"')
                .append(" tid=").append(thread.id)
                .append(" state=").append(thread.state)
                .append('\n')
            stack.forEach { builder.append("    at ").append(it).append('\n') }
            builder.append('\n')
        }
        return builder.toString().take(maxChars)
    }

    private fun Thread.stackTraceToString(): String {
        return stackTrace.joinToString(separator = "\n") { "    at $it" }
    }
}
