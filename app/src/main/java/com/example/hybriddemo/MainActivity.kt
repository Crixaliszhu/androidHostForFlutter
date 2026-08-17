package com.example.hybriddemo

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.alibaba.android.arouter.facade.annotation.Route
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alibaba.android.arouter.launcher.ARouter
import com.example.appapi.DemoRouterApiPaths
import com.example.appapi.IDemoRouterService
import com.example.flutterbiz.api.IFlutterRouterService
import com.example.flutterbiz.api.ServiceLocator
import com.example.flutterbiz.bridge.EventApiCaller
import com.example.hybriddemo.databinding.ActivityMainBinding
import com.example.hybriddemo.router.DemoRouterPaths
import com.example.hybriddemo.sf.CalculateUtils
import com.example.recruit.api.IRecruitRouterService
import com.example.recruit.api.RecruitRouterApiPaths
import com.example.router.RouterApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * 宿主 App 主页面。
 *
 * 提供 3 个按钮，分别演示：
 * 1. 走主引擎 + 默认路由打开 Flutter 首页（验证「主引擎复用」）。
 * 2. 走独立引擎 + 携带参数打开 Flutter 详情页（验证「nativeParams 协议」）。
 * 3. 直接调 [EventApiCaller] 推一个 `tick` 事件给所有 Flutter 引擎。
 */
@Route(path = DemoRouterPaths.MAIN)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val demoRouter: IDemoRouterService?
        get() = RouterApi.getByPath(
            DemoRouterApiPaths.DEMO_ROUTER_SERVICE,
            IDemoRouterService::class.java,
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        showAppVersion()
        reportColdStartupAfterFirstFrame()

        binding.btnOpenHome.setOnClickListener {
            // 主引擎打开首页：useMainEngine = true。
            ServiceLocator.get(IFlutterRouterService::class)
                ?.goFlutter(this, route = "flutter/home", useMainEngine = true)
        }

        binding.btnOpenDetail.setOnClickListener {
            // 独立引擎打开详情页 + 透传业务参数。
            ServiceLocator.get(IFlutterRouterService::class)?.goFlutter(
                this,
                route = "flutter/detail",
                args = mapOf(
                    "id" to 123,
                    "title" to "from_native",
                ),
                useMainEngine = false,
            )
        }

        binding.btnPushTick.setOnClickListener {
            // 跨引擎广播：所有正在显示的 Flutter 页面（home 监听了）都会 +1。
            EventApiCaller.sendTick()
            val result = CalculateUtils.firstBoundSearch(intArrayOf(1,4,6,8,8,8,10,14,16,19), 8)
            Log.e("CalculateUtils"," 打印答案 ================================== ${result}")
        }

        binding.btnOpenHistoryDataBinding.setOnClickListener {
            demoRouter?.openHistoryDataBinding(this)
        }

        binding.btnOpenHistoryViewBinding.setOnClickListener {
            demoRouter?.openHistoryViewBinding(this)
        }

        binding.btnOpenHistoryCompose.setOnClickListener {
            demoRouter?.openHistoryCompose(this)
        }

        binding.btnOpenFlowCompose.setOnClickListener {
            demoRouter?.openFlowCompose(this)
        }

        binding.btnOpenStorageBestPractice.setOnClickListener {
            demoRouter?.openStorageBestPractice(this)
        }

        binding.btnXbusMain.setOnClickListener {
            demoRouter?.openXBusMain(this)
        }

        binding.btnService.setOnClickListener {
            demoRouter?.openService(this)
        }

        binding.btnWorkManager.setOnClickListener {
            demoRouter?.openWorkManager(this)
        }

        binding.btnPhotoPicker.setOnClickListener {
            demoRouter?.openPhotoPicker(this)
        }

        binding.btnServiceMessenger.setOnClickListener {
            demoRouter?.openIpc(this)
        }

        binding.btnOpenJankOnEnter.setOnClickListener {
            demoRouter?.openJankOnEnter(this)
        }

        binding.btnOpenMemoryLeakDemo.setOnClickListener {
            demoRouter?.openMemoryLeak(this)
        }

        binding.btnOpenJobSearchCollapseDemo.setOnClickListener {
            demoRouter?.openJobSearchCollapse(this)
        }

        binding.btnOpenPathAnimationDemo.setOnClickListener {
            demoRouter?.openPathAnimation(this)
//            throw NullPointerException()
        }

        binding.btnOpenSentryDemo.setOnClickListener {
            demoRouter?.openSentry(this)
        }

        binding.btnOpenAnrCasesDemo.setOnClickListener {
            demoRouter?.openAnrCases(this)
        }

        binding.btnOpenSettings.setOnClickListener {
            demoRouter?.openSettings(this)
        }

        binding.btnRecruit.setOnClickListener {
            RouterApi.getByPath(
                RecruitRouterApiPaths.RECRUIT_ROUTER_SERVICE,
                IRecruitRouterService::class.java,
            )?.open(this)
        }
    }

    private fun showAppVersion() {
        // 首页底部展示的是当前安装包真实 BuildConfig 版本，便于测试时确认手机上安装的
        // 是否就是刚打出来的 release/debug 包，也能和 Sentry release 信息互相核对。
        binding.tvAppVersion.text = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    private fun reportColdStartupAfterFirstFrame() {
        binding.root.post {
            val costMillis = SystemClock.uptimeMillis() - DemoApplication.processStartUptimeMillis
            GodEyeStartupReporter.reportColdStart(costMillis)
        }
    }

    inline fun postBlock(block:() -> Unit){
        block()
    }

    private fun test(){
        postBlock {
            return@test
        }
        runBlock {
            return@runBlock
        }
    }

    inline fun runBlock(crossinline block:()->Unit){
        block()
    }
}
