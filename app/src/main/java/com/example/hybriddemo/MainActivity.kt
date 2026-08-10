package com.example.hybriddemo

import android.os.Bundle
import android.content.Intent
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.flutterbiz.api.IFlutterRouterService
import com.example.flutterbiz.api.ServiceLocator
import com.example.flutterbiz.bridge.EventApiCaller
import com.example.hybriddemo.databinding.ActivityMainBinding
import com.example.hybriddemo.anrdemo.AnrCasesDemoActivity
import com.example.hybriddemo.customview.JobSearchCollapseDemoActivity
import com.example.hybriddemo.customview.PathAnimationDemoActivity
import com.example.hybriddemo.flowcompose.FlowComposeActivity
import com.example.hybriddemo.historyrelease.ui.HistoryReleaseComposeActivity
import com.example.hybriddemo.historyrelease.ui.HistoryReleaseDataBindingActivity
import com.example.hybriddemo.historyrelease.ui.HistoryReleaseViewBindingActivity
import com.example.hybriddemo.ipc.ui.IpcDemoActivity
import com.example.hybriddemo.mediastore.PhotoPickerDemoActivity
import com.example.hybriddemo.performance.JankOnEnterActivity
import com.example.hybriddemo.performance.MemoryLeakDemoActivity
import com.example.hybriddemo.sentry.SentryDemoActivity
import com.example.hybriddemo.service.page.ServiceDemoActivity
import com.example.hybriddemo.service.workmanager.WorkManagerDemoActivity
import com.example.hybriddemo.settings.SettingsActivity
import com.example.hybriddemo.storage.demo.StorageBestPracticeActivity
import com.example.hybriddemo.xbus.XBusMainActivity
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
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
        }

        binding.btnOpenHistoryDataBinding.setOnClickListener {
            startActivity(Intent(this, HistoryReleaseDataBindingActivity::class.java))
        }

        binding.btnOpenHistoryViewBinding.setOnClickListener {
            startActivity(Intent(this, HistoryReleaseViewBindingActivity::class.java))
        }

        binding.btnOpenHistoryCompose.setOnClickListener {
            startActivity(Intent(this, HistoryReleaseComposeActivity::class.java))
        }

        binding.btnOpenFlowCompose.setOnClickListener {
            startActivity(Intent(this, FlowComposeActivity::class.java))
        }

        binding.btnOpenStorageBestPractice.setOnClickListener {
            startActivity(Intent(this, StorageBestPracticeActivity::class.java))
        }

        binding.btnXbusMain.setOnClickListener {
            startActivity(Intent(this, XBusMainActivity::class.java))
        }

        binding.btnService.setOnClickListener {
            startActivity(Intent(this, ServiceDemoActivity::class.java))
        }

        binding.btnWorkManager.setOnClickListener {
            startActivity(Intent(this, WorkManagerDemoActivity::class.java))
        }

        binding.btnPhotoPicker.setOnClickListener {
            startActivity(Intent(this, PhotoPickerDemoActivity::class.java))
        }

        binding.btnServiceMessenger.setOnClickListener {
            startActivity(Intent(this, IpcDemoActivity::class.java))
        }

        binding.btnOpenJankOnEnter.setOnClickListener {
            startActivity(Intent(this, JankOnEnterActivity::class.java))
        }

        binding.btnOpenMemoryLeakDemo.setOnClickListener {
            startActivity(Intent(this, MemoryLeakDemoActivity::class.java))
        }

        binding.btnOpenJobSearchCollapseDemo.setOnClickListener {
            startActivity(Intent(this, JobSearchCollapseDemoActivity::class.java))
        }

        binding.btnOpenPathAnimationDemo.setOnClickListener {
            startActivity(Intent(this, PathAnimationDemoActivity::class.java))
//            throw NullPointerException()
        }

        binding.btnOpenSentryDemo.setOnClickListener {
            startActivity(Intent(this, SentryDemoActivity::class.java))
        }

        binding.btnOpenAnrCasesDemo.setOnClickListener {
            startActivity(Intent(this, AnrCasesDemoActivity::class.java))
        }

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED){
            }
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
