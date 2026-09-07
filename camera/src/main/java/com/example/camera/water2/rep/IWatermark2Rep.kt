package com.example.camera.water2.rep

import android.content.Context
import android.graphics.Bitmap
import com.example.camera.water.location.LocationController
import com.example.camera.water.location.LocationUiState
import com.example.camera.water2.WatermarkImageEntity
import kotlinx.coroutines.flow.Flow

interface IWatermark2Rep {
    fun createWatermarkedBitmap(data: WatermarkImageEntity): Flow<Bitmap?>

    fun requestCurrentLocation(locationController: LocationController): Flow<LocationUiState?>

    /**
     * 图片保存
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, capturedAtMillis: Long): Flow<Boolean>
}