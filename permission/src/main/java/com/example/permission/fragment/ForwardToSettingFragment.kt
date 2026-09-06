package com.example.permission.fragment

import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.permission.PermissionController
import com.example.permission.PermissionUtils
import com.example.permission.annotation.PermissionReqResultType
import com.example.permission.dialog.IPermissionReqDialog
import kotlin.properties.Delegates

class ForwardToSettingFragment : Fragment() {
    private var permissionList: List<String> by Delegates.notNull()
    private var callback: PermissionController.PermissionReqResultCallback? = null
    private val forwardToSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        callback?.invoke(
            permissionList.all { isGranted(it) },
            PermissionReqResultType.SYSTEM
        )
    }

    private fun isGranted(permission: String): Boolean {
        val granted = requireContext().let {
            kotlin.runCatching {
                PermissionUtils.isGranted(it, permission)
            }.getOrNull()
        } ?: false
        return granted
    }

    /**
     * 引导前往设置页
     */
    fun forwardToSettings(
        permissionReqDialog: IPermissionReqDialog?,
        manager: FragmentManager,
        permissionList: List<String>,
        callback: PermissionController.PermissionReqResultCallback?,
    ) {
        this.permissionList = permissionList
        this.callback = callback
    }
}