package com.example.qualitymonitor.memory.leak

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.qualitymonitor.QualityMonitorConfig
import com.example.qualitymonitor.core.QualityEventStore
import com.example.qualitymonitor.memory.status.MemorySampler
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Activity 内存泄露监控入口。
 *
 * Activity 销毁后理论上应当可被 GC 回收；这里把销毁对象交给 ObjectWatcher，
 * 通过弱引用延迟复查判断是否存在强引用链残留。
 */
internal class LeakMonitor(
    application: Application,
    private val config: QualityMonitorConfig,
    store: QualityEventStore,
) : Application.ActivityLifecycleCallbacks {
    private val installed = AtomicBoolean(false)
    private val objectWatcher = ObjectWatcher(
        config = config,
        store = store,
        sampler = MemorySampler(application),
        hprofDumper = HprofDumper(application),
    )

    fun install(application: Application) {
        if (!config.memoryLeakEnabled || !installed.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityDestroyed(activity: Activity) {
        objectWatcher.watch(
            watchedObject = activity,
            description = "Activity 已执行 onDestroy 后仍未被回收",
        )
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
