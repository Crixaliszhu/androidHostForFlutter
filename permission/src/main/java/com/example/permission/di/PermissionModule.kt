package com.example.permission.di

import com.example.permission.kv.PermissionReqRepo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 自动向宿主提供权限记录仓库，保持每次请求创建实例的行为。 */
@Module
@InstallIn(SingletonComponent::class)
object PermissionModule {
    @Provides
    fun providePermissionReqRepo(): PermissionReqRepo = PermissionReqRepo()
}
