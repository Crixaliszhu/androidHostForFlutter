package com.example.hybriddemo.storage.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RecruitHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StorageDemoDatabase : RoomDatabase() {
    abstract fun recruitHistoryDao(): RecruitHistoryDao
}
