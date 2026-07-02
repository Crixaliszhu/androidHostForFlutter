package com.example.hybriddemo.storage.room

import android.content.Context
import androidx.room.Room
import com.example.hybriddemo.storage.room.db.MIGRATION_1_2
import com.example.hybriddemo.storage.room.db.StorageDemoDatabase

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
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
