package com.example.local_mmkv.core

import android.os.Parcelable
import android.util.Log
import com.tencent.mmkv.MMKV
import kotlin.reflect.KClass

object MMKVCore {
    const val TAG = "MMKVCore"

    fun put(mmkv: MMKV?, key: String?, value: Any?): Boolean {
        mmkv ?: return false
        if (key.isNullOrBlank() || value == null) return false
        return when (value) {
            is Boolean -> mmkv.encode(key, value)
            is Int -> mmkv.encode(key, value)
            is Long -> mmkv.encode(key, value)
            is Float -> mmkv.encode(key, value)
            is Double -> mmkv.encode(key, value)
            is String -> mmkv.encode(key, value)
            is ByteArray -> mmkv.encode(key, value)
            is Parcelable -> mmkv.encode(key, value)
            else -> {
                Log.e(TAG, "写入操作，不支持该数据类型")
                false
            }
        }
    }

    fun get(
        mmkv: MMKV?,
        key: String?,
        default: Any?,
        parcelableClazz: Class<Parcelable>?,
        returnType: KClass<*>
    ): Any? {
        if (key.isNullOrBlank()) {
            Log.e(TAG, "数据获取时， key不能为 kong")
            return default
        }
        mmkv ?: return default
        return when (returnType) {
            Boolean::class -> mmkv.decodeBool(key, (default as Boolean?) ?: false)
            Int::class -> mmkv.decodeInt(key, (default as Int?) ?: 0)
            Long::class -> mmkv.decodeLong(key, (default as Long?) ?: 0L)
            Float::class -> mmkv.decodeFloat(key, (default as Float?) ?: 0f)
            Double::class -> mmkv.decodeDouble(key, (default as Double?) ?: 0.0)
            String::class -> mmkv.decodeString(key, default as String?)
            ByteArray::class -> mmkv.decodeBytes(key, default as ByteArray?)
            Parcelable::class -> mmkv.decodeParcelable(key, parcelableClazz, default as Parcelable)
            else -> null
        } ?: default
    }

    fun deleteKey(mmkv: MMKV?, key: String?) {
        if (key.isNullOrBlank()) {
            Log.e(TAG, "要移除的key不能为 null!!")
            return
        }
        mmkv ?: return
        mmkv.removeValueForKey(key)
    }

    fun deleteKeys(mmkv: MMKV?, keys: Array<String>?) {
        if (keys.isNullOrEmpty()) {
            Log.e(TAG, "要移除的key集合不能为 null!!")
            return
        }
        mmkv ?: return
        mmkv.removeValuesForKeys(keys)
    }
}