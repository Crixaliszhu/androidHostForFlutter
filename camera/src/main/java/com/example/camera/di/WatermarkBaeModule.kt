package com.example.camera.di

import com.example.camera.water2.rep.IWatermark2Rep
import com.example.camera.water2.rep.WatermarkRepImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WatermarkBaeModule {

    @Binds
    abstract fun bindIWatermarkRep(impl: WatermarkRepImpl): IWatermark2Rep
}