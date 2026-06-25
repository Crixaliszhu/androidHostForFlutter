package com.example.flutterbiz.bridge

import android.os.Build
import com.example.flutterengine.entity.FlutterApiContext
import com.example.flutterengine.pigeon.DeviceInfo
import com.example.flutterengine.pigeon.DeviceInfoHostApi
import com.example.flutterengine.registry.ApiRegistrar

/**
 * 演示「带返回值 + 异步」的 HostApi。
 */
class DeviceInfoHostApiImpl(
    private val apiContext: FlutterApiContext,
) : DeviceInfoHostApi, ApiRegistrar {

    override fun register() {
        DeviceInfoHostApi.setUp(apiContext.messenger, this)
    }

    override fun getInfo(callback: (Result<DeviceInfo>) -> Unit) {
        callback(
            Result.success(
                DeviceInfo(
                    brand = Build.BRAND,
                    model = Build.MODEL,
                    sdkInt = Build.VERSION.SDK_INT.toLong(),
                )
            )
        )
    }
}
