package com.example.hybriddemo.storage.mmkv

interface KvStore {
    fun putString(key: String, value: String): Boolean
    fun getString(key: String, defaultValue: String? = null): String?
    fun putInt(key: String, value: Int): Boolean
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun putLong(key: String, value: Long): Boolean
    fun getLong(key: String, defaultValue: Long = 0L): Long
    fun putBoolean(key: String, value: Boolean): Boolean
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun putFloat(key: String, value: Float): Boolean
    fun getFloat(key: String, defaultValue: Float = 0f): Float
    fun putDouble(key: String, value: Double): Boolean
    fun getDouble(key: String, defaultValue: Double = 0.0): Double
    fun putBytes(key: String, value: ByteArray): Boolean
    fun getBytes(key: String, defaultValue: ByteArray? = null): ByteArray?
    fun remove(key: String)
    fun clear()
}
