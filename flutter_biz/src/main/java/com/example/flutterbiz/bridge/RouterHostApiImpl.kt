package com.example.flutterbiz.bridge

import android.app.Activity
import android.os.Bundle
import com.example.flutterbiz.container.DemoFlutterActivity
import com.example.flutterengine.entity.FlutterApiContext
import com.example.flutterengine.entity.FlutterPageParams
import com.example.flutterengine.manage.FlutterEngineManager
import com.example.flutterengine.pigeon.RouteHostApi
import com.example.flutterengine.registry.ApiRegistrar

/**
 * 路由 HostApi。
 *
 * 演示 Flutter ↔ Native 互跳与 setResult 模型：
 * - method=`pop`：关闭当前 Flutter 容器，可携带返回数据。
 * - method=`pushNative`：跳到一个原生页面（demo 这里用 Toast 模拟，实际接入会走 ARouter）。
 * - method=`pushFlutter`：再开一个 Flutter 容器（独立引擎）。
 *
 * 「当前承载 Activity」从 [FlutterEngineManager.getHostActivity] 取，避免桥接层
 * 自己维护 Activity 弱引用。
 */
class RouterHostApiImpl(
    private val apiContext: FlutterApiContext,
) : RouteHostApi, ApiRegistrar {

    private fun currentActivity(): Activity? = FlutterEngineManager.getHostActivity(apiContext.engineId)

    override fun register() {
        RouteHostApi.setUp(apiContext.messenger, this)
    }

    override fun removeFlutterContainer(instanceId: String) {
        currentActivity()?.finish()
    }

    override fun popRoute(arguments: Map<String?, Any?>?) {
        val activity = currentActivity()
        if (activity != null) {
            if (arguments != null) {
                val bundle = Bundle().apply {
                    arguments.forEach { (k, v) ->
                        if (k != null && v != null) putString(k, v.toString())
                    }
                }
                activity.intent?.putExtras(bundle)
                activity.setResult(Activity.RESULT_OK, activity.intent)
            }
            activity.finish()
        }
    }

    override fun pushNativeRoute(path: String, arguments: Map<String?, Any?>?) {
        currentActivity()?.let { activity ->
            android.widget.Toast.makeText(
                activity,
                "原生收到 pushNative：$path",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    override fun pushFlutterRoute(path: String, arguments: Map<String?, Any?>?) {
        val finalArgs = arguments.orEmpty()
        val activity = currentActivity() ?: return
        val finalRoute = if (finalArgs.isEmpty()) {
            path
        } else {
            "$path${if (path.contains("?")) "&" else "?"}" +
                finalArgs.entries.joinToString("&") { (k, v) -> "${k.orEmpty()}=$v" }
        }
        DemoFlutterActivity.start(
            activity,
            FlutterPageParams(route = finalRoute, engineId = null),
        )
    }
}
