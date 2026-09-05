package com.example.local_mmkv

import java.util.concurrent.ConcurrentHashMap

object MMKVFactory {
    /**
     * 缓存kv对象
     */
    private val keyCreatorCache = ConcurrentHashMap<Class<*>, Any?>()

    /**
     * 创建kv操作
     */
    fun <T> createKeyOperator(service: Class<T>): T {
        val optObj = keyCreatorCache[service]
        return if (optObj == null) {
            val newObj = KeyCreator().create(service)
            keyCreatorCache[service] = newObj
            newObj
        } else {
            optObj as T
        }
    }
}