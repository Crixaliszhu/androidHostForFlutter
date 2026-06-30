package com.example.hybriddemo.storage.room

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecruitHistoryRepositoryTest {

    @Test
    fun saveHistory_upsertsBusinessEntity() = runTest {
        val dao = FakeRecruitHistoryDao()
        val repository = RecruitHistoryRepository(dao)

        repository.saveHistory(id = "history_1", title = "招聘水电工", city = "重庆")

        val entity = dao.items.single()
        assertEquals("history_1", entity.id)
        assertEquals("招聘水电工", entity.title)
        assertEquals("重庆", entity.city)
    }

    @Test
    fun readSummary_returnsReadableBusinessText() = runTest {
        val dao = FakeRecruitHistoryDao()
        val repository = RecruitHistoryRepository(dao)
        repository.saveHistory(id = "history_2", title = "招聘泥瓦工", city = "成都")

        assertEquals("history_2 / 招聘泥瓦工 / 成都", repository.readSummary())
    }

    private class FakeRecruitHistoryDao : RecruitHistoryDao {
        val items = mutableListOf<RecruitHistoryEntity>()

        override fun observeAll(): Flow<List<RecruitHistoryEntity>> = flowOf(items)

        override suspend fun queryAll(): List<RecruitHistoryEntity> {
            return items.sortedByDescending { it.updatedAt }
        }

        override suspend fun upsert(entity: RecruitHistoryEntity) {
            items.removeAll { it.id == entity.id }
            items.add(entity)
        }

        override suspend fun deleteById(id: String) {
            items.removeAll { it.id == id }
        }

        override suspend fun clear() {
            items.clear()
        }
    }
}
