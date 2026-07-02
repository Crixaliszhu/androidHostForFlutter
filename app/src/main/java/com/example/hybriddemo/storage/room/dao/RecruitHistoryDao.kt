package com.example.hybriddemo.storage.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.hybriddemo.storage.room.table.RecruitHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 历史发布职位数据
 */
@Dao
interface RecruitHistoryDao {
    @Query("SELECT * FROM recruit_history ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<RecruitHistoryEntity>>

    @Query("SELECT * FROM recruit_history ORDER BY updated_at DESC")
    suspend fun queryAll(): List<RecruitHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecruitHistoryEntity)

    @Query("DELETE FROM recruit_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM recruit_history")
    suspend fun clear()
}
