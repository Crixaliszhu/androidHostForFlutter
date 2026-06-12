package com.example.flutterengine.container

import android.content.Context
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.example.flutterengine.utils.FlutterArgsUtils
import io.flutter.embedding.android.FlutterFragment
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.android.RenderMode
import io.flutter.embedding.engine.FlutterEngine

/**
 * Flutter 容器 Activity 基类。
 *
 * 职责：
 * - 适配 [IFlutterHost]，把 Activity 能力暴露给 [FlutterPageProxy]。
 * - 重写 FlutterFragmentActivity 钩子，把引擎管理交给 [FlutterPageProxy]。
 * - `shouldDestroyEngineWithHost = false`：容器销毁时不销毁引擎，引擎生命周期由 `FlutterEngineManager` 管。
 * - `RenderMode.texture`：避免 SurfaceView 在某些机型上盖住原生悬浮 View。
 *
 * 对应生产代码 `BaseFlutterActivity`。
 */
open class BaseFlutterActivity : FlutterFragmentActivity(), IFlutterHost {

    private val flutterPage by lazy { IFlutterPage.newInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 注册自己为当前 engineId 的活跃宿主，HostApi 通过 FlutterEngineManager.getHostActivity 拿。
        com.example.flutterengine.manage.FlutterEngineManager.bindHostActivity(flutterPage.engineId, this)
        // 自己处理返回键，避免 FlutterFragmentActivity 默认行为关闭 Activity。
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!flutterPage.onBackPressed()) {
                    finish()
                }
            }
        })
    }

    override fun onDestroy() {
        com.example.flutterengine.manage.FlutterEngineManager.bindHostActivity(flutterPage.engineId, null)
        super.onDestroy()
        // 必须放在 super.onDestroy 后，避免 Flutter 端还在引用已销毁的 view。
        flutterPage.onDestroy()
    }

    // ---------------- FlutterFragmentActivity 钩子 ----------------

    override fun shouldDestroyEngineWithHost(): Boolean = false

    override fun getCachedEngineId(): String? = null // 自管引擎缓存，不让 FlutterEngineCache 介入。

    override fun provideFlutterEngine(context: Context): FlutterEngine? = flutterPage.provideFlutterEngine()

    override fun getInitialRoute(): String? = flutterPage.route

    override fun getDartEntrypointArgs(): List<String> = FlutterArgsUtils.createEntryArgs(this)

    override fun getRenderMode(): RenderMode = RenderMode.texture

    override fun createFlutterFragment(): FlutterFragment {
        return super.createFlutterFragment().apply {
            // 与 shouldDestroyEngineWithHost 配套：Fragment 销毁时也别销毁引擎。
            arguments?.putBoolean("destroy_engine_with_fragment", false)
        }
    }

    // ---------------- IFlutterHost ----------------

    override fun providerActivity(): FragmentActivity? = if (isFinishing || isDestroyed) null else this
    override fun providerLifecycleOwner(): LifecycleOwner? = providerActivity()
    override fun providerArgs(): Bundle? = intent?.extras

    override fun finish(resultCode: Int?, data: Bundle?) {
        if (resultCode != null) {
            val resultIntent = (intent ?: android.content.Intent()).apply { if (data != null) putExtras(data) }
            setResult(resultCode, resultIntent)
        }
        finish()
    }
}
