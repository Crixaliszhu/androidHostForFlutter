package com.example.hybriddemo.sentry

import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import com.example.hybriddemo.BuildConfig
import com.example.hybriddemo.databinding.ActivitySentryDemoBinding
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SpanStatus

class SentryDemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySentryDemoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySentryDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvSentryStatus.text = buildString {
            appendLine("Sentry 开发调试 Demo")
            appendLine("DSN configured: ${BuildConfig.SENTRY_DSN.isNotBlank()}")
            appendLine("Environment: ${BuildConfig.SENTRY_ENVIRONMENT}")
        }
        binding.tvSentryResult.text = "没有 DSN 时不会发到 Sentry 后台；配置 DSN 后点击按钮即可在 Issues / Performance / Profiling / ANR 中查看。"

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
            Sentry.addBreadcrumb(Breadcrumb.info("Start ANR demo"))
            SystemClock.sleep(8_000)
            showResult("阻塞结束。如果 Sentry ANR watchdog 触发，后台会出现 ANR 事件。")
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

    private fun showResult(message: String) {
        binding.tvSentryResult.text = message
    }
}
