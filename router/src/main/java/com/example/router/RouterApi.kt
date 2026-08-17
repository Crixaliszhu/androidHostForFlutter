package com.example.router

import com.alibaba.android.arouter.launcher.ARouter

object RouterApi {
    fun <T> getByClass(clazz: Class<out T>): T? {
        return runCatching {
            ARouter.getInstance().navigation(clazz)
        }.getOrNull()
    }
}
