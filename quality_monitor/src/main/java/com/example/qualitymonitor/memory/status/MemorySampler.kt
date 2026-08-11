package com.example.qualitymonitor.memory.status

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import java.io.File

/**
 * 内存状态采样器。
 *
 * 采集 Java 堆、Native 堆、PSS、系统低内存状态、FD 和线程数量，为崩溃、ANR、
 * 卡顿前后的内存压力分析提供轻量上下文。
 */
internal class MemorySampler(context: Context) {
    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)

    fun sample(reason: String): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val javaUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_KB
        val javaMaxKb = runtime.maxMemory() / BYTES_PER_KB
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)

        val systemInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(systemInfo)

        return MemorySnapshot(
            reason = reason,
            timestampMillis = System.currentTimeMillis(),
            javaHeapUsedKb = javaUsedKb,
            javaHeapMaxKb = javaMaxKb,
            javaHeapRatio = if (javaMaxKb > 0) javaUsedKb.toDouble() / javaMaxKb else 0.0,
            nativeHeapAllocatedKb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_KB,
            nativeHeapSizeKb = Debug.getNativeHeapSize() / BYTES_PER_KB,
            totalPssKb = memoryInfo.totalPss,
            dalvikPssKb = memoryInfo.dalvikPss,
            nativePssKb = memoryInfo.nativePss,
            otherPssKb = memoryInfo.otherPss,
            graphicsPssKb = memoryInfo.getMemoryStat("summary.graphics")?.toLongOrNull(),
            systemAvailMemKb = systemInfo.availMem / BYTES_PER_KB,
            systemThresholdKb = systemInfo.threshold / BYTES_PER_KB,
            systemLowMemory = systemInfo.lowMemory,
            fdCount = File("/proc/self/fd").list()?.size ?: -1,
            threadCount = Thread.getAllStackTraces().size,
        )
    }

    companion object {
        private const val BYTES_PER_KB = 1024L
    }
}
