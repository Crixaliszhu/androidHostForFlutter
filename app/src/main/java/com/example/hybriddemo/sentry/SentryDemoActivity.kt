package com.example.hybriddemo.sentry

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import com.example.hybriddemo.BuildConfig
import com.example.hybriddemo.databinding.ActivitySentryDemoBinding
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SpanStatus

@com.alibaba.android.arouter.facade.annotation.Route(path = com.example.hybriddemo.router.DemoRouterPaths.SENTRY)
class SentryDemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySentryDemoBinding
    private val mainHandler = Handler(Looper.getMainLooper())

    // 忙等循环的结果写入字段，避免 R8/JIT 认为循环计算没有副作用而激进优化。
    // Demo 的目的就是让主线程真实处于长时间执行状态，从而稳定触发 Sentry ANR watchdog。
    private var anrBusyLoopGuard = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySentryDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvSentryStatus.text = buildString {
            appendLine("Sentry 开发调试 Demo")
            appendLine("DSN configured: ${BuildConfig.SENTRY_DSN.isNotBlank()}")
            appendLine("Environment: ${BuildConfig.SENTRY_ENVIRONMENT}")
        }
        binding.tvSentryResult.text =
            "没有 DSN 时不会发到 Sentry 后台；配置 DSN 后点击按钮即可在 Issues / Performance / Profiling / ANR 中查看。"

        binding.btnCaptureMessage.setOnClickListener {
            Sentry.captureMessage("AndroidHostForFlutter Sentry message demo", SentryLevel.INFO)
            showResult("已发送 message。")
        }

        binding.btnCaptureHandledException.setOnClickListener {
            Sentry.addBreadcrumb(Breadcrumb.info("User clicked handled exception demo"))
            Sentry.captureException(IllegalStateException("Sentry handled exception demo"))
            showResult("已上报已捕获异常。")
        }

        binding.btnManualTransaction.setOnClickListener {
            reportManualTransaction()
        }

        binding.btnMainThreadJank.setOnClickListener {
            Sentry.addBreadcrumb(Breadcrumb.info("Start 2200ms main thread jank"))
            SystemClock.sleep(2_200)
            showResult("主线程已阻塞 2200ms；查看 Performance 事务和 slow/frozen frame。")
        }

        binding.btnAnr.setOnClickListener {
            scheduleStableAnrDemo()
        }

        binding.btnCrash.setOnClickListener {
            throw NullPointerException("Sentry uncaught crash demo")
        }
    }

    private fun reportManualTransaction() {
        val transaction = Sentry.startTransaction("sentry-demo-main-thread-work", "ui.action")
        val span = transaction.startChild("task.cpu", "simulate main thread work")
        try {
            SystemClock.sleep(1_200)
            span.setStatus(SpanStatus.OK)
            transaction.setStatus(SpanStatus.OK)
            showResult("已完成手动性能事务，耗时约 1200ms。")
        } catch (throwable: Throwable) {
            span.setThrowable(throwable)
            span.setStatus(SpanStatus.INTERNAL_ERROR)
            transaction.setThrowable(throwable)
            transaction.setStatus(SpanStatus.INTERNAL_ERROR)
            Sentry.captureException(throwable)
        } finally {
            span.finish()
            transaction.finish()
        }
    }

    private fun scheduleStableAnrDemo() {
        binding.btnAnr.isEnabled = false
        Sentry.addBreadcrumb(
            Breadcrumb.info("Schedule stable ANR demo").apply {
                category = "demo.anr"
                setData("block_duration_ms", STABLE_ANR_BLOCK_MS)
            }
        )
        showResult("即将阻塞主线程 15 秒。建议使用 release 包、不连接调试器；阻塞期间可连续点击屏幕，让系统更容易判定输入超时 ANR。")

        // 先把点击事件完整返回给主线程消息队列，让上面的提示文字有机会绘制到屏幕上。
        // 如果直接在 onClick 里阻塞，用户看不到状态更新，也更像一次普通长点击卡顿而不是后续主线程失联。
        mainHandler.postDelayed({
            blockMainThreadForStableAnr(STABLE_ANR_BLOCK_MS)
            binding.btnAnr.isEnabled = true
            showResult("ANR Demo 阻塞结束。稍等片刻后到 Sentry Issues / Explore Errors 搜索 ANR 或 Application Not Responding。")
        }, START_ANR_DELAY_MS)
    }

    private fun anrTest() {
        var i = 0
        while (i < 1_000_000_000) {
            i++
            SystemClock.sleep(100)
            println("打印次数 =>>> ${i}")
        }
    }

    private fun blockMainThreadForStableAnr(durationMs: Long) {
        val startTime = SystemClock.elapsedRealtime()
        var counter = 0L

        // 使用忙等而不是 sleep：sleep 虽然也会阻塞 Looper，但忙等能在 Perfetto/Profile 中明显显示
        // 主线程持续占用 CPU，更适合作为卡顿/ANR 分析示例。
        while (SystemClock.elapsedRealtime() - startTime < durationMs) {
            counter++
            anrBusyLoopGuard += kotlin.math.sqrt(counter.toDouble())
            if (counter % 100_000L == 0L) {
                Thread.yield()
            }
        }
    }

    private fun showResult(message: String) {
        binding.tvSentryResult.text = message
    }

    companion object {
        private const val START_ANR_DELAY_MS = 300L
        private const val STABLE_ANR_BLOCK_MS = 15_000L
    }
}
