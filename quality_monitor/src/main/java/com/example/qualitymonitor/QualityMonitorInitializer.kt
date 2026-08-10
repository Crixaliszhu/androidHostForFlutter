package com.example.qualitymonitor

import android.app.Application
import com.example.qualitymonitor.anr.AnrMonitorInitializer
import com.example.qualitymonitor.anr.config.AnrMonitorConfig
import com.example.qualitymonitor.breadcrumb.QualityBreadcrumbs
import com.example.qualitymonitor.core.QualityEventStore
import com.example.qualitymonitor.core.QualityUploader
import com.example.qualitymonitor.crash.java.JavaCrashCollector
import com.example.qualitymonitor.crash.nativecrash.NativeCrashCollector
import com.example.qualitymonitor.performance.page.ActivityPagePerfMonitor
import com.example.qualitymonitor.performance.page.PagePerf
import com.example.qualitymonitor.performance.startup.StartupMonitor
import java.util.concurrent.Executors

/**
 * 自研质量监控模块入口。
 *
 * 宿主 Application 只需要调用 init，一次性安装崩溃、ANR、启动耗时、页面耗时、
 * 面包屑和本地队列上传逻辑，保持业务层接入成本稳定。
 */
object QualityMonitorInitializer {
    // 上报放到单线程执行，避免启动阶段并发读写本地队列导致文件状态竞争。
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "quality-monitor-uploader")
    }
    private var initialized = false
    private var breadcrumbs: QualityBreadcrumbs? = null

    lateinit var nativeCrashCollector: NativeCrashCollector
        private set

    fun init(application: Application, config: QualityMonitorConfig) {
        // 初始化必须幂等；Application 多进程或测试环境重复调用时不能重复注册生命周期回调。
        if (!config.enabled || initialized) return
        initialized = true

        val breadcrumbs = QualityBreadcrumbs()
        this.breadcrumbs = breadcrumbs
        application.registerActivityLifecycleCallbacks(breadcrumbs)

        val store = QualityEventStore(application, config, breadcrumbs)
        val uploader = QualityUploader(config)
        PagePerf.bindStore(store)

        // 崩溃和性能采集先安装，本地历史队列上传后置，避免上传耗时影响冷启动主路径。
        JavaCrashCollector(store).install()
        nativeCrashCollector = NativeCrashCollector(store).also { it.install() }
        StartupMonitor(application, config, store).install()
        ActivityPagePerfMonitor(application, store, config.activityOnPreDrawList).install()

        if (config.anrEnabled) {
            // ANR 采集已经并入质量监控模块，事件存储和上报都复用统一队列。
            AnrMonitorInitializer.init(
                application = application,
                config = AnrMonitorConfig(
                    enabled = true,
                    watchDogTimeoutMillis = config.anrWatchDogTimeoutMillis,
                    maxTraceChars = config.anrMaxTraceChars,
                ),
                breadcrumbs = breadcrumbs,
                store = store,
                uploadPending = { uploadPending(store, uploader) },
            )
        }

        executor.execute {
            uploadPending(store, uploader)
        }
    }

    fun addBreadcrumb(message: String) {
        // 暴露给业务补充关键动作，例如点击、路由跳转或接口状态，提升崩溃现场可读性。
        breadcrumbs?.add(message)
    }

    fun startPage(pageName: String, route: String = pageName): String {
        // 统一入口转调 PagePerf，业务无需感知页面性能实现类所在子包。
        return PagePerf.start(pageName, route)
    }

    fun markPage(sessionId: String, name: String) {
        // 标记数据 ready、首屏接口返回等业务节点，最终会写入 page_perf 事件。
        PagePerf.mark(sessionId, name)
    }

    fun endPage(sessionId: String, endName: String = "interactive") {
        // 结束页面性能会话；未调用时会话不会落盘，业务需要在合适生命周期补齐。
        PagePerf.end(sessionId, endName)
    }

    fun triggerNativeCrashForTest(): Boolean {
        // Demo 页面通过统一入口触发测试崩溃，避免直接依赖 native crash 子包实现。
        if (!::nativeCrashCollector.isInitialized) return false
        return nativeCrashCollector.triggerNativeCrashForTest()
    }

    private fun uploadPending(store: QualityEventStore, uploader: QualityUploader) {
        store.pendingFiles().forEach { file ->
            // 只有服务端确认成功后才删除，失败文件保留到下次启动继续尝试。
            if (uploader.upload(file)) {
                store.markUploaded(file)
            }
        }
    }
}
