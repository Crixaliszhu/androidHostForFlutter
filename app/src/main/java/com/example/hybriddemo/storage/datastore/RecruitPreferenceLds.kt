package com.example.hybriddemo.storage.datastore

import kotlinx.coroutines.flow.Flow

class RecruitPreferenceLds(
    private val preferenceStore: PreferenceStore,
) {
    fun observeAutoSaveDraft(): Flow<Boolean> {
        return preferenceStore.observe(RecruitPreferenceKeys.AutoSaveDraft)
    }

    suspend fun readAutoSaveDraft(): Boolean {
        return preferenceStore.read(RecruitPreferenceKeys.AutoSaveDraft)
    }

    suspend fun setAutoSaveDraft(enabled: Boolean) {
        preferenceStore.write(RecruitPreferenceKeys.AutoSaveDraft, enabled)
    }

    suspend fun resetAutoSaveDraft() {
        preferenceStore.remove(RecruitPreferenceKeys.AutoSaveDraft)
    }
}
