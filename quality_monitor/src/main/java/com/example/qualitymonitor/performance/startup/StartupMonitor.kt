package com.example.qualitymonitor.performance.startup

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.ViewTreeObserver
import com.example.qualitymonitor.QualityMonitorConfig
import com.example.qualitymonitor.core.QualityEventStore
import org.json.JSONObject

/**
 * 冷启动耗时采集器。
 *
 * 以进程启动时间到首个 Activity 首帧为主口径：Application创建 到首个Activity.onPreDraw() 首帧前最后一个回调
 * 同时记录 Activity 创建到首帧的耗时：首个Activity onCreate 到 Activity.onPreDraw() 首帧前最后一个回调
 * 便于区分 Application 初始化慢和首屏渲染慢。
 */
internal class StartupMonitor(
    private val application: Application,
    private val config: QualityMonitorConfig,
    private val store: QualityEventStore,
) : Application.ActivityLifecycleCallbacks {
    private var firstActivityName: String? = null
    private var firstActivityCreateUptime: Long = 0L
    private var reported = false

    fun install() {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (firstActivityName != null) return
        firstActivityName = activity.javaClass.name
        Log.e("watchdog", "本页面名称：${firstActivityName}---")
        firstActivityCreateUptime = SystemClock.uptimeMillis()
        // OnPreDraw 是首帧前最后一个稳定时机，适合统计“用户即将看到页面”的启动口径。
        activity.window.decorView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    activity.window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                    reportFirstDraw(activity)
                    return true
                }
            }
        )
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun reportFirstDraw(activity: Activity) {
        if (reported) return
        reported = true
        val now = SystemClock.uptimeMillis()
        // 启动事件只上报一次；后续页面首帧由 ActivityPagePerfMonitor 负责。
        store.enqueue(
            "startup",
            JSONObject()
                .put("startupType", "cold_start")
                .put("firstActivity", activity.javaClass.name)
                .put("processToFirstDrawMs", now - config.processStartUptimeMillis)
                .put("activityCreateToFirstDrawMs", now - firstActivityCreateUptime)
        )
    }
}
