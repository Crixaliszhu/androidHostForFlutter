package com.example.anrmonitor

import android.app.Application
import android.os.Build
import java.util.concurrent.Executors

/**
 * 独立 ANR 监控模块的公开入口。
 *
 * 宿主 App 负责传入上报地址、功能开关等构建环境值；模块内部负责看门狗检测、
 * Android 11+ 系统 ANR 补报、本地队列和上传重试。
 */
object SelfAnrInitializer {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "self-anr-reporter")
    }

    private var watchDog: AnrWatchDog? = null

    /**
     * 按进程初始化一次 ANR 监控。
     */
    fun init(application: Application, config: SelfAnrConfig) {
        if (!config.enabled || watchDog != null) return

        val breadcrumbs = AnrBreadcrumbs()
        application.registerActivityLifecycleCallbacks(breadcrumbs)

        val store = AnrSnapshotStore(application, config)
        val reporter = AnrHttpReporter(config)
        val processNameProvider = { currentProcessName(application) }

        executor.execute {
            // 系统确认的 ANR 只能在进程重启后读取，所以初始化时优先补报。
            AppExitAnrCollector(application, config, breadcrumbs, processNameProvider).collect { event ->
                store.enqueue(event)
            }
            uploadPending(store, reporter)
        }

        watchDog = AnrWatchDog(config, breadcrumbs, processNameProvider) { event ->
            executor.execute {
                store.enqueue(event)
                uploadPending(store, reporter)
            }
        }.apply { start() }
    }

    private fun uploadPending(store: AnrSnapshotStore, reporter: AnrHttpReporter) {
        store.pendingEvents().forEach { file ->
            if (reporter.upload(file)) {
                store.markUploaded(file)
            }
        }
    }

    private fun currentProcessName(application: Application): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            application.packageName
        }
    }
}
