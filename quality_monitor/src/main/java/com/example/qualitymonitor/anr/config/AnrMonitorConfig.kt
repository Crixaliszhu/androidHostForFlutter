package com.example.qualitymonitor.anr.config

/**
 * ANR 采集所需的内部配置。
 *
 * 上报地址和本地队列已经由 QualityMonitorConfig、QualityEventStore 统一管理，
 * 这里只保留 WatchDog 和 trace 截断这类 ANR 专属参数。
 */
data class AnrMonitorConfig(
    /** 是否开启监控 */
    val enabled: Boolean,
    /** 看门狗心跳间隔 */
    val watchDogTimeoutMillis: Long = 5_000L,
    /** 截取的 anr trace最大字符上限 */
    val maxTraceChars: Int = 180_000,
)
