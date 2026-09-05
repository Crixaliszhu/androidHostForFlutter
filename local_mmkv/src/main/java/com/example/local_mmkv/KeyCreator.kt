package com.example.local_mmkv

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class KeyCreator {

    fun <T> create(service: Class<T>): T {
        return Proxy.newProxyInstance(service.classLoader,
            arrayOf<Class<*>>(service),
            InvocationHandler { proxy, method, args ->
                try {
                    return@InvocationHandler MethodExecutorKt(method, args).execute()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@InvocationHandler null
                }
            }) as T
    }
}