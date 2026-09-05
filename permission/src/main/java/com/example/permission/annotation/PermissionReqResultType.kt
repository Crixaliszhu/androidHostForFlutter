package com.example.permission.annotation

import androidx.annotation.IntDef
import com.example.permission.annotation.PermissionReqResultType.Companion.GRANTED
import com.example.permission.annotation.PermissionReqResultType.Companion.NOTICE_NO
import com.example.permission.annotation.PermissionReqResultType.Companion.SETTING
import com.example.permission.annotation.PermissionReqResultType.Companion.SYSTEM

@IntDef(GRANTED, NOTICE_NO, SYSTEM, SETTING)
@Retention(AnnotationRetention.SOURCE)
internal annotation class PermissionReqResultType {
    companion object {
        /**
         * 有权限，未进入申请流程
         */
        const val GRANTED = 1

        /**
         * 提示弹窗给的结果
         */
        const val NOTICE_NO = 2

        /**
         * 系统弹窗给的权限结果
         */
        const val SYSTEM = 3

        /**
         * 系统设置页给的结果
         */
        const val SETTING = 4
    }
}