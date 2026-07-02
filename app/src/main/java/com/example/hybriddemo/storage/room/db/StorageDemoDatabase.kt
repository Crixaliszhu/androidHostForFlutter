package com.example.hybriddemo.storage.room.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.hybriddemo.storage.room.dao.RecruitHistoryDao
import com.example.hybriddemo.storage.room.dao.RecruitViewedDao
import com.example.hybriddemo.storage.room.table.RecruitHistoryEntity
import com.example.hybriddemo.storage.room.table.RecruitmentHistoryViewedEntity

@Database(
    entities = [RecruitHistoryEntity::class, RecruitmentHistoryViewedEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(value = [CConverters::class])
abstract class StorageDemoDatabase : RoomDatabase() {
    internal abstract fun recruitHistoryDao(): RecruitHistoryDao
    internal abstract fun recruitHistoryViewedDao(): RecruitViewedDao
}


/**
 * 数据库版本 1 到 2 的升级：
 * 1. recruit_history 表：列名 updatedAt -> updated_at（v1 无 @ColumnInfo，列名为驼峰 updatedAt）
 * 2. 新增 recruit_viewed_browse 表
 *
 * 说明：改列名未用 ALTER TABLE RENAME COLUMN，因为它需要 SQLite 3.25+，
 * 而 minSdk=24 的低版本机型内置 SQLite 版本过低不支持。改用
 * 「建新表 -> 拷数据 -> 删旧表 -> 重命名」的通用方案，全版本安全。
 *
 * 建表语句需与 Room 为实体生成的 schema 完全一致，
 * 否则 Room 启动时做完整性校验会抛 IllegalStateException。
 * 可在 build 后到 app/schemas/.../2.json 里核对 createSql。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        //原因是你 minSdk = 24,而 RENAME COLUMN 需要 SQLite 3.25+,API 24~28 的内置 SQLite 版本不够,直接用会在老机型崩。这种重建方案全版本通用、更安全。新增 recruit_viewed_browse 表的逻辑也保留在同一个迁移里。
        /**
         * SQLite 3.25+ 支持 RENAME COLUMN(Android API 30+ 才保证内置该版本)
         *    database.execSQL("ALTER TABLE recruit_history RENAME COLUMN updatedAt TO updated_at")
         * }
         */
        // 1. recruit_history 列名 updatedAt -> updated_at
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `recruit_history_new` (" +
                    "`id` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`city` TEXT NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
        )
        database.execSQL(
            "INSERT INTO `recruit_history_new` (`id`, `title`, `city`, `updated_at`) " +
                    "SELECT `id`, `title`, `city`, `updatedAt` FROM `recruit_history`"
        )
        database.execSQL("DROP TABLE `recruit_history`")
        database.execSQL("ALTER TABLE `recruit_history_new` RENAME TO `recruit_history`")

        // 2. 新增 recruit_viewed_browse 表
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `recruit_viewed_browse` (" +
                    "`id` TEXT NOT NULL, " +
                    "`browse_count` INTEGER NOT NULL, " +
                    "`focus_count` INTEGER NOT NULL, " +
                    "`contact_count` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))"
        )
    }
}