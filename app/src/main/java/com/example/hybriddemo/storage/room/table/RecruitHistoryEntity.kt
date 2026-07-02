package com.example.hybriddemo.storage.room.table

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 历史发布信息
 */
@Keep
@Entity(tableName = "recruit_history")
data class RecruitHistoryEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val city: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

@Keep
@Entity(tableName = "recruit_viewed_browse")
data class RecruitmentHistoryViewedEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "browse_count")
    val browseCount: Long,
    @ColumnInfo(name = "focus_count")
    val focusCount: Long,
    @ColumnInfo(name = "contact_count")
    val contactCount: Long,
)


