package com.example.hybriddemo

import android.app.Application
import android.os.SystemClock
import com.example.anrmonitor.config.SelfAnrConfig
import com.example.anrmonitor.SelfAnrInitializer
import com.example.flutterbiz.DemoFlutterInitManager
import com.example.hybriddemo.sentry.SentryInitializer
import com.tencent.mmkv.MMKV

/**
 * 宿主 App Application。
 *
 * 唯一职责：在 onCreate 触发 Flutter 引擎相关初始化。
 * 真实项目里的 `CustomApplication` 还做埋点、广告、IM 等初始化，这里精简掉。
 */
class DemoApplication : Application() {
    companion object {
        val processStartUptimeMillis: Long = SystemClock.uptimeMillis()
    }

    override fun onCreate() {
        super.onCreate()
        SentryInitializer.init(this)
        SelfAnrInitializer.init(
            application = this,
            config = SelfAnrConfig(
                enabled = BuildConfig.SELF_ANR_ENABLED,
                reportUrl = BuildConfig.SELF_ANR_REPORT_URL,
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
