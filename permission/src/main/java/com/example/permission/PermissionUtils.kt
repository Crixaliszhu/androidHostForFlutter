package com.example.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionUtils {

    /**
     * 权限检查
     */
    fun isGranted(context: Context, permission: String?): Boolean {
        permission ?: return false
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}