package com.example.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.example.camera.databinding.ActivityWatermarkCameraBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 简单版水印相机页面，使用 XML DataBinding 实现。
 *
 * 页面有两个明确状态：
 * - 拍摄态：TextureView 实时预览，时间每秒刷新，地点可通过按钮获取。
 * - 确认态：ImageView 显示已绘制水印的位图，只允许关闭或保存。
 */
@Route(path = CameraRouterPaths.WATERMARK_CAMERA)
class WatermarkCameraActivity : ComponentActivity(), WatermarkCameraActionHandler {

    private lateinit var binding: ActivityWatermarkCameraBinding
    private val uiModel = WatermarkCameraUiModel()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val timeFormat = SimpleDateFormat("yyyy.MM.dd  HH:mm:ss", Locale.getDefault())

    private val locationManager by lazy { getSystemService(LocationManager::class.java) }
    private var cameraPermissionRequested = false
    private var locationGeneration = 0
    private var locationTimeout: Runnable? = null
    private var locationCancellationSignal: CancellationSignal? = null

    /** 正在确认的位图只保存在内存中；关闭不会产生文件，保存成功后才释放。 */
    private var pendingBitmap: Bitmap? = null
    private var capturedAtMillis = 0L
    private var capturedTimeText = ""
    private var capturedLocationText = DEFAULT_LOCATION
    private var saveAfterPermissionGranted = false

    private val cameraController by lazy {
        WatermarkCameraController(
            context = this,
            onCameraReady = {
                // 相机就绪可能与定位并行，不能在这里清掉定位任务的 busy/status。
                if (!uiModel.busy.get()) uiModel.showStatus("")
            },
            onPhotoCaptured = ::onJpegCaptured,
            onError = { message ->
                uiModel.busy.set(false)
                uiModel.showStatus(message)
            },
        )
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionRequested = true
        if (granted && !uiModel.reviewing.get()) {
            cameraController.start(binding.cameraPreview)
        } else if (!granted) {
            uiModel.showStatus("相机权限被拒绝，点击拍照可再次申请")
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            requestCurrentLocation()
        } else {
            uiModel.busy.set(false)
            uiModel.showStatus("未获得定位权限，地点保持“我在这里”")
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && saveAfterPermissionGranted) {
            saveAfterPermissionGranted = false
            saveCurrentPhoto()
        } else {
            saveAfterPermissionGranted = false
            uiModel.busy.set(false)
            uiModel.showStatus("没有存储权限，无法保存到相册")
        }
    }

    private val clockTicker = object : Runnable {
        override fun run() {
            if (!uiModel.reviewing.get()) {
                uiModel.timeText.set(timeFormat.format(Date()))
            }
            mainHandler.postDelayed(this, CLOCK_INTERVAL_MILLIS)
        }
    }

