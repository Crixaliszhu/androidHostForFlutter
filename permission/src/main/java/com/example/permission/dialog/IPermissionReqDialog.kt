package com.example.permission.dialog

import android.content.Context
import androidx.fragment.app.FragmentManager

/**
 * 权限请求弹窗
 */
interface IPermissionReqDialog {

    /**
     * 权限申请提示弹窗
     */
    fun permissionReqHint(
        fragmentManager: FragmentManager?,
        negativeClick: (() -> Unit)? = null,
        positiveClick: (() -> Unit)? = null,
    )

    /**
     * 引导前往设置页弹窗
     */
    fun forward2Setting(
        context: Context?,
        fragmentManager: FragmentManager?,
        negativeClick: (() -> Unit)? = null,
        positiveClick: (() -> Unit)? = null,
    )
}