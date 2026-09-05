package com.example.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.pm.PermissionInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.example.permission.annotation.PermissionReqResultType
import kotlin.properties.Delegates

class PermissionController {
    private var permissionList: List<String> by Delegates.notNull()
    private var resultCallback: PermissionReqResultCallback? = null
    private var fragment: Fragment? = null
    private var activity: FragmentActivity? = null
    private var manager: FragmentManager? = null

    internal constructor(fragment: Fragment) {
        this.fragment = fragment
        this.manager = fragment.childFragmentManager
    }

    internal constructor(activity: FragmentActivity) {
        this.activity = activity
        this.manager = activity.supportFragmentManager
    }

    companion object {

        fun newInstance(activity: FragmentActivity): PermissionController {
            return PermissionController(activity)
        }

        fun newInstance(fragment: Fragment): PermissionController {
            return PermissionController(fragment)
        }
    }

    fun request(permission: String, callback: PermissionReqResultCallback) {
        request(arrayListOf(permission), callback)
    }

    private fun request(permissionList: List<String>, callback: PermissionReqResultCallback) {
        if (permissionList.isEmpty()) return
        this.permissionList = permissionList
        this.resultCallback = callback
        val notGranted = permissionList.filter { !isGranted(it) }
        //有权限不进入申请流程
        if (notGranted.isEmpty()) {
            callback(true, PermissionReqResultType.GRANTED)
            return
        }

        val allShouldHint = notGranted.all {
            shouldShowHint(it)
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
     * 是否应该弹出提示弹窗
     */
    private fun shouldShowHint(permission: String): Boolean {
        if (permission.isBlank()) return false
        // android 13以下不支持通知权限动态申请
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            && permission == Manifest.permission.POST_NOTIFICATIONS
        ) {
            return false
        }
        // 是否可以在显示提示弹窗
        val canAgain = getActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
        }?:false
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