package com.example.qualitymonitor.performance.page

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.ViewTreeObserver
import com.example.qualitymonitor.core.QualityEventStore
import org.json.JSONObject

/**
 * Activity 页面首帧自动采集器。
 *
 * 通过 ActivityLifecycleCallbacks 记录每个原生页面创建、恢复和首帧时间，
 * 不需要业务页面逐个接入即可获得基础页面加载耗时。
 */
internal class ActivityPagePerfMonitor(
    private val application: Application,
    private val store: QualityEventStore,
    private val enableList: List<String>,
) : Application.ActivityLifecycleCallbacks {
    private val sessions = mutableMapOf<Int, ActivitySession>()

    fun install() {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // 使用 identityHashCode 区分同一 Activity 类的多个实例，避免快速重建时会话互相覆盖。
        val key = System.identityHashCode(activity)
        val activityName = activity.javaClass.name
        if (!enableList.contains(activityName)) {
            return
        }
        val session = ActivitySession(
            activityName = activityName,
            createUptime = SystemClock.uptimeMillis(),
        )
        sessions[key] = session
        activity.window.decorView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    activity.window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                    reportFirstDraw(activity, key)
                    return true
                }
            }
        )
    }

    override fun onActivityResumed(activity: Activity) {
        sessions[System.identityHashCode(activity)]?.resumeUptime = SystemClock.uptimeMillis()
    }

    override fun onActivityDestroyed(activity: Activity) {
        // 页面销毁时兜底清理，防止未触发首帧的异常页面残留会话。
        sessions.remove(System.identityHashCode(activity))
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private fun reportFirstDraw(activity: Activity, key: Int) {
        val session = sessions[key] ?: return
        if (!enableList.contains(activity.javaClass.name)) {
            return
        }
        val firstDrawUptime = SystemClock.uptimeMillis()
        // 第一阶段自动口径只定义到首帧；业务数据 ready 和可交互由页面按需调用 PagePerf API 补点。
        store.enqueue(
            "page_perf",
            JSONObject()
                .put("pageName", session.activityName)
                .put("route", session.activityName)
                .put("containerType", "native_activity")
                .put("durationFirstDrawMs", firstDrawUptime - session.createUptime)
                .put("durationResumeMs", session.resumeUptime?.let { it - session.createUptime })
                .put("auto", true)
        )
        sessions.remove(System.identityHashCode(activity))
    }

    /** Activity 自动采集会话，保存首帧统计需要的最小状态。 */
    private data class ActivitySession(
        val activityName: String,
        val createUptime: Long,
        var resumeUptime: Long? = null,
    )
}
