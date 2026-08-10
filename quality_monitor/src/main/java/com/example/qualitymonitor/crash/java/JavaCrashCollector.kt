package com.example.qualitymonitor.crash.java

import com.example.qualitymonitor.core.QualityEventStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Java/Kotlin 崩溃采集器。
 *
 * 通过默认 UncaughtExceptionHandler 在进程崩溃前保存异常、因果链和线程快照；
 * 保存完成后必须继续交给原始 handler，避免吞掉系统崩溃流程或影响调试器表现。
 */
internal class JavaCrashCollector(
    private val store: QualityEventStore,
) {
    private var originHandler: Thread.UncaughtExceptionHandler? = null

    fun install() {
        originHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 崩溃现场只做同步落盘，复杂上报放到下次启动，避免二次崩溃影响系统崩溃流程。
            runCatching {
                store.enqueue("java_crash", buildPayload(thread, throwable))
            }
            originHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildPayload(thread: Thread, throwable: Throwable): JSONObject {
        return JSONObject()
            .put("threadName", thread.name)
            .put("exceptionClass", throwable.javaClass.name)
            .put("message", throwable.message)
            .put("stacktrace", throwable.stackTraceToString())
            .put("causeChain", JSONArray(causeChain(throwable)))
            .put("allThreadStacks", allThreadStacks())
    }

    private fun causeChain(throwable: Throwable): List<String> {
        val result = mutableListOf<String>()
        var current: Throwable? = throwable
        while (current != null && result.size < MAX_CAUSE_DEPTH) {
            result += "${current.javaClass.name}:${current.message.orEmpty()}"
            current = current.cause
        }
        return result
    }

    private fun allThreadStacks(): String {
        // Java 崩溃通常只给崩溃线程；额外保存全线程栈可辅助判断死锁、线程池阻塞等连带原因。
        return Thread.getAllStackTraces()
            .entries
            .joinToString(separator = "\n\n") { (thread, stack) ->
                buildString {
                    append('"').append(thread.name).append('"')
                        .append(" tid=").append(thread.id)
                        .append(" state=").append(thread.state)
                        .append('\n')
                    stack.forEach { append("    at ").append(it).append('\n') }
                }
            }
    }

    companion object {
        private const val MAX_CAUSE_DEPTH = 8
    }
}
