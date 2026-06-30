package com.example.hybriddemo.storage.room

import android.content.Context
import androidx.room.Room

object StorageDemoDatabaseProvider {
    private const val DB_NAME = "storage_demo.db"

    @Volatile
    private var instance: StorageDemoDatabase? = null

    fun get(context: Context): StorageDemoDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StorageDemoDatabase::class.java,
                DB_NAME,
            )
                .build()
                .also { instance = it }
        }
    }
}
