package com.example.router

import android.util.Log
import com.alibaba.android.arouter.launcher.ARouter

object RouterApi {
    private const val TAG = "RouterApi"

    fun <T> getByClass(clazz: Class<out T>): T? {
        return runCatching {
            ARouter.getInstance().navigation(clazz)
        }.onFailure {
            Log.e(TAG, "Get router service by class failed: ${clazz.name}", it)
        }.getOrNull()
    }

    fun <T> getByPath(path: String, clazz: Class<out T>): T? {
        return runCatching {
            val service = ARouter.getInstance().build(path).navigation()
            if (clazz.isInstance(service)) clazz.cast(service) else null
        }.onFailure {
            Log.e(TAG, "Get router service by path failed: $path, service=${clazz.name}", it)
        }.getOrNull()
    }
}
