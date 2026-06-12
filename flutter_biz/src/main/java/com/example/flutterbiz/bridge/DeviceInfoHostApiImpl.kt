package com.example.flutterbiz.bridge

import android.os.Build
import com.example.flutterengine.entity.FlutterApiContext
import com.example.flutterengine.registry.ApiRegistrar
import io.flutter.plugin.common.MethodChannel

/**
 * 演示「带返回值 + 异步」的 HostApi。
 *
 * Flutter 端 `await DeviceInfoChannel.getInfo()` 触发，原生返回一个 Map。
 */
class DeviceInfoHostApiImpl(
    private val context: FlutterApiContext,
) : ApiRegistrar {

    override fun register() {
        val channel = MethodChannel(context.messenger, "com.example.hybriddemo/device_info")
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "getInfo" -> result.success(
                    mapOf(
                        "brand" to Build.BRAND,
                        "model" to Build.MODEL,
                        "sdkInt" to Build.VERSION.SDK_INT,
                    )
                )

                else -> result.notImplemented()
            }
        }
    }
}
