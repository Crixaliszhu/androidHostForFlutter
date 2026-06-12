package com.example.flutterbiz.api

import android.content.Context
import com.example.flutterbiz.container.DemoFlutterActivity
import com.example.flutterengine.config.FlutterConstants
import com.example.flutterengine.entity.FlutterPageParams

/**
 * 对外暴露的 Flutter 路由能力。
 *
 * 业务模块只需要拿到这个接口，不需要知道 Flutter Activity 的具体类。
 * 生产代码会用 ARouter Provider，这里用 [ServiceLocator] 注册。
 */
interface IFlutterRouterService {
    /**
     * 跳到 Flutter 页面。
     *
     * @param route Flutter 路由，比如 `flutter/detail`，可携带 query 参数。
     * @param args  额外参数，会拼到路由 query 上（最终在 Flutter 端通过 nativeParams 拿到）。
     * @param useMainEngine true 表示用主引擎渲染（首页类），false 起独立引擎。
     */
    fun goFlutter(
        context: Context,
        route: String,
        args: Map<String, Any?>? = null,
        useMainEngine: Boolean = false,
    )
}

/** 默认实现。 */
class FlutterRouterServiceImpl : IFlutterRouterService {
    override fun goFlutter(
        context: Context,
        route: String,
        args: Map<String, Any?>?,
        useMainEngine: Boolean,
    ) {
        val finalRoute = if (args.isNullOrEmpty()) route else buildRouteWithArgs(route, args)
        DemoFlutterActivity.start(
            context,
            FlutterPageParams(
                route = finalRoute,
                engineId = if (useMainEngine) FlutterConstants.ENGINE_MAIN else null,
            ),
        )
    }

    private fun buildRouteWithArgs(route: String, args: Map<String, Any?>): String {
        // 演示用：把 args 转成 query 字符串。生产代码会序列化成 paramsStr JSON 保留复杂类型。
        val sep = if (route.contains("?")) "&" else "?"
        val query = args.entries.joinToString("&") { (k, v) -> "$k=$v" }
        return "$route$sep$query"
    }
}
