package com.example.qualitymonitor.breadcrumb

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import java.util.ArrayDeque

/**
 * 质量事件面包屑采集器。
 *
 * 通过 Activity 生命周期记录最近页面路径和前后台状态，崩溃或 ANR 发生时可以还原用户
 * 进入现场前的关键页面流转；这里只保存类名和生命周期事件，避免持有 Activity 实例导致泄露。
 */
class QualityBreadcrumbs : Application.ActivityLifecycleCallbacks {
    private val breadcrumbs = ArrayDeque<String>()

    /** 当前可见或最近活跃 Activity 类名，只存字符串，不保留 Activity 引用。 */
    @Volatile
    var currentActivity: String? = null
        private set

    /** 前后台状态，用 started Activity 计数计算，避免单个页面切换时误判后台。 */
    @Volatile
    var foreground: Boolean = false
        private set

    private var startedCount = 0

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivity = activity.javaClass.name
        add("activity.created:${activity.javaClass.simpleName}")
    }

    override fun onActivityStarted(activity: Activity) {
        startedCount++
        foreground = startedCount > 0
        currentActivity = activity.javaClass.name
        add("activity.started:${activity.javaClass.simpleName}")
        Log.e("watchDog", "当前应用状态1：${foreground}")
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity.javaClass.name
        add("activity.resumed:${activity.javaClass.simpleName}")
    }

    override fun onActivityPaused(activity: Activity) {
        add("activity.paused:${activity.javaClass.simpleName}")
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        foreground = startedCount > 0
        add("activity.stopped:${activity.javaClass.simpleName}")
        Log.e("watchDog", "当前应用状态2：${foreground}")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        add("activity.destroyed:${activity.javaClass.simpleName}")
        if (currentActivity == activity.javaClass.name) {
            currentActivity = null
        }
    }

    @Synchronized
    fun add(message: String) {
        breadcrumbs.addLast("${System.currentTimeMillis()}:$message")
        // 只保留最近有限条，防止长时间运行后面包屑无限增长占用内存。
        while (breadcrumbs.size > MAX_BREADCRUMBS) {
            breadcrumbs.removeFirst()
        }
    }

    @Synchronized
    fun snapshot(): List<String> = breadcrumbs.toList()

    companion object {
        private const val MAX_BREADCRUMBS = 50
    }
}
