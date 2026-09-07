package com.example.camera.water2

import androidx.annotation.Keep

/**
 * 用户动作，系统回调
 */
sealed interface Water2CameraUserIntent {
    data object CameraReady : Water2CameraUserIntent
    data object PageStarted : Water2CameraUserIntent
    data object PageStopped : Water2CameraUserIntent
    data object CameraCaptureUnavailable : Water2CameraUserIntent

    @Keep
    data class PageResumed(val hasCameraPermission: Boolean) : Water2CameraUserIntent

    @Keep
    data class PhotoCaptured(
        val jpegBytes: ByteArray,
        val jpegOrientation: Int,
    ) : Water2CameraUserIntent

    @Keep
    data class CameraError(val message: String) : Water2CameraUserIntent

    @Keep
    data class CameraPermissionResult(val granted: Boolean) : Water2CameraUserIntent

    @Keep
    data class LocationPermissionResult(val granted: Boolean) : Water2CameraUserIntent

    @Keep
    data class StoragePermissionResult(val granted: Boolean) : Water2CameraUserIntent
}