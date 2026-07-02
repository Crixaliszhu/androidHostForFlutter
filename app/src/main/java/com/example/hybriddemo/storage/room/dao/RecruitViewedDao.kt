package com.example.hybriddemo.storage.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.hybriddemo.storage.room.table.RecruitmentHistoryViewedEntity

/**
 * 历史发布职位被查看，被浏览，被关注数据
 */
@Dao
internal interface RecruitViewedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(list: MutableList<RecruitmentHistoryViewedEntity>)

    @Query("select * from recruit_viewed_browse where id=:id")
    suspend fun queryById(id: String): RecruitmentHistoryViewedEntity?

    @Query("delete from recruit_viewed_browse where id=:id")
    suspend fun deleteById(id: String)

    @Query("delete from recruit_viewed_browse")
    suspend fun clear()
}