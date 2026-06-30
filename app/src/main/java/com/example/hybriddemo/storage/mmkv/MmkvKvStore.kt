package com.example.hybriddemo.storage.mmkv

import com.tencent.mmkv.MMKV

class MmkvKvStore(
    namespace: String,
) : KvStore {

    private val mmkv: MMKV = MMKV.mmkvWithID(namespace)

    override fun putString(key: String, value: String): Boolean = mmkv.encode(key, value)

    override fun getString(key: String, defaultValue: String?): String? {
        return mmkv.decodeString(key, defaultValue)
    }

    override fun putInt(key: String, value: Int): Boolean = mmkv.encode(key, value)

    override fun getInt(key: String, defaultValue: Int): Int = mmkv.decodeInt(key, defaultValue)

    override fun putLong(key: String, value: Long): Boolean = mmkv.encode(key, value)

    override fun getLong(key: String, defaultValue: Long): Long = mmkv.decodeLong(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean): Boolean = mmkv.encode(key, value)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return mmkv.decodeBool(key, defaultValue)
    }

    override fun putFloat(key: String, value: Float): Boolean = mmkv.encode(key, value)

    override fun getFloat(key: String, defaultValue: Float): Float = mmkv.decodeFloat(key, defaultValue)

    override fun putDouble(key: String, value: Double): Boolean = mmkv.encode(key, value)

    override fun getDouble(key: String, defaultValue: Double): Double = mmkv.decodeDouble(key, defaultValue)

    override fun putBytes(key: String, value: ByteArray): Boolean = mmkv.encode(key, value)

    override fun getBytes(key: String, defaultValue: ByteArray?): ByteArray? {
        return mmkv.decodeBytes(key, defaultValue)
    }

    override fun remove(key: String) {
        mmkv.removeValueForKey(key)
    }

    override fun clear() {
        mmkv.clearAll()
    }
}
