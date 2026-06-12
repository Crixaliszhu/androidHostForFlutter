package com.example.flutterengine.manage

import android.content.Context
import android.util.Log
import com.example.flutterengine.config.FlutterConstants
import com.example.flutterengine.utils.FlutterArgsUtils
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineGroup

/**
 * 单纯负责"创建 FlutterEngine"。
 *
 * 用 [FlutterEngineGroup] 是为了让多个引擎共享底层资源（DartVM、native 资源），
 * 比 `new FlutterEngine()` 高效得多。
 *
 * 对应生产代码 `FlutterEngineProvider`。
 */
internal class FlutterEngineProvider(context: Context) {
    private val TAG = "FlutterEngineProvider"

    private val appContext = context.applicationContext

    private val group: FlutterEngineGroup by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FlutterEngineGroup(appContext)
    }

    /**
     * 创建一个引擎并启动 Dart 入口。
     *
     * @param initRoute 初始路由，会通过 NavigationChannel 透传给 Flutter。
     */
    fun create(initRoute: String?): FlutterEngine? {
        val route = initRoute ?: FlutterConstants.DEFAULT_ROOT_ROUTE
        val options = FlutterEngineGroup.Options(appContext)
            .setInitialRoute(route)
            .setDartEntrypointArgs(FlutterArgsUtils.createEntryArgs(appContext))

        return runCatching { group.createAndRunEngine(options) }
            .onFailure { Log.e(TAG, "create flutter engine failed: ${it.message}") }
            .getOrNull()
    }
}
