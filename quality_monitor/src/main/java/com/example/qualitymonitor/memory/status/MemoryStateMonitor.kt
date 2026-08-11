package com.example.qualitymonitor.memory.status

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.example.qualitymonitor.QualityMonitorConfig
import com.example.qualitymonitor.core.QualityEventStore
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * 内存状态监控入口。
 *
 * 通过周期采样和系统内存压力回调生成 memory_snapshot / memory_warning 事件，
 * 不做对象级分析，保证可以在 release 中常驻运行。
 */
internal class MemoryStateMonitor(
    application: Application,
    private val config: QualityMonitorConfig,
    private val store: QualityEventStore,
) : ComponentCallbacks2 {
    private val appContext = application.applicationContext
    private val sampler = MemorySampler(appContext)
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "quality-memory-state")
    }
    private val installed = AtomicBoolean(false)
    private var periodicTask: ScheduledFuture<*>? = null

    fun install(application: Application) {
        if (!config.memoryStateEnabled || !installed.compareAndSet(false, true)) return
        application.registerComponentCallbacks(this)
        report("install")
        val interval = config.memorySampleIntervalMillis.coerceAtLeast(MIN_SAMPLE_INTERVAL_MILLIS)
        periodicTask = executor.scheduleWithFixedDelay(
            { report("periodic") },
            interval,
            interval,
            TimeUnit.MILLISECONDS,
        )
    }

    override fun onTrimMemory(level: Int) {
        // onTrimMemory 是系统已经感知到内存压力的强信号，需要立即采样并单独记录 level。
        report("trim_memory", level)
    }

    override fun onLowMemory() {
        report("low_memory")
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    private fun report(reason: String, trimLevel: Int? = null) {
        executor.execute {
            runCatching {
                val snapshot = sampler.sample(reason)
                val payload = snapshot.toJson().apply {
                    if (trimLevel != null) {
                        put("trimMemoryLevel", trimLevel)
                    }
                }
                store.enqueue("memory_snapshot", payload)
                if (shouldWarn(snapshot, trimLevel)) {
                    store.enqueue(
                        "memory_warning",
                        JSONObject()
                            .put("warningReason", warningReason(snapshot, trimLevel))
                            .put("snapshot", snapshot.toJson())
                            .put("trimMemoryLevel", trimLevel)
                    )
                }
            }
        }
    }

    private fun shouldWarn(snapshot: MemorySnapshot, trimLevel: Int?): Boolean {
        if (trimLevel != null && trimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            return true
        }
        if (snapshot.systemLowMemory) return true
        if (snapshot.javaHeapRatio >= config.memoryJavaHeapWarningRatio) return true
        return config.memoryTotalPssWarningKb > 0 && snapshot.totalPssKb >= config.memoryTotalPssWarningKb
    }

    private fun warningReason(snapshot: MemorySnapshot, trimLevel: Int?): String {
        return when {
            trimLevel != null && trimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "trim_memory_level"
            snapshot.systemLowMemory -> "system_low_memory"
            snapshot.javaHeapRatio >= config.memoryJavaHeapWarningRatio -> "java_heap_high"
            else -> "total_pss_high"
        }
    }

    companion object {
        private const val MIN_SAMPLE_INTERVAL_MILLIS = 10_000L
    }
}
