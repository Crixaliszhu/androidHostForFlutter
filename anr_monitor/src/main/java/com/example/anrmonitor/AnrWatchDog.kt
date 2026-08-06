package com.example.anrmonitor

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 通过向主线程 Looper 投递心跳任务来检测主线程卡死。
 */
class AnrWatchDog(
    private val config: SelfAnrConfig,
    private val breadcrumbs: AnrBreadcrumbs,
    private val processNameProvider: () -> String,
    private val onAnr: (AnrEvent) -> Unit,
) : Thread("self-anr-watchdog") {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tick = AtomicLong(0)
    private var reportedInCurrentFreeze = false

    override fun run() {
        while (!isInterrupted) {
            val lastTick = tick.get()
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

            if (tick.get() == lastTick && !reportedInCurrentFreeze) {
                // 同一次卡死可能持续多个检测周期，每个卡死窗口只保留一条事件。
                reportedInCurrentFreeze = true
                onAnr(buildWatchDogEvent())
            }
        }
    }

    private fun buildWatchDogEvent(): AnrEvent {
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
