package com.example.hybriddemo.sentry

import android.app.Application
import com.example.hybriddemo.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

object SentryInitializer {
    fun init(application: Application) {
        SentryAndroid.init(application) { options ->
            // DSN 为空时禁用 Sentry。这样仓库可以保留完整接入代码，
            // 但没有配置 local.properties/CI Secret 的机器不会因为缺少 DSN 启动失败。
            options.dsn = BuildConfig.SENTRY_DSN
            options.isEnabled = BuildConfig.SENTRY_DSN.isNotBlank()
            // environment 用于区分 debug-local、production 等环境，后台告警和查询都依赖它过滤数据。
            options.environment = BuildConfig.SENTRY_ENVIRONMENT
            // release 必须和发包版本一一对应。R8 mapping 上传也会按这个 release 关联，
            // 如果这里和构建产物版本不一致，后台可能无法反混淆线上崩溃栈。
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isDebug = BuildConfig.SENTRY_DEBUG
            // tracesSampleRate 控制事务/页面加载/手动 span 的采样比例；线上不建议 100%。
            options.tracesSampleRate = BuildConfig.SENTRY_TRACES_SAMPLE_RATE
            // profilesSampleRate 控制 CPU Profile 的采样比例，它只会作用在已被 Trace 采样的事务上。
            options.profilesSampleRate = BuildConfig.SENTRY_PROFILES_SAMPLE_RATE
            // 截图和 ViewHierarchy 对定位 UI 问题很有用，但可能包含用户隐私，线上默认由构建配置关闭。
            options.isAttachScreenshot = BuildConfig.SENTRY_ATTACH_SCREENSHOT
            options.isAttachViewHierarchy = BuildConfig.SENTRY_ATTACH_VIEW_HIERARCHY
            // Session Tracking 是 Crash Free Sessions / Crash Free Users 指标的数据来源。
            options.isEnableAutoSessionTracking = true
            // Breadcrumbs 记录 Activity、App 生命周期和系统事件，崩溃详情页可以看到出错前的行为轨迹。
            options.isEnableActivityLifecycleBreadcrumbs = true
            options.isEnableAppLifecycleBreadcrumbs = true
            options.isEnableSystemEventBreadcrumbs = true
            // ANR 监控由 Sentry 的 watchdog 检测主线程长时间无响应；debug 是否上报由构建类型决定。
            options.isAnrEnabled = true
            options.isAnrReportInDebug = BuildConfig.SENTRY_REPORT_ANR_IN_DEBUG
            // 附带 ANR 线程栈可以直接看到主线程当时卡在什么调用链上。
            options.isAttachAnrThreadDump = true
            options.anrTimeoutIntervalMillis = 5_000
            // beforeSend 是事件发出前最后一道处理口，可在这里打公共 tag、脱敏或丢弃不需要的事件。
            options.beforeSend = SentryOptions.BeforeSendCallback { event: SentryEvent, _ ->
                event.setTag("demo_project", "AndroidHostForFlutter")
                event
            }
        }

        Sentry.addBreadcrumb(
            Breadcrumb.info("Sentry initialized").apply {
                category = "demo.init"
                level = SentryLevel.INFO
                setData("dsn_configured", BuildConfig.SENTRY_DSN.isNotBlank())
            }
        )
    }
}
