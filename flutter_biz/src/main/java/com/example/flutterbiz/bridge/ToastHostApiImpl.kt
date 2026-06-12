package com.example.flutterbiz.bridge

import android.widget.Toast
import com.example.flutterengine.entity.FlutterApiContext
import com.example.flutterengine.registry.ApiRegistrar
import io.flutter.plugin.common.MethodChannel

/**
 * 简单 Toast HostApi。
 *
 * 协议（生产代码用 Pigeon 自动生成）：
 * - method = `show`
 * - arguments = `{ "message": String }`
 *
 * 对应 Flutter 端 `ToastChannel.show(...)`。
 */
class ToastHostApiImpl(
    private val context: FlutterApiContext,
    private val applicationContext: android.content.Context,
) : ApiRegistrar {

    override fun register() {
        val channel = MethodChannel(context.messenger, "com.example.hybriddemo/toast")
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "show" -> {
                    val message = call.argument<String>("message").orEmpty()
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                    result.success(null)
                }

                else -> result.notImplemented()
            }
        }
    }
}
