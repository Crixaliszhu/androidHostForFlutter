package com.example.anrmonitor.config

/**
 * 宿主 App 传入独立 ANR 监控模块所需的环境配置。
 */
data class SelfAnrConfig(
    /** 是否开启监控 */
    val enabled: Boolean,
    /** anr日志上报地址 */
    val reportUrl: String,
    /** 看门狗心跳间隔 */
    val watchDogTimeoutMillis: Long = 5_000L,
    /** anr 日志上传缓存队列上限 */
    val maxPendingEvents: Int = 20,
    /** 截取的 anr trace最大字符上限 */
    val maxTraceChars: Int = 180_000,
)
