package com.example.hybriddemo.storage.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recruit_history")
data class RecruitHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val city: String,
    val updatedAt: Long,
)
