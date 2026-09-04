package com.example.camera.water.entity

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.core.graphics.drawable.DrawableCompat

/**
 * 水印相机的完整页面状态。
 *
 * Activity 只负责把这个状态渲染到 XML/DataBinding，业务变化统一由 ViewModel reduce。
 */
@Keep
data class WaterCameraUiState(
    val timedText: String = "",
    val locationText: String = DEFAULT_LOCATION,
    val statusText: String = "",
    val reviewing: Boolean = false,
    val busy: Boolean = false,
    val previewBitmap: Bitmap? = null,
) {
    fun statusEnable(): Boolean = statusText.isNotBlank()

    companion object {
        const val DEFAULT_LOCATION = "我在这里"
    }
}
