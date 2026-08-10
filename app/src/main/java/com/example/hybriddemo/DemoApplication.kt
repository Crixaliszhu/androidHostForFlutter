package com.example.hybriddemo

import android.app.Application
import android.os.SystemClock
import com.example.flutterbiz.DemoFlutterInitManager
import com.example.hybriddemo.sentry.SentryInitializer
import com.example.qualitymonitor.QualityMonitorConfig
import com.example.qualitymonitor.QualityMonitorInitializer
import com.tencent.mmkv.MMKV

/**
 * 宿主 App Application。
 *
 * Demo 中集中初始化 Sentry、自研质量监控、MMKV 和 Flutter 引擎；真实项目里的
 * `CustomApplication` 还可能包含埋点、广告、IM 等更多启动项，这里只保留教学主线。
 */
class DemoApplication : Application() {
    companion object {
        /** 进程加载 Application 类时记录时间，供自研启动耗时采集计算冷启动总耗时。 */
        val processStartUptimeMillis: Long = SystemClock.uptimeMillis()
    }

    override fun onCreate() {
        super.onCreate()
        SentryInitializer.init(this)
        // 自研质量监控尽量靠前初始化，保证后续初始化阶段的崩溃和启动耗时也能被采集。
        QualityMonitorInitializer.init(
            application = this,
            config = QualityMonitorConfig(
                enabled = BuildConfig.QUALITY_MONITOR_ENABLED,
                uploadUrl = BuildConfig.QUALITY_MONITOR_UPLOAD_URL,
                appId = BuildConfig.APPLICATION_ID,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                processStartUptimeMillis = processStartUptimeMillis,
                anrEnabled = BuildConfig.SELF_ANR_ENABLED,
                activityOnPreDrawList = listOf("com.example.hybriddemo.sentry.SentryDemoActivity")
            ),
        )
        MMKV.initialize(this)
        // 触发：
        //  - FlutterEngineManager.init(...)
        //  - 注册业务 HostApi 工厂 + Provider
        //  - 主引擎常驻
        //  - idle 预热
        DemoFlutterInitManager.init(this, isKeepMainEngine = true)
    }
}