    private val legacyLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            resolveLocation(location, locationGeneration)
        }

        @Deprecated("Deprecated by framework")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatermarkCameraBinding.inflate(layoutInflater)
        binding.vm = uiModel
        binding.actions = this
        binding.lifecycleOwner = this
        setContentView(binding.root)

        uiModel.timeText.set(timeFormat.format(Date()))
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = onBack()
            },
        )
    }

    override fun onStart() {
        super.onStart()
        mainHandler.post(clockTicker)
    }

    override fun onResume() {
        super.onResume()
        if (uiModel.reviewing.get()) return
        if (hasPermission(Manifest.permission.CAMERA)) {
            cameraController.start(binding.cameraPreview)
        } else if (!cameraPermissionRequested) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        cameraController.stop()
        super.onPause()
    }

    override fun onStop() {
        mainHandler.removeCallbacks(clockTicker)
        super.onStop()
    }

    override fun onDestroy() {
        cancelLocationRequest()
        // 保存线程可能正在读取 bitmap；忙碌时由 worker 完成后负责回收，避免并发 recycle。
        if (!uiModel.busy.get()) pendingBitmap?.recycle()
        pendingBitmap = null
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onBack() {
        if (uiModel.busy.get()) return
        if (uiModel.reviewing.get()) {
            onDiscard()
        } else {
            finish()
        }
    }

    override fun onLocate() {
        if (!hasAnyLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return
        }
        requestCurrentLocation()
    }

    override fun onCapture() {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        if (uiModel.busy.get() || uiModel.reviewing.get()) return

        // 快门按下瞬间冻结水印内容，后续定位或时钟变化不会改变这张照片的语义。
        capturedAtMillis = System.currentTimeMillis()
        capturedTimeText = timeFormat.format(Date(capturedAtMillis))
        capturedLocationText = uiModel.locationText.get().orEmpty().ifBlank { DEFAULT_LOCATION }
        uiModel.busy.set(true)
        uiModel.showStatus("正在拍照…")
        if (!cameraController.takePhoto()) {
            uiModel.busy.set(false)
            uiModel.showStatus("相机尚未准备好，请稍后重试")
        }
    }

    override fun onDiscard() {
        if (uiModel.busy.get()) return
        returnToCameraPreview()
    }

    private fun returnToCameraPreview() {
        binding.photoPreview.setImageDrawable(null)
        pendingBitmap?.recycle()
        pendingBitmap = null
        uiModel.reviewing.set(false)
        uiModel.busy.set(false)
        uiModel.showStatus("")
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // 页面可能在确认照片期间进入过后台，此时 Session 已释放：先尝试恢复，再确保重新打开。
            cameraController.resumePreview()
            cameraController.start(binding.cameraPreview)
        }
    }

    override fun onSave() {
        if (pendingBitmap == null || uiModel.busy.get()) return
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        ) {
            saveAfterPermissionGranted = true
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        saveCurrentPhoto()
    }

    /** JPEG 解码、旋转和 Canvas 水印都可能较耗时，放到单线程 worker 中串行执行。 */
    private fun onJpegCaptured(jpegBytes: ByteArray, jpegOrientation: Int) {
        uiModel.showStatus("正在生成水印照片…")
        worker.execute {
            runCatching {
                WatermarkPhotoProcessor.createWatermarkedBitmap(
                    jpegBytes = jpegBytes,
                    jpegOrientation = jpegOrientation,
                    timeText = capturedTimeText,
                    locationText = capturedLocationText,
                )
            }.onSuccess { bitmap ->
                mainHandler.post {
                    if (isDestroyed) {
                        bitmap.recycle()
                        return@post
                    }
                    pendingBitmap?.recycle()
                    pendingBitmap = bitmap
                    binding.photoPreview.setImageBitmap(bitmap)
                    uiModel.reviewing.set(true)
                    uiModel.busy.set(false)
                    uiModel.showStatus("")
                }
            }.onFailure { throwable ->
                mainHandler.post {
                    if (isDestroyed) return@post
                    uiModel.busy.set(false)
                    uiModel.showStatus(throwable.message ?: "生成水印照片失败")
                    cameraController.resumePreview()
                }
            }
        }
    }

    private fun saveCurrentPhoto() {
        val bitmap = pendingBitmap ?: return
        uiModel.busy.set(true)
        uiModel.showStatus("正在保存到相册…")
        worker.execute {
            runCatching {
                WatermarkPhotoProcessor.saveToGallery(this, bitmap, capturedAtMillis)
            }.onSuccess {
                mainHandler.post {
                    if (isDestroyed) {
                        bitmap.recycle()
                        return@post
                    }
                    Toast.makeText(this, "照片已保存到相册", Toast.LENGTH_SHORT).show()
                    returnToCameraPreview()
                }
            }.onFailure { throwable ->
                mainHandler.post {
                    if (isDestroyed) {
                        bitmap.recycle()
                        return@post
                    }
                    uiModel.busy.set(false)
                    uiModel.showStatus(throwable.message ?: "保存照片失败")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation() {
        if (!hasAnyLocationPermission()) return
        cancelLocationRequest()
        val generation = ++locationGeneration
        uiModel.busy.set(true)
        uiModel.showStatus("正在获取当前位置…")

        val provider = enabledLocationProvider()
        if (provider == null) {
            uiModel.busy.set(false)
            uiModel.showStatus("系统定位服务未开启")
            startActivity(android.content.Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }

        val timeout = Runnable {
            if (generation != locationGeneration) return@Runnable
            val fallback = newestLastKnownLocation()
            if (fallback != null) {
                resolveLocation(fallback, generation)
            } else {
                cancelLocationRequest()
                uiModel.busy.set(false)
                uiModel.showStatus("暂时无法获取位置，请到开阔处重试")
            }
        }
        locationTimeout = timeout
        mainHandler.postDelayed(timeout, LOCATION_TIMEOUT_MILLIS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            locationCancellationSignal = signal
            locationManager.getCurrentLocation(provider, signal, mainExecutor) { location ->
                if (location != null) resolveLocation(location, generation)
            }
        } else {
            @Suppress("DEPRECATION")
            locationManager.requestSingleUpdate(provider, legacyLocationListener, Looper.getMainLooper())
        }
    }

    /** 地址反查可能访问系统 Geocoder 服务，放到后台线程，失败时仍展示经纬度。 */
    private fun resolveLocation(location: Location, generation: Int) {
        if (generation != locationGeneration) return
        locationTimeout?.let(mainHandler::removeCallbacks)
        locationTimeout = null
        runCatching { locationManager.removeUpdates(legacyLocationListener) }
        locationCancellationSignal?.cancel()
        locationCancellationSignal = null
        worker.execute {
            val locationText = reverseGeocode(location)
            mainHandler.post {
                if (generation != locationGeneration || isDestroyed) return@post
                uiModel.locationText.set(locationText)
                uiModel.busy.set(false)
                uiModel.showStatus("定位成功")
                mainHandler.postDelayed(
                    { if (!uiModel.busy.get()) uiModel.showStatus("") },
                    STATUS_HIDE_DELAY_MILLIS,
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(location: Location): String {
        val fallback = String.format(
            Locale.getDefault(),
            "%.5f, %.5f",
            location.latitude,
            location.longitude,
        )
        if (!Geocoder.isPresent()) return fallback
        return runCatching {
            val address = Geocoder(this, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
            address?.displayText().orEmpty().ifBlank { fallback }
        }.getOrDefault(fallback)
    }

    private fun Address.displayText(): String {
        getAddressLine(0)?.takeIf { it.isNotBlank() }?.let { return it }
        return listOfNotNull(adminArea, locality, subLocality, thoroughfare, featureName)
            .distinct()
            .joinToString("")
    }

    @SuppressLint("MissingPermission")
    private fun newestLastKnownLocation(): Location? {
        return locationManager.getProviders(true)
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull(Location::getTime)
    }

    private fun enabledLocationProvider(): String? {
        return when {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
    }

    private fun cancelLocationRequest() {
        locationGeneration++
        locationTimeout?.let(mainHandler::removeCallbacks)
        locationTimeout = null
        locationCancellationSignal?.cancel()
        locationCancellationSignal = null
        runCatching { locationManager.removeUpdates(legacyLocationListener) }
    }

    private fun hasAnyLocationPermission(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun hasPermission(permission: String): Boolean {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val DEFAULT_LOCATION = "我在这里"
        const val CLOCK_INTERVAL_MILLIS = 1_000L
        const val LOCATION_TIMEOUT_MILLIS = 10_000L
        const val STATUS_HIDE_DELAY_MILLIS = 1_800L
    }
}
