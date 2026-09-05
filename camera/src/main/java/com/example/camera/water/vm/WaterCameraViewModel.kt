package com.example.camera.water.vm

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.water.utils.WatermarkPhotoProcessor
import com.example.camera.water.entity.WaterCameraUiState
import com.example.camera.water.intent.WaterCameraEffect
import com.example.camera.water.intent.WaterCameraUserIntent
import com.example.camera.water.location.LocationController
import com.example.camera.water.location.LocationUiState
import com.example.camera.water.permission.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 水印相机 MVI ViewModel。
 *
 * - Intent：用户点击、权限结果、Camera2 回调和生命周期输入。
 * - State：页面可直接渲染的数据。
 * - Effect：启动相机、请求权限、Toast、跳系统设置等一次性动作。
 */
class WaterCameraViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val timeFormat = SimpleDateFormat("yyyy.MM.dd  HH:mm:ss", Locale.getDefault())
    private val locationController = LocationController(appContext)

    private val _uiState = MutableStateFlow(
        WaterCameraUiState(timedText = currentTimeText()),
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = Channel<WaterCameraEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var clockJob: Job? = null
    private var cameraPermissionRequested = false
    private var saveAfterPermissionGranted = false
    private var capturedAtMillis = 0L
    private var capturedTimeText = ""
    private var capturedLocationText = WaterCameraUiState.DEFAULT_LOCATION

    fun dispatch(intent: WaterCameraUserIntent) {
        when (intent) {
            WaterCameraUserIntent.PageStarted -> startClock()
            WaterCameraUserIntent.PageStopped -> stopClock()
            is WaterCameraUserIntent.PageResumed -> onPageResumed(intent.hasCameraPermission)
            WaterCameraUserIntent.BackClicked -> onBack()
            WaterCameraUserIntent.LocateClicked -> onLocate()
            WaterCameraUserIntent.CaptureClicked -> onCapture()
            WaterCameraUserIntent.DiscardClicked -> onDiscard()
            WaterCameraUserIntent.SaveClicked -> onSave()
            is WaterCameraUserIntent.CameraPermissionResult -> onCameraPermissionResult(intent.granted)
            is WaterCameraUserIntent.LocationPermissionResult -> onLocationPermissionResult(intent.granted)
            is WaterCameraUserIntent.StoragePermissionResult -> onStoragePermissionResult(intent.granted)
            WaterCameraUserIntent.CameraReady -> onCameraReady()
            WaterCameraUserIntent.CameraCaptureUnavailable -> onCameraCaptureUnavailable()
            is WaterCameraUserIntent.CameraError -> showError(intent.message)
            is WaterCameraUserIntent.PhotoCaptured -> onPhotoCaptured(
                jpegBytes = intent.jpegBytes,
                jpegOrientation = intent.jpegOrientation,
            )
        }
    }

    private fun startClock() {
        if (clockJob != null) return
        clockJob = viewModelScope.launch {
            while (true) {
                _uiState.update { state ->
                    if (state.reviewing) state else state.copy(timedText = currentTimeText())
                }
                delay(CLOCK_INTERVAL_MILLIS)
            }
        }
    }

    private fun stopClock() {
        clockJob?.cancel()
        clockJob = null
    }

    private fun onPageResumed(hasCameraPermission: Boolean) {
        if (_uiState.value.reviewing) return
        if (hasCameraPermission) {
            sendEffect(WaterCameraEffect.StartCameraPreview)
        } else if (!cameraPermissionRequested) {
            cameraPermissionRequested = true
            sendEffect(WaterCameraEffect.RequestCameraPermission)
        }
    }

    private fun onBack() {
        val state = _uiState.value
        if (state.busy) return
        if (state.reviewing) {
            returnToCameraPreview()
        } else {
            sendEffect(WaterCameraEffect.FinishPage)
        }
    }

    private fun onLocate() {
        if (_uiState.value.busy) return
        if (!PermissionUtils.hasAnyLocationPermission(appContext)) {
            sendEffect(WaterCameraEffect.RequestLocationPermission)
            return
        }
        requestCurrentLocation()
    }

    private fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            requestCurrentLocation()
        } else {
            _uiState.update {
                it.copy(
                    busy = false,
                    statusText = "未获得定位权限，地点保持“我在这里”",
                )
            }
        }
    }

    private fun requestCurrentLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, statusText = "正在获取当前位置…") }
            when (val result = locationController.requestCurrentLocation()) {
                is LocationUiState.Success -> {
                    _uiState.update {
                        it.copy(
                            locationText = result.locationText,
                            busy = false,
                            statusText = "定位成功",
                        )
                    }
                    hideStatusLater()
                }

                LocationUiState.PermissionDenied -> {
                    _uiState.update { it.copy(busy = false, statusText = "未获得定位权限，地点保持“我在这里”") }
                }

                LocationUiState.ProviderDisabled -> {
                    _uiState.update { it.copy(busy = false, statusText = "系统定位服务未开启") }
                    sendEffect(WaterCameraEffect.OpenLocationSettings)
                }

                LocationUiState.Unavailable -> {
                    _uiState.update { it.copy(busy = false, statusText = "暂时无法获取位置，请到开阔处重试") }
                }

                is LocationUiState.Failure -> {
                    _uiState.update { it.copy(busy = false, statusText = result.message.ifBlank { "定位失败" }) }
                }
            }
        }
    }

    private fun onCapture() {
        if (!PermissionUtils.hasPermission(appContext, Manifest.permission.CAMERA)) {
            cameraPermissionRequested = true
            sendEffect(WaterCameraEffect.RequestCameraPermission)
            return
        }

        val state = _uiState.value
        if (state.busy || state.reviewing) return

        capturedAtMillis = System.currentTimeMillis()
        capturedTimeText = timeFormat.format(Date(capturedAtMillis))
        capturedLocationText = state.locationText.ifBlank { WaterCameraUiState.DEFAULT_LOCATION }
        _uiState.update {
            it.copy(
                busy = true,
                statusText = "正在拍照…",
            )
        }
        sendEffect(WaterCameraEffect.CapturePhoto)
    }

    private fun onCameraPermissionResult(granted: Boolean) {
        if (granted && !_uiState.value.reviewing) {
            sendEffect(WaterCameraEffect.StartCameraPreview)
        } else if (!granted) {
            _uiState.update { it.copy(statusText = "相机权限被拒绝，点击拍照可再次申请") }
        }
    }

    private fun onCameraReady() {
        if (!_uiState.value.busy) {
            _uiState.update { it.copy(statusText = "") }
        }
    }

    private fun onCameraCaptureUnavailable() {
        _uiState.update {
            it.copy(
                busy = false,
                statusText = "相机尚未准备好，请稍后重试",
            )
        }
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                busy = false,
                statusText = message,
            )
        }
    }

    private fun onPhotoCaptured(jpegBytes: ByteArray, jpegOrientation: Int) {
        _uiState.update { it.copy(statusText = "正在生成水印照片…") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    WatermarkPhotoProcessor.createWatermarkedBitmap(
                        jpegBytes = jpegBytes,
                        jpegOrientation = jpegOrientation,
                        timeText = capturedTimeText,
                        locationText = capturedLocationText,
                    )
                }
            }.onSuccess { bitmap ->
                _uiState.update {
                    it.copy(
                        previewBitmap = bitmap,
                        reviewing = true,
                        busy = false,
                        statusText = "",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        busy = false,
                        statusText = throwable.message ?: "生成水印照片失败",
                    )
                }
                sendEffect(WaterCameraEffect.ResumeCameraPreview)
            }
        }
    }

    private fun onDiscard() {
        if (_uiState.value.busy) return
        returnToCameraPreview()
    }

    private fun returnToCameraPreview() {
        _uiState.update {
            it.copy(
                previewBitmap = null,
                reviewing = false,
                busy = false,
                statusText = "",
            )
        }
        sendEffect(WaterCameraEffect.ResumeCameraPreview)
    }

    private fun onSave() {
        if (_uiState.value.busy || _uiState.value.previewBitmap == null) return
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            !PermissionUtils.hasPermission(appContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ) {
            saveAfterPermissionGranted = true
            sendEffect(WaterCameraEffect.RequestStoragePermission)
            return
        }
        saveCurrentPhoto()
    }

    private fun onStoragePermissionResult(granted: Boolean) {
        if (granted && saveAfterPermissionGranted) {
            saveAfterPermissionGranted = false
            saveCurrentPhoto()
        } else {
            saveAfterPermissionGranted = false
            _uiState.update {
                it.copy(
                    busy = false,
                    statusText = "没有存储权限，无法保存到相册",
                )
            }
        }
    }

    private fun saveCurrentPhoto() {
        val bitmap = _uiState.value.previewBitmap ?: return
        _uiState.update { it.copy(busy = true, statusText = "正在保存到相册…") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    WatermarkPhotoProcessor.saveToGallery(appContext, bitmap, capturedAtMillis)
                }
            }.onSuccess {
                sendEffect(WaterCameraEffect.Toast("照片已保存到相册"))
                returnToCameraPreview()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        busy = false,
                        statusText = throwable.message ?: "保存照片失败",
                    )
                }
            }
        }
    }

    private fun hideStatusLater() {
        viewModelScope.launch {
            delay(STATUS_HIDE_DELAY_MILLIS)
            if (!_uiState.value.busy) {
                _uiState.update { it.copy(statusText = "") }
            }
        }
    }

    private fun sendEffect(effect: WaterCameraEffect) {
        _effects.trySend(effect)
    }

    private fun currentTimeText(): String = timeFormat.format(Date())

    override fun onCleared() {
        stopClock()
        locationController.cancel()
        super.onCleared()
    }

    init {
        uiState.launchIn(viewModelScope)
    }

    private companion object {
        const val CLOCK_INTERVAL_MILLIS = 1_000L
        const val STATUS_HIDE_DELAY_MILLIS = 1_800L
    }
}
