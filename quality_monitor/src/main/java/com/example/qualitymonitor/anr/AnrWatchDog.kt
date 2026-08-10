package com.example.qualitymonitor.anr

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.qualitymonitor.anr.config.AnrMonitorConfig
import com.example.qualitymonitor.anr.entity.AnrEvent
import com.example.qualitymonitor.anr.util.AnrThreadDumper
import com.example.qualitymonitor.breadcrumb.QualityBreadcrumbs
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 通过向主线程 Looper 投递心跳任务来检测主线程卡死。
 */
class AnrWatchDog(
    private val config: AnrMonitorConfig,
    private val breadcrumbs: QualityBreadcrumbs,
    private val processNameProvider: () -> String,
    private val onAnr: (AnrEvent) -> Unit,
) : Thread("self-anr-watchdog") {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tick = AtomicLong(0)
    private var reportedInCurrentFreeze = false

    override fun run() {
        while (!isInterrupted) {
            val lastTick = tick.get()
            // 每 watchDogTimeoutMillis 投递一次任务，在 runnable 里 tick+1。
            mainHandler.post {
                tick.incrementAndGet()
                reportedInCurrentFreeze = false
            }

            try {
                sleep(config.watchDogTimeoutMillis)
            } catch (_: InterruptedException) {
                interrupt()
                return
            }
            // watchDogTimeoutMillis 后 tick 没有增加，说明主线程 Handler 被耗时操作占用。
            if (tick.get() == lastTick && !reportedInCurrentFreeze) {
                // 同一次卡死可能持续多个检测周期，每个卡死窗口只保留一条事件。
                reportedInCurrentFreeze = true
                onAnr(buildWatchDogEvent())
            }
        }
    }

    private fun buildWatchDogEvent(): AnrEvent {
        Log.e("watchdog", "检测到 ANR")
        return AnrEvent(
            id = UUID.randomUUID().toString(),
            type = "watchdog",
            timestampMillis = System.currentTimeMillis(),
            processName = processNameProvider(),
            foreground = breadcrumbs.foreground,
            currentActivity = breadcrumbs.currentActivity,
            lastBreadcrumbs = breadcrumbs.snapshot(),
            mainThreadStack = AnrThreadDumper.mainThreadStack(),
            allThreadStacks = AnrThreadDumper.allThreadStacks(config.maxTraceChars),
            systemTrace = null,
            extra = mapOf("timeoutMillis" to config.watchDogTimeoutMillis.toString()),
        )
    }
}
