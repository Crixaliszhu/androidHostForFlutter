package com.example.hybriddemo.performance

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.hybriddemo.databinding.ActivityMemoryLeakDemoBinding

class MemoryLeakDemoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryLeakDemoBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val demoPayload = ByteArray(8 * 1024 * 1024)

    private val delayedTask = Runnable {
        binding.tvLeakStatus.text = "延迟任务执行完成"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryLeakDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCreateLeak.setOnClickListener {
            createActivityLeak()
        }

        binding.btnClearLeak.setOnClickListener {
            clearDemoLeak()
        }

        binding.btnRequestGc.setOnClickListener {
            requestGc()
        }

        binding.btnFinishPage.setOnClickListener {
            finish()
        }
    }

    private fun createActivityLeak() {
        demoPayload[0] = 1
        val listener = View.OnClickListener {
            binding.tvLeakStatus.text = "静态 listener 仍然持有旧 Activity"
        }
        MemoryLeakRegistry.leak(this, listener, mainHandler, delayedTask)
        mainHandler.postDelayed(delayedTask, 10 * 60 * 1000L)
        binding.tvLeakStatus.text =
            "已制造泄漏：点击返回或关闭页面后，Activity 仍会被静态对象和 Handler 回调持有"
    }

    private fun clearDemoLeak() {
        MemoryLeakRegistry.clear()
        mainHandler.removeCallbacksAndMessages(null)
        binding.tvLeakStatus.text = "已清理示例泄漏，可重新抓取 Heap Dump 对比"
    }

    private fun requestGc() {
        Runtime.getRuntime().gc()
        Runtime.getRuntime().runFinalization()
        Runtime.getRuntime().gc()
        binding.tvLeakStatus.text = "已请求 GC，请回到 Memory Profiler 重新抓取 Heap Dump"
    }

    override fun onDestroy() {
        super.onDestroy()
        // Intentionally do not clear the registry or handler callback here.
        // This keeps the destroyed Activity retained so Memory Profiler can find it.
    }
}
