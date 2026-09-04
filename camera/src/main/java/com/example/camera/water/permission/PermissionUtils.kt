package com.example.camera.water.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

object PermissionUtils {

    fun hasAnyLocationPermission(context: Context?): Boolean {
        context ?: return false
        return hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}