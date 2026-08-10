package com.example.qualitymonitor.anr

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.qualitymonitor.anr.config.AnrMonitorConfig
import com.example.qualitymonitor.anr.entity.AnrEvent
import com.example.qualitymonitor.breadcrumb.QualityBreadcrumbs
import java.util.UUID

/**
 * 回放 Android 11+ 的进程退出记录，用于补报系统确认过的 ANR。
 */
class AppExitAnrCollector(
    private val application: Application,
    private val config: AnrMonitorConfig,
    private val breadcrumbs: QualityBreadcrumbs,
    private val processNameProvider: () -> String,
) {
    private val preferences =
        application.getSharedPreferences("self_anr_exit_info", Context.MODE_PRIVATE)

    fun collect(onAnr: (AnrEvent) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activityManager = application.getSystemService(ActivityManager::class.java) ?: return
        activityManager.getHistoricalProcessExitReasons(application.packageName, 0, 10)
            .asSequence()
            .filter { it.reason == ApplicationExitInfo.REASON_ANR }
            // 历史退出记录会跨启动保留，入队前先持久化去重状态。
            .filterNot { preferences.getBoolean(it.dedupeKey(), false) }
            .forEach { info ->
                preferences.edit().putBoolean(info.dedupeKey(), true).apply()
                onAnr(info.toAnrEvent())
            }
    }

    private fun ApplicationExitInfo.toAnrEvent(): AnrEvent {
        Log.e("watchdog", "上传 系统ANR日志：system_exit_info")
        return AnrEvent(
            id = UUID.randomUUID().toString(),
            type = "system_exit_info",
            timestampMillis = timestamp,
            processName = processName.ifBlank { processNameProvider() },
            foreground = breadcrumbs.foreground,
            currentActivity = breadcrumbs.currentActivity,
            lastBreadcrumbs = breadcrumbs.snapshot(),
            mainThreadStack = "",
            allThreadStacks = null,
            systemTrace = readTrace(),
            extra = mapOf(
                "pid" to pid.toString(),
                "importance" to importance.toString(),
                "description" to (description ?: ""),
            ),
        )
    }

    private fun ApplicationExitInfo.readTrace(): String? {
        return traceInputStream?.bufferedReader()?.use { reader ->
            val buffer = CharArray(config.maxTraceChars)
            val length = reader.read(buffer)
            if (length <= 0) "" else String(buffer, 0, length)
        }
    }

    private fun ApplicationExitInfo.dedupeKey(): String {
        return "${pid}_${timestamp}_${reason}"
    }
}
