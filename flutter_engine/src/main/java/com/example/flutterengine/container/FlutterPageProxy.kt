package com.example.flutterengine.container

import android.net.Uri
import android.os.Bundle
import com.example.flutterengine.config.FlutterConstants
import com.example.flutterengine.entity.FlutterPageEntity
import com.example.flutterengine.manage.FlutterEngineManager
import com.example.flutterengine.utils.FlutterArgsUtils
import io.flutter.embedding.engine.FlutterEngine
import org.json.JSONObject

/**
 * Flutter 页面代理。
 *
 * 把 [BaseFlutterActivity] 这层"原生 Activity 模板代码"和"Flutter 页面行为"解耦：
 * - Activity / Fragment 只负责声明 `IFlutterHost` 能力（拿 activity、参数、关闭页面等）。
 * - 与引擎打交道、拼路由协议、登记/移除页面等行为，全部走代理。
 *
 * 对应生产代码 `FlutterPageProxy`。
 */
class FlutterPageProxy(
    private val host: IFlutterHost,
) : IFlutterPage, IFlutterHost by host {

    /** 实例 ID。容器可以从 Intent 参数里读，没读到就生成一个新的。 */
    override val instanceId: String by lazy {
        host.providerArgs()?.getString(FlutterConstants.ArgsKey.INSTANCE_ID)
            ?: FlutterArgsUtils.createUUID()
    }

    /** 引擎 ID。null 表示开一个独立引擎。 */
    override val engineId: String by lazy {
        host.providerArgs()?.getString(FlutterConstants.ArgsKey.CACHED_ENGINE_ID)
            ?: FlutterArgsUtils.createUUID()
    }

    /**
     * 给 Flutter 端的"完整路由"。
     *
     * 路由协议：
     * - 把 Intent 中 `route` 字段当原始路由（可能自带 query 参数）。
     * - 在 query 里附加 `nativeParams=<json>`，里头塞 instanceId + 透传的业务参数。
     *
     * 对应生产代码 `FlutterPageProxy.getFlutterRoute`。
     */
    override val route: String by lazy {
        val rawRoute = host.providerArgs()?.getString(FlutterConstants.ArgsKey.ROUTE) ?: "flutter/root"
        val original = Uri.parse(if (rawRoute.startsWith("/")) "demo:$rawRoute" else "demo:/$rawRoute")
        val nativeParams = JSONObject().apply {
            put("instanceId", instanceId)
            // 把原 query 参数也展开进 nativeParams，让 Flutter 拿到统一形态。
            original.queryParameterNames.forEach { put(it, original.getQueryParameter(it)) }
        }
        // 用 path 重建 URL，避免和原 query 冲突。
        val path = (original.path ?: "/").trimStart('/')
        "$path?nativeParams=${Uri.encode(nativeParams.toString())}"
    }

    override fun provideFlutterEngine(): FlutterEngine? {
        val wasAlive = FlutterEngineManager.isAliveEngine(engineId)
        val engine = FlutterEngineManager.fetchFlutterEngine(engineId, route) ?: return null
        // 复用引擎时，Flutter 已在跑，需要主动 push 一个新路由进去。
        if (wasAlive && engine.dartExecutor.isExecutingDart) {
            engine.navigationChannel.pushRoute(route)
        }
        FlutterEngineManager.addPage(
            engineId,
            FlutterPageEntity(instanceId = instanceId, route = route, page = this),
        )
        return engine
    }

    override fun onDestroy() {
        FlutterEngineManager.removePage(engineId, instanceId)
    }

    override fun onBackPressed(): Boolean {
        // 把 pop 动作交给 Flutter Navigator 处理。
        // Flutter 端 RouteStackObserver 会在栈里只剩根路由时，
        // 主动调 RouteChannel.removeFlutterContainer(instanceId) 通知原生 finish Activity。
        // 这样 Flutter 完全掌控路由栈，不会出现白屏。
        val engine = FlutterEngineManager.getFlutterEngine(engineId) ?: return false
        return runCatching { engine.navigationChannel.popRoute() }.isSuccess
    }
}
