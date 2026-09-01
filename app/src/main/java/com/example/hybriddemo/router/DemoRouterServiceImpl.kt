package com.example.hybriddemo.router

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.alibaba.android.arouter.facade.Postcard
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.example.appapi.DemoRouterApiPaths
import com.example.appapi.IDemoRouterService

@Route(path = DemoRouterApiPaths.DEMO_ROUTER_SERVICE)
class DemoRouterServiceImpl : IDemoRouterService {
    override fun openMain(context: Context) = openPath(context, DemoRouterPaths.MAIN)
    override fun openHistoryDataBinding(context: Context) = openPath(context, DemoRouterPaths.HISTORY_DATA_BINDING)
    override fun openHistoryViewBinding(context: Context) = openPath(context, DemoRouterPaths.HISTORY_VIEW_BINDING)
    override fun openHistoryCompose(context: Context) = openPath(context, DemoRouterPaths.HISTORY_COMPOSE)
    override fun openFlowCompose(context: Context) = openPath(context, DemoRouterPaths.FLOW_COMPOSE)
    override fun openStorageBestPractice(context: Context) = openPath(context, DemoRouterPaths.STORAGE_BEST_PRACTICE)
    override fun openXBusMain(context: Context) = openPath(context, DemoRouterPaths.XBUS_MAIN)
    override fun openXBusSend(context: Context) = openPath(context, DemoRouterPaths.XBUS_SEND)
    override fun openService(context: Context) = openPath(context, DemoRouterPaths.SERVICE)
    override fun openWorkManager(context: Context) = openPath(context, DemoRouterPaths.WORK_MANAGER)
    override fun openPhotoPicker(context: Context) = openPath(context, DemoRouterPaths.PHOTO_PICKER)
    override fun openIpc(context: Context) = openPath(context, DemoRouterPaths.IPC)
    override fun openJankOnEnter(context: Context) = openPath(context, DemoRouterPaths.JANK_ON_ENTER)
    override fun openMemoryLeak(context: Context) = openPath(context, DemoRouterPaths.MEMORY_LEAK)
    override fun openJobSearchCollapse(context: Context) = openPath(context, DemoRouterPaths.JOB_SEARCH_COLLAPSE)
    override fun openPathAnimation(context: Context) = openPath(context, DemoRouterPaths.PATH_ANIMATION)
    override fun openSentry(context: Context) = openPath(context, DemoRouterPaths.SENTRY)
    override fun openAnrCases(context: Context) = openPath(context, DemoRouterPaths.ANR_CASES)
    override fun openSettings(context: Context) = openPath(context, DemoRouterPaths.SETTINGS)
    override fun openCameraDemo(context: Context) = openPath(context, DemoRouterPaths.CAMERA)

    override fun openPath(context: Context, pathOrUri: String, extras: Map<String, Any?>?) {
        build(pathOrUri)
            .applyExtras(extras)
            .navigation(context)
    }

    override fun openPathForResult(
        activity: Activity,
        pathOrUri: String,
        requestCode: Int,
        extras: Map<String, Any?>?,
    ) {
        build(pathOrUri)
            .applyExtras(extras)
            .navigation(activity, requestCode)
    }

    override fun init(context: Context?) = Unit

    private fun build(pathOrUri: String): Postcard {
        return if (pathOrUri.contains("://")) {
            ARouter.getInstance().build(Uri.parse(pathOrUri))
        } else {
            ARouter.getInstance().build(pathOrUri.normalizedPath())
        }
    }

    private fun String.normalizedPath(): String {
        return if (startsWith("/")) this else "/$this"
    }

    private fun Postcard.applyExtras(extras: Map<String, Any?>?): Postcard {
        extras.orEmpty().forEach { (key, value) ->
            when (value) {
                null -> Unit
                is Boolean -> withBoolean(key, value)
                is Int -> withInt(key, value)
                is Long -> withLong(key, value)
                is Float -> withFloat(key, value)
                is Double -> withDouble(key, value)
                is String -> withString(key, value)
                is Bundle -> withBundle(key, value)
                else -> withString(key, value.toString())
            }
        }
        return this
    }
}
