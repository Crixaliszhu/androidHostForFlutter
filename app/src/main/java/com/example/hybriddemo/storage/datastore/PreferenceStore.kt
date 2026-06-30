package com.example.hybriddemo.storage.datastore

import kotlinx.coroutines.flow.Flow

interface PreferenceStore {
    fun <T : Any> observe(key: PreferenceKey<T>): Flow<T>
    suspend fun <T : Any> read(key: PreferenceKey<T>): T
    suspend fun <T : Any> write(key: PreferenceKey<T>, value: T)
    suspend fun <T : Any> remove(key: PreferenceKey<T>)
    suspend fun clear()
}
