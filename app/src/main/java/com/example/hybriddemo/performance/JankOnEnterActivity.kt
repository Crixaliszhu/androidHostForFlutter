package com.example.hybriddemo.performance

import android.os.Bundle
import android.os.SystemClock
import android.os.Trace
import androidx.appcompat.app.AppCompatActivity
import com.example.hybriddemo.databinding.ActivityJankOnEnterBinding

/**
 * 页面进入卡顿示例。
 *
 * 这是一个刻意制造的反例：页面刚进入后，在主线程执行一段限时忙循环，
 * 用来观察 Activity 启动阶段因为主线程被占满而导致的掉帧/冻结。
 *
 * 真实业务中这类工作应拆到后台线程、懒加载、分页加载或首帧后再执行。
 */
@com.alibaba.android.arouter.facade.annotation.Route(path = com.example.hybriddemo.router.DemoRouterPaths.JANK_ON_ENTER)
class JankOnEnterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJankOnEnterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJankOnEnterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvCost.text = "页面已进入，稍后将在主线程执行 900ms 卡顿任务"
        binding.tvDigest.text = "等待触发..."

        // 延迟一点执行，让页面先有机会绘制出来，避免低性能设备直接黑屏或触发 ANR。
//        binding.root.postDelayed({
//            runJankTaskAndShowCost("进入页面后主线程同步耗时")
//        }, 120)
        runJankTaskAndShowCost("进入页面后主线程同步耗时")


        binding.btnRunAgain.setOnClickListener {
            runJankTaskAndShowCost("按钮点击后主线程同步耗时")
        }
    }

    private fun runJankTaskAndShowCost(label: String) {
        try {
            Trace.beginSection("JankDemo#runJankTaskAndShowCost")
            val startMs = SystemClock.elapsedRealtime()
            val result = runHeavyMainThreadWork(durationMs = 900)
            val costMs = SystemClock.elapsedRealtime() - startMs
            binding.tvCost.text = "$label：${costMs}ms"
            binding.tvDigest.text = "模拟计算结果：$result"
        } finally {
            Trace.endSection()
        }
    }

    private fun runHeavyMainThreadWork(durationMs: Long): Long {
        val endMs = SystemClock.elapsedRealtime() + durationMs
        var value = 0L

        // CPU 密集忙循环：模拟页面启动时解析大 JSON、同步建模、图片解码等错误用法。
        while (SystemClock.elapsedRealtime() < endMs) {
            value = (value * 31 + System.nanoTime()) xor (value shl 7)
        }

        return value
    }
}
