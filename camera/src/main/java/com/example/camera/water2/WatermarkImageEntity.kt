package com.example.camera.water2

import androidx.annotation.Keep

@Keep
data class WatermarkImageEntity(
    val jpegBytes: ByteArray,
    val jpegOrientation: Int,
    val timeText: String,
    val locationText: String,
) {
}