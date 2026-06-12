package com.example.flutterbiz.container

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.example.flutterengine.config.FlutterConstants
import com.example.flutterengine.container.BaseFlutterActivity
import com.example.flutterengine.entity.FlutterPageParams
import com.example.flutterengine.utils.FlutterArgsUtils
import io.flutter.embedding.android.FlutterActivityLaunchConfigs.BackgroundMode

/**
 * 业务侧的 Flutter 容器 Activity。
 *
 * 真正的项目里会用 ARouter 注解 `@Route(path = "/flutter/activity")` 暴露给整个 App，
 * demo 用 [start] / [startForResult] 静态方法直接启动。
 *
 * 对应生产代码 `FlutterMainActivity`。
 */
class DemoFlutterActivity : BaseFlutterActivity() {

    override fun getBackgroundMode(): BackgroundMode = BackgroundMode.opaque

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    companion object {
        /**
         * 启动一个 Flutter 容器。
         *
         * @param params 路由 + 引擎参数。engineId 留空时随机生成（独立引擎），
         *               传 [FlutterConstants.ENGINE_MAIN] 走主引擎。
         */
        @JvmStatic
        fun start(context: Context, params: FlutterPageParams) {
            val intent = Intent(context, DemoFlutterActivity::class.java).apply {
                putExtra(FlutterConstants.ArgsKey.ROUTE, params.route)
                putExtra(FlutterConstants.ArgsKey.CACHED_ENGINE_ID, params.engineId)
                putExtra(
                    FlutterConstants.ArgsKey.INSTANCE_ID,
                    params.instanceId ?: FlutterArgsUtils.createUUID(),
                )
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }

        @JvmStatic
        fun startForResult(activity: Activity, params: FlutterPageParams, requestCode: Int) {
            val intent = Intent(activity, DemoFlutterActivity::class.java).apply {
                putExtra(FlutterConstants.ArgsKey.ROUTE, params.route)
                putExtra(FlutterConstants.ArgsKey.CACHED_ENGINE_ID, params.engineId)
                putExtra(
                    FlutterConstants.ArgsKey.INSTANCE_ID,
                    params.instanceId ?: FlutterArgsUtils.createUUID(),
                )
            }
            activity.startActivityForResult(intent, requestCode)
        }
    }
}
