package com.example.camera.water2.rep

import android.content.Context
import android.graphics.Bitmap
import com.example.camera.water.location.LocationController
import com.example.camera.water.location.LocationUiState
import com.example.camera.water.utils.WatermarkPhotoProcessor
import com.example.camera.water2.WatermarkImageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class WatermarkRepImpl @Inject constructor() : IWatermark2Rep {

    override fun createWatermarkedBitmap(data: WatermarkImageEntity): Flow<Bitmap?> {
        return flow {
            runCatching {
                WatermarkPhotoProcessor.createWatermarkedBitmap(
                    jpegBytes = data.jpegBytes,
                    jpegOrientation = data.jpegOrientation,
                    timeText = data.timeText,
                    locationText = data.locationText,
                )
            }.onSuccess { bitmap ->
                emit(bitmap)
            }.onFailure {
                emit(null)
            }
        }.flowOn(Dispatchers.Default)
    }

    override fun requestCurrentLocation(locationController: LocationController): Flow<LocationUiState?> {
        return flow {
            val result = locationController.requestCurrentLocation()
            emit(result)
        }
    }

    override fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        capturedAtMillis: Long
    ): Flow<Boolean> {
        return flow {
            runCatching {
                WatermarkPhotoProcessor.saveToGallery(context, bitmap, capturedAtMillis)
            }.onSuccess {
                emit(true)
            }.onFailure {
                emit(false)
            }
        }
    }
}