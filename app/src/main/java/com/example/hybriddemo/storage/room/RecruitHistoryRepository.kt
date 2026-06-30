package com.example.hybriddemo.storage.room

import kotlinx.coroutines.flow.Flow

class RecruitHistoryRepository(
    private val dao: RecruitHistoryDao,
) {
    fun observeHistory(): Flow<List<RecruitHistoryEntity>> {
        return dao.observeAll()
    }

    suspend fun saveHistory(id: String, title: String, city: String) {
        dao.upsert(
            RecruitHistoryEntity(
                id = id,
                title = title,
                city = city,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun readSummary(): String {
        val histories = dao.queryAll()
        return histories.joinToString(separator = "\n") {
            "${it.id} / ${it.title} / ${it.city}"
        }.ifEmpty {
            "empty"
        }
    }

    suspend fun clear() {
        dao.clear()
    }
}
