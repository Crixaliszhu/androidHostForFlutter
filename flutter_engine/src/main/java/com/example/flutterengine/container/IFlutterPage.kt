package com.example.flutterengine.container

import io.flutter.embedding.engine.FlutterEngine

/**
 * Flutter 页面接口。
 *
 * 在 [IFlutterHost] 上叠加了「Flutter 页面专属」的属性：
 * - 当前引擎/实例/路由 ID
 * - 提供 FlutterEngine 给 FlutterFragmentActivity
 * - 生命周期回调（onDestroy / onBackPressed）
 *
 * 对应生产代码 `IFlutterPage` / `FlutterPageProxy`。
 */
interface IFlutterPage : IFlutterHost {

    val instanceId: String
    val engineId: String
    val route: String

    fun provideFlutterEngine(): FlutterEngine?
    fun onDestroy()
    fun onBackPressed(): Boolean

    companion object {
        fun newInstance(host: IFlutterHost): IFlutterPage = FlutterPageProxy(host)
    }
}
