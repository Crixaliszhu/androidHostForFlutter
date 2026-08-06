package com.example.anrmonitor

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.ArrayDeque

/**
 * 采集可安全附加到 ANR 事件中的粗粒度 Activity 行为轨迹。
 */
class AnrBreadcrumbs : Application.ActivityLifecycleCallbacks {
    private val breadcrumbs = ArrayDeque<String>()

    @Volatile
    var currentActivity: String? = null
        private set

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
        while (breadcrumbs.size > MAX_BREADCRUMBS) {
            breadcrumbs.removeFirst()
        }
    }

    @Synchronized
    fun snapshot(): List<String> = breadcrumbs.toList()

    companion object {
        private const val MAX_BREADCRUMBS = 40
    }
}
