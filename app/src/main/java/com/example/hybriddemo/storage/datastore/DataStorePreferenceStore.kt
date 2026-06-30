package com.example.hybriddemo.storage.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class DataStorePreferenceStore(
    context: Context,
    storeName: String,
) : PreferenceStore {

    private val appContext = context.applicationContext
    private val dataStore: DataStore<Preferences> = stores.getOrPut(storeName) {
        PreferenceDataStoreFactory.create(
            produceFile = { appContext.preferencesDataStoreFile(storeName) },
        )
    }

    override fun <T : Any> observe(key: PreferenceKey<T>): Flow<T> {
        val preferenceKey = key.toPreferencesKey()
        return dataStore.data
            .map { preferences -> preferences[preferenceKey] ?: key.defaultValue }
            .catch { throwable ->
                Log.e(TAG, "observe failed, key=${key.name}", throwable)
                emit(key.defaultValue)
            }
    }

    override suspend fun <T : Any> read(key: PreferenceKey<T>): T {
        return observe(key).first()
    }

    override suspend fun <T : Any> write(key: PreferenceKey<T>, value: T) {
        withContext(Dispatchers.IO) {
            runCatching {
                val preferenceKey = key.toPreferencesKey()
                dataStore.edit { preferences ->
                    preferences[preferenceKey] = value
                }
            }.onFailure {
                Log.e(TAG, "write failed, key=${key.name}", it)
            }
        }
    }

    override suspend fun <T : Any> remove(key: PreferenceKey<T>) {
        withContext(Dispatchers.IO) {
            runCatching {
                dataStore.edit { preferences ->
                    preferences.remove(key.toPreferencesKey())
                }
            }.onFailure {
                Log.e(TAG, "remove failed, key=${key.name}", it)
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            runCatching {
                dataStore.edit { preferences -> preferences.clear() }
            }.onFailure {
                Log.e(TAG, "clear failed", it)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> PreferenceKey<T>.toPreferencesKey(): Preferences.Key<T> {
        return when (defaultValue) {
            is String -> stringPreferencesKey(name)
            is Int -> intPreferencesKey(name)
            is Long -> longPreferencesKey(name)
            is Boolean -> booleanPreferencesKey(name)
            is Float -> floatPreferencesKey(name)
            is Double -> doublePreferencesKey(name)
            else -> error("Unsupported DataStore type: ${defaultValue::class.java.name}")
        } as Preferences.Key<T>
    }

    private companion object {
        const val TAG = "DataStorePreferenceStore"
        val stores = ConcurrentHashMap<String, DataStore<Preferences>>()
    }
}
