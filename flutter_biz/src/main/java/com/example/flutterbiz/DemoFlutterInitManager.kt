package com.example.flutterbiz

import android.content.Context
import android.os.Looper
import com.example.flutterbiz.api.FlutterRouterServiceImpl
import com.example.flutterbiz.api.IFlutterRouterService
import com.example.flutterbiz.api.ServiceLocator
import com.example.flutterbiz.bridge.DeviceInfoHostApiImpl
import com.example.flutterbiz.bridge.RouterHostApiImpl
import com.example.flutterbiz.bridge.ToastHostApiImpl
import com.example.flutterengine.config.FlutterConstants
import com.example.flutterengine.entity.FlutterApiContext
import com.example.flutterengine.manage.FlutterEngineManager
import com.example.flutterengine.registry.ApiRegistrar

/**
 * 业务侧 Flutter 初始化入口。
 *
 * 在 Application.onCreate 里调一次 [init]：
 * 1. 注册业务 HostApi 工厂（`bizHandlerApi`），引擎创建时会自动调起注册。
 * 2. 注册 Provider（`IFlutterRouterService` 等）。
 * 3. 配置「主引擎不销毁」白名单 + idle 预热主引擎。
 *
 * 对应生产代码 `FlutterInitManager`。
 */
object DemoFlutterInitManager {

    fun init(context: Context, isKeepMainEngine: Boolean = true) {
        // 1) 引擎管理器初始化（持 application context 用作 FlutterEngineGroup 创建参数）。
        FlutterEngineManager.init(context.applicationContext)

        // 2) 业务 API 工厂。引擎一创建，FlutterEngineManager 会用这个回调收集 ApiRegistrar 列表。
        FlutterEngineManager.setHandlerApiRegister { ctx -> fetchBizApis(context.applicationContext, ctx) }

        // 3) 业务 Provider 注册（替代 ARouter）。
        ServiceLocator.register(IFlutterRouterService::class) { FlutterRouterServiceImpl() }

        // 4) 主引擎常驻。
        if (isKeepMainEngine) {
            FlutterEngineManager.addKeepEmptyEngineIds(setOf(FlutterConstants.ENGINE_MAIN))
        }

        // 5) 主线程空闲时预热主引擎。生产代码还会判断应用是否前台。
        Looper.myQueue().addIdleHandler {
            FlutterEngineManager.initMainEngine()
            false // idle 一次后移除
        }
    }

    private fun fetchBizApis(appContext: Context, ctx: FlutterApiContext): List<ApiRegistrar> {
        return listOf(
            ToastHostApiImpl(ctx, appContext),
            DeviceInfoHostApiImpl(ctx),
            RouterHostApiImpl(ctx),
            // 真实项目这里还有 30+ 个：账号、IM、媒体、网络、上报…
        )
    }
}
