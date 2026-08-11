package com.example.qualitymonitor

/**
 * 自研质量监控的运行时配置。
 *
 * 由宿主 App 在启动时注入，统一控制采集开关、上报地址、版本信息和进程启动时间，
 * 避免监控模块直接依赖宿主工程的 BuildConfig 或业务常量。
 */
data class QualityMonitorConfig(
    /** 总开关；关闭后不注册任何崩溃、ANR 或性能采集逻辑。 */
    val enabled: Boolean,
    /** 统一上报地址；为空时事件只落本地队列，便于 Demo 或离线调试。 */
    val uploadUrl: String,
    /** 应用标识，用于服务端按 App 维度聚合质量事件。 */
    val appId: String,
    /** 版本名，用于定位某个发布版本的崩溃和性能波动。 */
    val versionName: String,
    /** 版本号，用于和 versionName 一起形成可排序的发布标识。 */
    val versionCode: Int,
    /** 进程创建时刻，启动耗时需要用它计算进程到首帧的完整冷启动口径。 */
    val processStartUptimeMillis: Long,
    /** 是否启用独立 ANR 模块；保留开关方便单独灰度或排查 WatchDog 自身问题。 */
    val anrEnabled: Boolean = true,
    /** ANR WatchDog 心跳间隔；默认 5 秒，对齐 Android 输入无响应的常见判定窗口。 */
    val anrWatchDogTimeoutMillis: Long = 5_000L,
    /** 系统 ANR trace 和全线程栈截断上限，避免单条事件过大影响本地存储和上传。 */
    val anrMaxTraceChars: Int = 180_000,
    /** 需要进行页面启动耗时统计的页面*/
    val activityOnPreDrawList: List<String>,
    /** 是否启用内存状态监控；关闭后不注册周期采样和系统内存压力回调。 */
    val memoryStateEnabled: Boolean = true,
    /** 前台内存采样间隔；间隔过短会放大 Debug.MemoryInfo 和文件落盘的运行成本。 */
    val memorySampleIntervalMillis: Long = 60_000L,
    /** Java 堆使用率告警阈值；超过后额外写入 memory_warning，便于筛出高压现场。 */
    val memoryJavaHeapWarningRatio: Double = 0.80,
    /** PSS 告警阈值，单位 KB；小于等于 0 表示不按 PSS 触发告警。 */
    val memoryTotalPssWarningKb: Int = 0,
    /** 是否启用 Activity 泄露监控；第一阶段只观察 Activity，避免 Fragment 接入侵入业务。 */
    val memoryLeakEnabled: Boolean = true,
    /** Activity 销毁后首次检查延迟；太短容易把系统延迟回收误判为泄露。 */
    val leakCheckDelayMillis: Long = 5_000L,
    /** 疑似泄露后的确认延迟；二次检查仍未回收才会上报 confirmed 事件。 */
    val leakConfirmDelayMillis: Long = 30_000L,
    /** 疑似泄露数量达到该阈值后才允许 HPROF，可降低单个误判触发重型 dump 的概率。 */
    val leakHprofRetainedThreshold: Int = 5,
    /** 是否允许泄露确认后 dump HPROF；线上默认建议关闭，通过灰度或本地导出取证。 */
    val leakHprofDumpEnabled: Boolean = false,
)
