package com.example.permission.kv

import androidx.annotation.Keep

@Keep
internal interface IPermissionKV {

    companion object{
        private fun create():IPermissionKV{
            return MmkvKvStore
        }
    }
}