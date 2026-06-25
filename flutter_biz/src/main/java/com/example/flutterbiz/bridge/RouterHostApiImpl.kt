package com.example.flutterbiz.bridge

import android.app.Activity
import android.os.Bundle
import com.example.flutterbiz.container.DemoFlutterActivity
import com.example.flutterengine.entity.FlutterApiContext
import com.example.flutterengine.entity.FlutterPageParams
import com.example.flutterengine.manage.FlutterEngineManager
import com.example.flutterengine.registry.ApiRegistrar
import io.flutter.plugin.common.MethodChannel

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
    private val context: FlutterApiContext,
) : ApiRegistrar {

    private fun currentActivity(): Activity? = FlutterEngineManager.getHostActivity(context.engineId)

    override fun register() {
        val channel = MethodChannel(context.messenger, "com.example.hybriddemo/route")
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                // Flutter 端路由栈只剩根路由时，主动调此方法通知原生关 Activity。
                // 对应生产代码 `RouteAPI.removeFlutterContainer(instanceId: ...)`。
                "removeFlutterContainer" -> {
                    val activity = currentActivity()
                    activity?.finish()
                    result.success(null)
                }

                "pop" -> {
                    val activity = currentActivity()
                    val args = call.arguments as? Map<*, *>
                    if (activity != null) {
                        if (args != null) {
                            val bundle = Bundle().apply {
                                args.forEach { (k, v) ->
                                    if (k is String && v != null) putString(k, v.toString())
                                }
                            }
                            activity.intent?.putExtras(bundle)
                            activity.setResult(Activity.RESULT_OK, activity.intent)
                        }
                        activity.finish()
                    }
                    result.success(null)
                }

                "pushNative" -> {
                    // demo 简化：实际项目这里会调 ARouter 走原生路由表。
                    val path = call.argument<String>("path").orEmpty()
                    currentActivity()?.let { activity ->
                        android.widget.Toast.makeText(
                            activity,
                            "原生收到 pushNative：$path",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                    result.success(null)
                }

                "pushFlutter" -> {
                    val path = call.argument<String>("path").orEmpty()
                    val args = call.argument<Map<String, Any?>>("arguments").orEmpty()
                    val activity = currentActivity() ?: return@setMethodCallHandler result.success(null)
                    val finalRoute = if (args.isEmpty()) path else
                        "$path${if (path.contains("?")) "&" else "?"}" +
                            args.entries.joinToString("&") { (k, v) -> "$k=$v" }
                    DemoFlutterActivity.start(
                        activity,
                        FlutterPageParams(route = finalRoute, engineId = null),
                    )
                    result.success(null)
                }

                else -> result.notImplemented()
            }
        }
    }
}
