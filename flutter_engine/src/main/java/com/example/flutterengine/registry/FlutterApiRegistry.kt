package com.example.flutterengine.registry

import android.util.Log

/**
 * 桥接 API 注册中心。
 *
 * - 基础 API 由 `flutter_engine` 模块自己提供（这里 demo 没有，留空）。
 * - 业务 API 由业务方在 `setHandlerApiRegister` 钩子里塞进来（见 `DemoFlutterInitManager`）。
 *
 * 引擎创建后会调一次 [performRegister]，把这一批 API 全部 register 一遍。
 *
 * 对应生产代码 `FlutterApiRegistry`。
 */
object FlutterApiRegistry {
    private const val TAG = "FlutterApiRegistry"

    fun performRegister(list: List<ApiRegistrar>) {
        list.forEach { item ->
            runCatching { item.register() }
                .onFailure { Log.e(TAG, "register ${item.getName()} failed: ${it.message}") }
        }
    }
}
