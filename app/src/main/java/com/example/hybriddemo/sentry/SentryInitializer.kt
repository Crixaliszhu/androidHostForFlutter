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
            options.dsn = BuildConfig.SENTRY_DSN
            options.isEnabled = BuildConfig.SENTRY_DSN.isNotBlank()
            options.environment = BuildConfig.SENTRY_ENVIRONMENT
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isDebug = BuildConfig.SENTRY_DEBUG
            options.tracesSampleRate = 1.0
            options.profilesSampleRate = 1.0
            options.isAttachScreenshot = true
            options.isAttachViewHierarchy = true
            options.isEnableAutoSessionTracking = true
            options.isEnableActivityLifecycleBreadcrumbs = true
            options.isEnableAppLifecycleBreadcrumbs = true
            options.isEnableSystemEventBreadcrumbs = true
            options.isAnrEnabled = true
            options.isAnrReportInDebug = true
            options.isAttachAnrThreadDump = true
            options.anrTimeoutIntervalMillis = 5_000
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
