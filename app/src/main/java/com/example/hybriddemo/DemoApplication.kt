package com.example.hybriddemo

import android.app.Application
import com.example.flutterbiz.DemoFlutterInitManager

/**
 * 宿主 App Application。
 *
 * 唯一职责：在 onCreate 触发 Flutter 引擎相关初始化。
 * 真实项目里的 `CustomApplication` 还做埋点、广告、IM 等初始化，这里精简掉。
 */
class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 触发：
        //  - FlutterEngineManager.init(...)
        //  - 注册业务 HostApi 工厂 + Provider
        //  - 主引擎常驻
        //  - idle 预热
        DemoFlutterInitManager.init(this, isKeepMainEngine = true)
    }
}
