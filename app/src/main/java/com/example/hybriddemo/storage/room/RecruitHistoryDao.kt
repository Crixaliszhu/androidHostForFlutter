package com.example.hybriddemo.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecruitHistoryDao {
    @Query("SELECT * FROM recruit_history ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RecruitHistoryEntity>>

    @Query("SELECT * FROM recruit_history ORDER BY updatedAt DESC")
    suspend fun queryAll(): List<RecruitHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecruitHistoryEntity)

    @Query("DELETE FROM recruit_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM recruit_history")
    suspend fun clear()
}
