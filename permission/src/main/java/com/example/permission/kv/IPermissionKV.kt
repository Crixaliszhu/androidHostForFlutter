package com.example.permission.kv

import androidx.annotation.Keep
import com.example.local_mmkv.MMKVFactory
import com.example.local_mmkv.annotation.MethodGet
import com.example.local_mmkv.annotation.MethodSave
import com.example.local_mmkv.annotation.PartKey
import com.example.local_mmkv.annotation.Value
import com.example.local_mmkv.annotation.ValueDefault

@Keep
internal interface IPermissionKV {

    companion object {
        private const val PERMISSION_REQ = "permission_key"

        private fun create(): IPermissionKV {
            return MMKVFactory.createKeyOperator(IPermissionKV::class.java)
        }

        /**
         * 权限是否申请过
         */
        fun isRequested(permission: String): Boolean {
            if (permission.isBlank()) {
                return false
            }
            return create().get(permission = permission) ?: false
        }

        /**
         * 更新到申请过
         */
        fun updateToRequested(permission: String) {
            if (permission.isBlank()) {
                return
            }
            create().save(permission = permission, value = true)
        }
    }

    @MethodSave
    fun save(
        @PartKey key: String = PERMISSION_REQ,
        @PartKey permission: String,
        @Value value: Boolean?
    )

    @MethodGet
    fun get(
        @PartKey key: String = PERMISSION_REQ,
        @PartKey permission: String,
        @ValueDefault defaultValue: Boolean? = null
    ): Boolean?
}