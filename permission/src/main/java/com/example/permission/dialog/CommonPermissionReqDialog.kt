package com.example.permission.dialog

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.example.widget.titlebar.dialog.CommonDialog2
import javax.inject.Inject

class CommonPermissionReqDialog @Inject constructor(): IPermissionReqDialog {
    override fun permissionReqHint(
        fragmentManager: FragmentManager?,
        negativeClick: (() -> Unit)?,
        positiveClick: (() -> Unit)?
    ) {
        fragmentManager ?: return
        CommonDialog2.show(
            fragmentManager,
            content = "通用权限请求提示弹窗内容",
            negativeClick = negativeClick,
            positiveClick = positiveClick
        )
    }

    override fun forward2Setting(
        context: Context?,
        fragmentManager: FragmentManager?,
        negativeClick: (() -> Unit)?,
        positiveClick: (() -> Unit)?
    ) {
        fragmentManager ?: return
        context ?: return
        CommonDialog2.show(
            fragmentManager,
            content = "通用权限引导设置页弹窗内容",
            negativeClick = negativeClick,
            positiveClick = positiveClick
        )
    }

}