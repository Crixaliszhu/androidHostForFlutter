package com.example.camera.water2.vm

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.water.entity.WaterCameraUiState
import com.example.camera.water.intent.WaterCameraEffect
import com.example.camera.water.location.LocationController
import com.example.camera.water.location.LocationUiState
import com.example.camera.water2.Water2CameraUserIntent
import com.example.camera.water2.WatermarkImageEntity
import com.example.camera.water2.click.IBizClick
import com.example.camera.water2.rep.IWatermark2Rep
import com.example.permission.PermissionUtils
import com.example.widget.titlebar.ktx.signalFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class Watermark2CameraViewModel @Inject constructor(
    application: Application,
    val iWatermark2Rep: IWatermark2Rep
) : AndroidViewModel(application),
    IBizClick {
    private val appContext = application.applicationContext
    private val timeFormat = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault())
    private val locationController = LocationController(appContext)

    private val _uiState = MutableStateFlow(
        WaterCameraUiState(timedText = currentTimeText())
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = Channel<WaterCameraEffect>(Channel.BUFFERED)
    val effect = _effects.receiveAsFlow()

    private val _bitmapSignal = signalFlow<WatermarkImageEntity>()
    private val bitmapResult = _bitmapSignal.flatMapLatest {
        iWatermark2Rep.createWatermarkedBitmap(it)
    }.onEach { bitmap ->
        if (bitmap == null) {
            _uiState.update {
                it.copy(
                    busy = false,
                    statusText = "生成水印照片失败",
                )
            }
            sendEffect(WaterCameraEffect.ResumeCameraPreview)
        } else {
            _uiState.update {
                it.copy(
                    previewBitmap = bitmap,
                    reviewing = true,
                    busy = false,
                    statusText = ""
                )
            }
        }
    }

    private val _locationSignal = signalFlow<Boolean>()
    private val locationResult = _locationSignal.flatMapLatest {
        iWatermark2Rep.requestCurrentLocation(locationController)
    }.onEach {
        handleLocationResult(it)
    }

    private val _saveImageSignal = signalFlow<Bitmap>()
    private val imageSaveResult = _saveImageSignal.flatMapLatest {
        iWatermark2Rep.saveToGallery(appContext, it, captureAtMills)
    }.onEach {
        handleSaveImageResult(it)
    }


    private var clockJob: Job? = null
    private var cameraPermissionRequested = false
    private var saveAfterPermissionGranted = false

    /** 拍照时间戳 */
    private var captureAtMills = 0L
    private var capturedTimedText = ""
    private var capturedLocationText = WaterCameraUiState.DEFAULT_LOCATION

    fun dispatch(intent: Water2CameraUserIntent) {
        when (intent) {
            Water2CameraUserIntent.CameraReady -> onCameraReady()
            Water2CameraUserIntent.PageStarted -> startClock()
            Water2CameraUserIntent.PageStopped -> stopClock()
            Water2CameraUserIntent.CameraCaptureUnavailable -> onCaptureUnavailable()
            is Water2CameraUserIntent.PageResumed -> onPageResumed(intent.hasCameraPermission)
            is Water2CameraUserIntent.PhotoCaptured -> onPhotoCaptured(
                jpegBytes = intent.jpegBytes,
                jpegOrientation = intent.jpegOrientation
            )

            is Water2CameraUserIntent.CameraError -> showError(intent.message)
            is Water2CameraUserIntent.CameraPermissionResult -> onCameraPermissionResult(intent.granted)
            is Water2CameraUserIntent.LocationPermissionResult -> onLocationPermissionResult(intent.granted)
            is Water2CameraUserIntent.StoragePermissionResult -> onStoragePermissionResult(intent.granted)
        }
    }

    private fun onCameraReady() {
        if (!_uiState.value.busy) {
            _uiState.update { it.copy(statusText = "") }
        }
    }

    private fun onPhotoCaptured(jpegBytes: ByteArray, jpegOrientation: Int) {
        _uiState.update { it.copy(statusText = "正在生成水印照片…") }
        _bitmapSignal.tryEmit(
            WatermarkImageEntity(
                jpegBytes = jpegBytes,
                jpegOrientation = jpegOrientation,
                timeText = capturedTimedText,
                locationText = capturedLocationText,
            )
        )
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

    private fun sendEffect(effect: WaterCameraEffect) {
        _effects.trySend(effect)
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

    private fun onCaptureUnavailable() {
        _uiState.update {
            it.copy(
                busy = false,
                statusText = "相机尚未准备好，请稍后重试"
            )
        }
    }

    private fun stopClock() {
        clockJob?.cancel()
        clockJob = null
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                busy = false,
                statusText = message
            )
        }
    }

    private fun handleSaveImageResult(status: Boolean) {
        if (status) {
            sendEffect(WaterCameraEffect.Toast("照片已保存到相册"))
            returnToCameraPreview()
            return
        }
        _uiState.update {
            it.copy(
                busy = false,
                statusText = "保存照片失败",
            )
        }
    }

    private fun handleLocationResult(result: LocationUiState?) {
        when (result) {
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
                _uiState.update {
                    it.copy(
                        busy = false,
                        statusText = "未获得定位权限，地点保持“我在这里”"
                    )
                }
            }

            LocationUiState.ProviderDisabled -> {
                _uiState.update { it.copy(busy = false, statusText = "系统定位服务未开启") }
                sendEffect(WaterCameraEffect.OpenLocationSettings)
            }

            LocationUiState.Unavailable -> {
                _uiState.update {
                    it.copy(
                        busy = false,
                        statusText = "暂时无法获取位置，请到开阔处重试"
                    )
                }
            }

            is LocationUiState.Failure -> {
                _uiState.update {
                    it.copy(
                        busy = false,
                        statusText = result.message.ifBlank { "定位失败" })
                }
            }

            null -> {}
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

    /**
     * 请求定位信息
     */
    private fun requestCurrentLocation() {
        _uiState.update { it.copy(busy = true, statusText = "正在获取当前位置…") }
        _locationSignal.tryEmit(true)
    }

    private fun onCameraPermissionResult(granted: Boolean) {
        if (granted && !_uiState.value.reviewing) {
            sendEffect(WaterCameraEffect.StartCameraPreview)
        } else if (!granted) {
            _uiState.update { it.copy(statusText = "相机权限被拒绝，点击拍照再次请求权限") }
        }
    }

    private fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            requestCurrentLocation()
            return
        }
        _uiState.update {
            it.copy(
                busy = false,
                statusText = "未获得定位权限，地点保持“我在这里”",
            )
        }
    }

    private fun onStoragePermissionResult(granted: Boolean) {
        if (granted && saveAfterPermissionGranted) {
            saveAfterPermissionGranted = false
            saveCurrentPhoto()
            return
        }
        saveAfterPermissionGranted = false
        _uiState.update {
            it.copy(
                busy = false,
                statusText = "没有存储权限，无法保存到相册",
            )
        }
    }

    /**
     * 图片保存到相册
     */
    private fun saveCurrentPhoto() {
        val bitmap = _uiState.value.previewBitmap ?: return
        _uiState.update { it.copy(busy = true, statusText = "正在保存到相册…") }
        _saveImageSignal.tryEmit(bitmap)
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

    private fun currentTimeText(): String = timeFormat.format(Date())

    override fun onLocate() {
        if (_uiState.value.busy) return
        if (!PermissionUtils.hasAnyLocationPermission(appContext)) {
            sendEffect(WaterCameraEffect.RequestLocationPermission)
            return
        }
        requestCurrentLocation()
    }

    override fun onCapture() {
        if (!PermissionUtils.hasPermission(appContext, Manifest.permission.CAMERA)) {
            cameraPermissionRequested = true
            sendEffect(WaterCameraEffect.RequestCameraPermission)
            return
        }
        val state = _uiState.value
        if (state.busy || state.reviewing) return
        captureAtMills = System.currentTimeMillis()
        capturedTimedText = timeFormat.format(Date(captureAtMills))
        capturedLocationText = state.locationText.ifBlank { WaterCameraUiState.DEFAULT_LOCATION }
        _uiState.update {
            it.copy(busy = true, statusText = "正在拍照...")
        }
        sendEffect(WaterCameraEffect.CapturePhoto)
    }

    override fun onDiscard() {
        if (_uiState.value.busy) return
        returnToCameraPreview()
    }

    override fun onSave() {
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

    override fun onBack() {
        val state = _uiState.value
        if (state.busy) return
        if (state.reviewing) {
            returnToCameraPreview()
            return
        }
        sendEffect(WaterCameraEffect.FinishPage)
    }

    override fun onCleared() {
        stopClock()
        locationController.cancel()
        super.onCleared()
    }

    init {
        uiState.launchIn(viewModelScope)
        bitmapResult.launchIn(viewModelScope)
        locationResult.launchIn(viewModelScope)
        imageSaveResult.launchIn(viewModelScope)
    }

    private companion object {
        const val CLOCK_INTERVAL_MILLIS = 1_000L
        const val STATUS_HIDE_DELAY_MILLIS = 1_800L
    }
}