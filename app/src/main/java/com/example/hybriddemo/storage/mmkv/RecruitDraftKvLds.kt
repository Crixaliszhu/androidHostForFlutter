package com.example.hybriddemo.storage.mmkv

class RecruitDraftKvLds(
    private val kvStore: KvStore,
) {
    fun saveLastDraft(id: String, title: String) {
        kvStore.putString(RecruitDraftKvKeys.LAST_DRAFT_ID, id)
        kvStore.putString(RecruitDraftKvKeys.LAST_DRAFT_TITLE, title)
    }

    fun readLastDraft(): String {
        val id = kvStore.getString(RecruitDraftKvKeys.LAST_DRAFT_ID, "none")
        val title = kvStore.getString(RecruitDraftKvKeys.LAST_DRAFT_TITLE, "empty")
        return "$id / $title"
    }

    fun clearLastDraft() {
        kvStore.remove(RecruitDraftKvKeys.LAST_DRAFT_ID)
        kvStore.remove(RecruitDraftKvKeys.LAST_DRAFT_TITLE)
    }
}
