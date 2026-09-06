package com.example.permission.kv

class PermissionReqRepo {

    fun isRequested(permission: String): Boolean {
        return IPermissionKV.isRequested(permission)
    }

    fun updateToRequested(permission: String) {
        IPermissionKV.updateToRequested(permission)
    }
}