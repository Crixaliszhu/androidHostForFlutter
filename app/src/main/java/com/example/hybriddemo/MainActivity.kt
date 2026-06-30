package com.example.hybriddemo

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.flutterbiz.api.IFlutterRouterService
import com.example.flutterbiz.api.ServiceLocator
import com.example.flutterbiz.bridge.EventApiCaller
import com.example.hybriddemo.databinding.ActivityMainBinding
import com.example.hybriddemo.historyrelease.ui.HistoryReleaseComposeActivity
import com.example.hybriddemo.historyrelease.ui.HistoryReleaseDataBindingActivity
import com.example.hybriddemo.historyrelease.ui.HistoryReleaseViewBindingActivity

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
    }
}
