package com.example.qualitymonitor.memory.leak

import com.example.qualitymonitor.QualityMonitorConfig
import com.example.qualitymonitor.core.QualityEventStore
import com.example.qualitymonitor.memory.status.MemorySampler
import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * 弱引用对象观察器。
 *
 * 对象进入观察队列后只保留 WeakReference；如果延迟 GC 后仍未进入 ReferenceQueue，
 * 说明它仍被某条强引用链持有，需要作为疑似泄露事件上报。
 */
internal class ObjectWatcher(
    private val config: QualityMonitorConfig,
    private val store: QualityEventStore,
    private val sampler: MemorySampler,
    private val hprofDumper: HprofDumper,
) {
    // 弱引用引用的对象被回收时，会被放入ReferenceQueue
    private val referenceQueue = ReferenceQueue<Any>()
    private val watchedReferences = ConcurrentHashMap<String, WatchedReference>()
    // 提供延迟执行，周期执行的能力
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "quality-leak-watcher")
    }

    fun watch(watchedObject: Any, description: String) {
        removeWeaklyReachableReferences()
        val key = UUID.randomUUID().toString()
        watchedReferences[key] = WatchedReference(
            watchedObject = watchedObject,
            referenceQueue = referenceQueue,
            key = key,
            className = watchedObject.javaClass.name,
            description = description,
            watchTimestampMillis = System.currentTimeMillis(),
        )
        executor.schedule(
            { checkRetained(key, confirmed = false) },
            config.leakCheckDelayMillis.coerceAtLeast(MIN_CHECK_DELAY_MILLIS),
            TimeUnit.MILLISECONDS,
        )
    }

    private fun checkRetained(key: String, confirmed: Boolean) {
        removeWeaklyReachableReferences()
        val watched = watchedReferences[key] ?: return
        forceGc()
        removeWeaklyReachableReferences()
        if (watched.weakReference.get() == null) {
            watchedReferences.remove(key)
            return
        }

        if (confirmed) {
            reportConfirmed(watched)
            return
        }

        if (!watched.suspectReported) {
            watched.suspectReported = true
            store.enqueue("memory_leak_suspect", buildPayload(watched, "suspect", null))
        }
        executor.schedule(
            { checkRetained(key, confirmed = true) },
            config.leakConfirmDelayMillis.coerceAtLeast(MIN_CONFIRM_DELAY_MILLIS),
            TimeUnit.MILLISECONDS,
        )
    }

    private fun reportConfirmed(watched: WatchedReference) {
        if (watched.confirmedReported) return
        watched.confirmedReported = true
        val hprofFile = if (shouldDumpHprof()) {
            hprofDumper.dump("leak_${watched.className.substringAfterLast('.')}")
        } else {
            null
        }
        store.enqueue("memory_leak_confirmed", buildPayload(watched, "confirmed", hprofFile?.absolutePath))
    }

    private fun buildPayload(watched: WatchedReference, stage: String, hprofPath: String?): JSONObject {
        return JSONObject()
            .put("stage", stage)
            .put("key", watched.key)
            .put("className", watched.className)
            .put("description", watched.description)
            .put("watchTimestampMillis", watched.watchTimestampMillis)
            .put("retainedMillis", System.currentTimeMillis() - watched.watchTimestampMillis)
            .put("retainedCount", retainedCount())
            .put("hprofPath", hprofPath)
            .put("memorySnapshot", sampler.sample("leak_$stage").toJson())
    }

    private fun retainedCount(): Int {
        removeWeaklyReachableReferences()
        return watchedReferences.values.count { it.weakReference.get() != null }
    }

    /**
     * 开启dump && 内存泄露次数超过阈值则dump hprof
     */
    private fun shouldDumpHprof(): Boolean {
        return config.leakHprofDumpEnabled && retainedCount() >= config.leakHprofRetainedThreshold
    }

    private fun removeWeaklyReachableReferences() {
        while (true) {
            val reference: Reference<out Any> = referenceQueue.poll() ?: return
            watchedReferences.entries.removeIf { it.value.weakReference === reference }
        }
    }

    private fun forceGc() {
        // GC 不能保证立刻回收，但连续触发 GC 与 finalization 可以显著降低误报概率。
        Runtime.getRuntime().gc()
        System.runFinalization()
        Runtime.getRuntime().gc()
    }

    companion object {
        private const val MIN_CHECK_DELAY_MILLIS = 1_000L
        private const val MIN_CONFIRM_DELAY_MILLIS = 5_000L
    }
}
