package com.example.hybriddemo.storage.mmkv

import android.util.Log

class RecruitDraftKvLds(
) {
    fun saveLastDraft(id: String, title: String) {
        Log.e("saveLastDraft", "id = ${id}, title = ${title}")
        IRecruitDraftKv.saveID(id)
        IRecruitDraftKv.saveTitle(title)
    }

    fun readLastDraft(): String {
        val id = IRecruitDraftKv.getId("none")
        val title = IRecruitDraftKv.getTitle("empty")
        return "$id / $title"
    }

    fun clearLastDraft() {
        IRecruitDraftKv.clearDraft()
    }
}
