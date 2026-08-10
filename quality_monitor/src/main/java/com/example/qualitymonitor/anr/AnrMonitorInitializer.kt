package com.example.qualitymonitor.anr

import android.app.Application
import com.example.qualitymonitor.anr.config.AnrMonitorConfig
import com.example.qualitymonitor.breadcrumb.QualityBreadcrumbs
import com.example.qualitymonitor.core.QualityEventStore
import com.example.qualitymonitor.util.ProcessName
import java.util.concurrent.Executors

/**
 * quality_monitor 内部的 ANR 采集入口。
 *
 * 负责安装 WatchDog 和 Android 11+ 历史退出原因补报；ANR 事件统一写入
 * QualityEventStore，并复用 QualityUploader 的后台上传链路。
 */
internal object AnrMonitorInitializer {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "quality-monitor-anr")
    }

    private var watchDog: AnrWatchDog? = null

    /**
     * 按进程初始化一次 ANR 监控，重复调用不会创建多个 WatchDog。
     */
    fun init(
        application: Application,
        config: AnrMonitorConfig,
        breadcrumbs: QualityBreadcrumbs,
        store: QualityEventStore,
        uploadPending: () -> Unit,
    ) {
        if (!config.enabled || watchDog != null) return

        val processNameProvider = { ProcessName.current().ifBlank { application.packageName } }

        executor.execute {
            // 系统确认的 ANR 只能在进程重启后读取，所以初始化时优先补报历史退出记录。
            AppExitAnrCollector(
                application,
                config,
                breadcrumbs,
                processNameProvider
            ).collect { event ->
                store.enqueue("anr", event.toJson())
            }
            uploadPending()
        }

        watchDog = AnrWatchDog(config, breadcrumbs, processNameProvider) { event ->
            executor.execute {
                store.enqueue("anr", event.toJson())
                uploadPending()
            }
        }.apply { start() }
    }
}
