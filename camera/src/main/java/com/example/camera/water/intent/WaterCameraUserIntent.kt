package com.example.camera.water.intent

import androidx.annotation.Keep

/**
 * 用户动作、系统回调和页面生命周期统一收敛成 Intent。
 *
 * ViewModel 只处理这些输入，Activity 不直接修改页面状态，这就是本页 MVI 的入口。
 */
sealed interface WaterCameraUserIntent {
    data object PageStarted : WaterCameraUserIntent
    data object PageStopped : WaterCameraUserIntent

    @Keep
    data class PageResumed(val hasCameraPermission: Boolean) : WaterCameraUserIntent

    data object BackClicked : WaterCameraUserIntent
    data object LocateClicked : WaterCameraUserIntent
    data object CaptureClicked : WaterCameraUserIntent
    data object DiscardClicked : WaterCameraUserIntent
    data object SaveClicked : WaterCameraUserIntent

    @Keep
    data class CameraPermissionResult(val granted: Boolean) : WaterCameraUserIntent

    @Keep
    data class LocationPermissionResult(val granted: Boolean) : WaterCameraUserIntent

    @Keep
    data class StoragePermissionResult(val granted: Boolean) : WaterCameraUserIntent

    data object CameraReady : WaterCameraUserIntent
    data object CameraCaptureUnavailable : WaterCameraUserIntent

    @Keep
    data class CameraError(val message: String) : WaterCameraUserIntent

    @Keep
    data class PhotoCaptured(
        val jpegBytes: ByteArray,
        val jpegOrientation: Int,
    ) : WaterCameraUserIntent
}

/**
 * 只能执行一次的 UI 副作用：权限弹窗、打开系统设置、启动相机、Toast 等。
 */
sealed interface WaterCameraEffect {
    data object RequestCameraPermission : WaterCameraEffect
    data object RequestLocationPermission : WaterCameraEffect
    data object RequestStoragePermission : WaterCameraEffect
    data object StartCameraPreview : WaterCameraEffect
    data object CapturePhoto : WaterCameraEffect
    data object ResumeCameraPreview : WaterCameraEffect
    data object OpenLocationSettings : WaterCameraEffect
    data object FinishPage : WaterCameraEffect

    @Keep
    data class Toast(val message: String) : WaterCameraEffect
}

/** XML 点击事件回调到 Activity，再由 Activity 转成 MVI Intent 交给 ViewModel。 */
interface WaterCameraActionHandler {
    fun onBack()
    fun onLocate()
    fun onCapture()
    fun onDiscard()
    fun onSave()
}
