package com.example.appapi

import android.app.Activity
import android.content.Context
import com.alibaba.android.arouter.facade.template.IProvider

interface IDemoRouterService : IProvider {
    fun openMain(context: Context)
    fun openHistoryDataBinding(context: Context)
    fun openHistoryViewBinding(context: Context)
    fun openHistoryCompose(context: Context)
    fun openFlowCompose(context: Context)
    fun openStorageBestPractice(context: Context)
    fun openXBusMain(context: Context)
    fun openXBusSend(context: Context)
    fun openService(context: Context)
    fun openWorkManager(context: Context)
    fun openPhotoPicker(context: Context)
    fun openIpc(context: Context)
    fun openJankOnEnter(context: Context)
    fun openMemoryLeak(context: Context)
    fun openJobSearchCollapse(context: Context)
    fun openPathAnimation(context: Context)
    fun openSentry(context: Context)
    fun openAnrCases(context: Context)
    fun openSettings(context: Context)
    fun openCameraDemo(context: Context)

    fun openPath(context: Context, pathOrUri: String, extras: Map<String, Any?>? = null)
    fun openPathForResult(
        activity: Activity,
        pathOrUri: String,
        requestCode: Int,
        extras: Map<String, Any?>? = null,
    )
}
