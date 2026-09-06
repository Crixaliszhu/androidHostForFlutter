package com.example.hybriddemo.di

import android.content.Context
import com.example.hybriddemo.storage.datastore.DataStorePreferenceStore
import com.example.hybriddemo.storage.datastore.PreferenceStore
import com.example.hybriddemo.storage.datastore.RecruitPreferenceLds
import com.example.hybriddemo.storage.mmkv.RecruitDraftKvLds
import com.example.hybriddemo.storage.room.RecruitHistoryRepository
import com.example.hybriddemo.storage.room.StorageDemoDatabaseProvider
import com.example.hybriddemo.storage.room.db.StorageDemoDatabase
import com.example.hybriddemo.storage.room.dao.RecruitHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 提供存储示例依赖，由 Hilt 自动收集，无需宿主手动注册。 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    fun provideDraftLds(): RecruitDraftKvLds = RecruitDraftKvLds()

    // 复用同一文件的 DataStore，避免多个实例并发操作相同文件。
    @Provides
    @Singleton
    fun providePreferenceStore(@ApplicationContext context: Context): PreferenceStore =
        DataStorePreferenceStore(context, "demo_recruit_preferences")

    @Provides
    fun providePreferenceLds(store: PreferenceStore): RecruitPreferenceLds = RecruitPreferenceLds(store)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StorageDemoDatabase =
        StorageDemoDatabaseProvider.get(context)

    @Provides
    @Singleton
    fun provideHistoryDao(database: StorageDemoDatabase): RecruitHistoryDao = database.recruitHistoryDao()

    @Provides
    fun provideHistoryRepository(dao: RecruitHistoryDao): RecruitHistoryRepository = RecruitHistoryRepository(dao)
}
