package com.example.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.pm.PermissionInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.example.permission.annotation.PermissionReqResultType
import com.example.permission.dialog.CommonPermissionReqDialog
import com.example.permission.dialog.IPermissionReqDialog
import com.example.permission.kv.PermissionReqRepo
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.properties.Delegates

class PermissionController {
    private var permissionList: List<String> by Delegates.notNull()
    private var resultCallback: PermissionReqResultCallback? = null
    private var fragment: Fragment? = null
    private var activity: FragmentActivity? = null
    private var manager: FragmentManager? = null
    private val permissionReqRepo = PermissionReqRepo()
    private val permissionReqDialogMap: Map<String, IPermissionReqDialog>
    private var forwardToSettingsLauncher: ActivityResultLauncher<Intent>? = null
    private var permissionRequestLauncher: ActivityResultLauncher<Array<String>>? = null

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PermissionReqMapEntryProvider {
        fun providerPermissionReqDialogMap(): Map<String, IPermissionReqDialog>
    }

    internal constructor(
        fragment: Fragment,
        permissionReqDialogMap: Map<String, IPermissionReqDialog>
    ) {
        this.fragment = fragment
        this.manager = fragment.childFragmentManager
        this.permissionReqDialogMap = permissionReqDialogMap
        createLauncher()
        createPermissionLauncher()
    }

    internal constructor(
        activity: FragmentActivity,
        permissionReqDialogMap: Map<String, IPermissionReqDialog>
    ) {
        this.activity = activity
        this.manager = activity.supportFragmentManager
        this.permissionReqDialogMap = permissionReqDialogMap
        createLauncher()
        createPermissionLauncher()
    }

    private fun createLauncher() {
        forwardToSettingsLauncher = (fragment ?: activity)?.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            Log.e(TAG, "设置页打开 结果接受--")
            resultCallback?.invoke(
                permissionList.all { isGranted(it) },
                PermissionReqResultType.SETTING
            )
        }
    }

    private fun createPermissionLauncher() {
        permissionRequestLauncher = (fragment ?: activity)?.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            Log.e(TAG, "权限请求 结果接受--")
            resultCallback?.invoke(
                permissionList.all { isGranted(it) },
                PermissionReqResultType.SYSTEM
            )
        }
    }

    companion object {

        private const val TAG = "PermissionController"

        fun newInstance(activity: FragmentActivity): PermissionController {
            val process = EntryPointAccessors.fromApplication(
                activity,
                PermissionReqMapEntryProvider::class.java
            )
            return PermissionController(activity, process.providerPermissionReqDialogMap())
        }

        fun newInstance(fragment: Fragment): PermissionController {
            val process = fragment.context?.let {
                EntryPointAccessors.fromApplication(
                    it,
                    PermissionReqMapEntryProvider::class.java
                )
            }
            return PermissionController(
                fragment,
                process?.providerPermissionReqDialogMap() ?: emptyMap()
            )
        }
    }

    fun request(permission: String, callback: PermissionReqResultCallback) {
        request(arrayListOf(permission), callback)
    }

    fun request(permissionList: List<String>, callback: PermissionReqResultCallback) {
        if (permissionList.isEmpty()) return
        this.permissionList = permissionList
        this.resultCallback = callback
        val notGranted = permissionList.filter { !isGranted(it) }
        //有权限不进入申请流程
        if (notGranted.isEmpty()) {
            Log.e(TAG, "有权限不进入申请流程--")
            callback(true, PermissionReqResultType.GRANTED)
            return
        }

        val allShouldHint = notGranted.all {
            shouldShowHint(it)
        }
        val permissionReqDialog = getPermissionReqDialog()
        if (allShouldHint) {
            // 展示权限请求提醒弹窗
            Log.e(TAG, "展示权限请求提醒弹窗")
            permissionReqDialog?.permissionReqHint(
                fragmentManager = manager,
                negativeClick = {
                    callback.invoke(false, PermissionReqResultType.NOTICE_NO)
                },
                positiveClick = {
                    // 发起权限请求
                    Log.e(TAG, "发起权限请求")
                    permissionRequestLauncher?.launch(permissionList.toTypedArray())
                },
            )
        } else {
            // 展示引导去设置页弹窗
            Log.e(TAG, "展示引导去设置页弹窗")
            permissionReqDialog?.forward2Setting(
                context = getContext(),
                fragmentManager = manager,
                negativeClick = {
                    callback.invoke(false, PermissionReqResultType.NOTICE_NO)
                },
                positiveClick = {
                    kotlin.runCatching {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", getContext()?.packageName, null)
                        }
                    }.getOrNull()?.also {
                        forwardToSettingsLauncher?.launch(it)
                    }
                },
            )
        }
    }

    private fun getPermissionReqDialog(): IPermissionReqDialog? {
        if (permissionList.isEmpty()) return null
        val key = permissionList.joinToString(",")
        return permissionReqDialogMap.getOrElse(key) {
            Log.e(TAG, "未找到可用的弹窗")
            CommonPermissionReqDialog()
        }
    }

    private fun getContext(): Context? {
        if (activity != null) {
            return activity
        }
        return fragment?.context
    }

    private fun getActivity(): Activity? {
        return fragment?.activity ?: activity
    }


    /**
     * 是否应该弹出提示弹窗：请求权限提示
     * 用户点击拒绝不再提醒后，展示引导至设置页弹窗
     */
    private fun shouldShowHint(permission: String): Boolean {
        if (permission.isBlank()) return false
        // android 13以下不支持通知权限动态申请
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            && permission == Manifest.permission.POST_NOTIFICATIONS
        ) {
            return false
        }
        // 是否可以再显示提示弹窗
        val canAgain = getActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
        } ?: false
        val requested = permissionReqRepo.isRequested(permission)
        return !requested || canAgain
    }

    /**
     * 权限判断
     */
    private fun isGranted(permission: String): Boolean {
        if (permission.isEmpty()) return false
        val granted = getContext()?.let {
            kotlin.runCatching {
                PermissionUtils.isGranted(it, permission)
            }.getOrNull()
        } ?: false
        return granted
    }


    /**
     * 权限请求结果回调
     */
    fun interface PermissionReqResultCallback {
        operator fun invoke(granted: Boolean, @PermissionReqResultType type: Int)
    }
}