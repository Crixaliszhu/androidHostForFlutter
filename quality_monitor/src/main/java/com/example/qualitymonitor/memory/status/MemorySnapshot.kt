package com.example.qualitymonitor.memory.status

import org.json.JSONObject

/**
 * 单次内存状态快照。
 *
 * 只保存线上排查最常用的聚合指标，不保存对象明细，避免内存采样本身引入隐私和体积风险。
 */
internal data class MemorySnapshot(
    /** 触发采样的原因，例如周期采样、onTrimMemory、onLowMemory 或泄露检测现场。 */
    val reason: String,
    /** 采样发生的本地时间戳，用于和崩溃、ANR、页面耗时事件按时间线关联。 */
    val timestampMillis: Long,
    /** Java/Kotlin 堆已使用内存，单位 KB；由 Runtime totalMemory - freeMemory 计算。 */
    val javaHeapUsedKb: Long,
    /** Java/Kotlin 堆最大可用内存，单位 KB；接近该值时更容易触发 Java OOM。 */
    val javaHeapMaxKb: Long,
    /** Java/Kotlin 堆使用率，javaHeapUsedKb / javaHeapMaxKb，用于快速判断堆压力。 */
    val javaHeapRatio: Double,
    /** Native 堆已分配内存，单位 KB；用于观察 C/C++、JNI、Bitmap native 分配等增长趋势。 */
    val nativeHeapAllocatedKb: Long,
    /** Native 堆当前总大小，单位 KB；和 allocated 对比可判断 native 堆保留空间。 */
    val nativeHeapSizeKb: Long,
    /** 当前进程总 PSS，单位 KB；PSS 更接近系统视角下该进程实际分摊的物理内存占用。 */
    val totalPssKb: Int,
    /** Dalvik/ART PSS，单位 KB；主要反映 Java 堆、类元数据等运行时相关内存。 */
    val dalvikPssKb: Int,
    /** Native PSS，单位 KB；主要反映 native heap、JNI 库和 native 运行时相关内存。 */
    val nativePssKb: Int,
    /** 其他分类 PSS，单位 KB；包含系统未归入 dalvik/native 的映射、资源和共享内存等。 */
    val otherPssKb: Int,
    /** 图形相关内存，单位 KB；可能包含 GraphicBuffer、纹理等，低版本或部分设备可能取不到。 */
    val graphicsPssKb: Long?,
    /** 系统当前可用内存，单位 KB；用于判断是 App 自身膨胀还是整机内存紧张。 */
    val systemAvailMemKb: Long,
    /** 系统低内存阈值，单位 KB；可用内存低于附近时系统会更积极回收后台进程。 */
    val systemThresholdKb: Long,
    /** 系统是否已经处于低内存状态；来自 ActivityManager.MemoryInfo.lowMemory。 */
    val systemLowMemory: Boolean,
    /** 当前进程打开的文件描述符数量；持续上涨可能表示文件、Socket 或 Cursor 未关闭。 */
    val fdCount: Int,
    /** 当前进程线程数量；持续上涨可能表示线程池失控、任务泄露或第三方 SDK 异常创建线程。 */
    val threadCount: Int,
) {
    /** 转成统一事件 payload，便于 QualityEventStore 直接落盘。 */
    fun toJson(): JSONObject {
        return JSONObject()
            .put("reason", reason)
            .put("timestampMillis", timestampMillis)
            .put("javaHeapUsedKb", javaHeapUsedKb)
            .put("javaHeapMaxKb", javaHeapMaxKb)
            .put("javaHeapRatio", javaHeapRatio)
            .put("nativeHeapAllocatedKb", nativeHeapAllocatedKb)
            .put("nativeHeapSizeKb", nativeHeapSizeKb)
            .put("totalPssKb", totalPssKb)
            .put("dalvikPssKb", dalvikPssKb)
            .put("nativePssKb", nativePssKb)
            .put("otherPssKb", otherPssKb)
            .put("graphicsPssKb", graphicsPssKb)
            .put("systemAvailMemKb", systemAvailMemKb)
            .put("systemThresholdKb", systemThresholdKb)
            .put("systemLowMemory", systemLowMemory)
            .put("fdCount", fdCount)
            .put("threadCount", threadCount)
    }
}
