package com.example.hybriddemo.storage.mmkv

import org.junit.Assert.assertEquals
import org.junit.Test

class RecruitDraftKvLdsTest {

    @Test
    fun saveLastDraft_writesOnlyBusinessKeys() {
        val store = FakeKvStore()
        val lds = RecruitDraftKvLds(store)

        lds.saveLastDraft(id = "draft_1", title = "招聘焊工")

        assertEquals("draft_1", store.values[RecruitDraftKvKeys.LAST_DRAFT_ID])
        assertEquals("招聘焊工", store.values[RecruitDraftKvKeys.LAST_DRAFT_TITLE])
        assertEquals("draft_1 / 招聘焊工", lds.readLastDraft())
    }

    @Test
    fun clearLastDraft_removesBusinessKeys() {
        val store = FakeKvStore()
        val lds = RecruitDraftKvLds(store)
        lds.saveLastDraft(id = "draft_2", title = "招聘木工")

        lds.clearLastDraft()

        assertEquals("none / empty", lds.readLastDraft())
    }

    private class FakeKvStore : KvStore {
        val values = mutableMapOf<String, Any>()

        override fun putString(key: String, value: String): Boolean {
            values[key] = value
            return true
        }

        override fun getString(key: String, defaultValue: String?): String? {
            return values[key] as? String ?: defaultValue
        }

        override fun putInt(key: String, value: Int): Boolean = put(key, value)
        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
        override fun putLong(key: String, value: Long): Boolean = put(key, value)
        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue
        override fun putBoolean(key: String, value: Boolean): Boolean = put(key, value)
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
        override fun putFloat(key: String, value: Float): Boolean = put(key, value)
        override fun getFloat(key: String, defaultValue: Float): Float = values[key] as? Float ?: defaultValue
        override fun putDouble(key: String, value: Double): Boolean = put(key, value)
        override fun getDouble(key: String, defaultValue: Double): Double = values[key] as? Double ?: defaultValue
        override fun putBytes(key: String, value: ByteArray): Boolean = put(key, value)
        override fun getBytes(key: String, defaultValue: ByteArray?): ByteArray? = values[key] as? ByteArray ?: defaultValue

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun clear() {
            values.clear()
        }

        private fun put(key: String, value: Any): Boolean {
            values[key] = value
            return true
        }
    }
}
