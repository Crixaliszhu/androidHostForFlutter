package com.example.hybriddemo.storage.mmkv

import androidx.annotation.Keep
import com.example.local_mmkv.MMKVFactory
import com.example.local_mmkv.annotation.MethodDelete
import com.example.local_mmkv.annotation.MethodGet
import com.example.local_mmkv.annotation.MethodSave
import com.example.local_mmkv.annotation.PartKey
import com.example.local_mmkv.annotation.Value
import com.example.local_mmkv.annotation.ValueDefault

@Keep
interface IRecruitDraftKv {
    companion object {
        private const val RECRUIT_KEY = "recruit_history_key"
        private const val RECRUIT_ID_KEY = "recruit_history_id"
        private const val RECRUIT_TITLE_KEY = "recruit_history_title"

        fun create(): IRecruitDraftKv {
            return MMKVFactory.createKeyOperator(IRecruitDraftKv::class.java)
        }

        fun saveID(id: String) {
            create().save(RECRUIT_KEY, RECRUIT_ID_KEY, id)
        }

        fun saveTitle(title: String) {
            create().save(RECRUIT_KEY, RECRUIT_TITLE_KEY, title)
        }

        fun getId(def: String? = null): String? {
            return create().get(RECRUIT_KEY, RECRUIT_ID_KEY, def)
        }

        fun getTitle(def: String? = null): String? {
            return create().get(RECRUIT_KEY, RECRUIT_TITLE_KEY, def)
        }

        fun clearDraft() {
            create().delete(RECRUIT_KEY, RECRUIT_ID_KEY)
            create().delete(RECRUIT_KEY, RECRUIT_TITLE_KEY)
        }
    }

    @MethodSave
    fun save(
        @PartKey recruitKey: String = RECRUIT_KEY,
        @PartKey id: String,
        @Value value: String
    )

    @MethodGet
    fun get(
        @PartKey recruitKey: String = RECRUIT_KEY,
        @PartKey title: String,
        @ValueDefault defValue: String? = ""
    ): String?

    @MethodDelete
    fun delete(@PartKey key: String = RECRUIT_KEY, @PartKey title: String)
}