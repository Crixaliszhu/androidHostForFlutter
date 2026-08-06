package com.example.anrmonitor

/**
 * 宿主 App 传入独立 ANR 监控模块所需的环境配置。
 */
data class SelfAnrConfig(
    val enabled: Boolean,
    val reportUrl: String,
    val watchDogTimeoutMillis: Long = 5_000L,
    val maxPendingEvents: Int = 20,
    val maxTraceChars: Int = 180_000,
)
