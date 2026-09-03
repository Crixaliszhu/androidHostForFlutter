package com.example.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import java.util.concurrent.Executor
import kotlin.math.abs

/**
 * 水印相机专用的 Camera2 控制器。
 *
 * 与教学页控制器的区别是：这里不把 JPEG 直接写文件，而是把内存字节交给页面。页面先生成
 * 带水印的确认图，用户点击保存后才真正写入 MediaStore，因此“关闭”不会留下临时照片。
 */
class WatermarkCameraController(
    context: Context,
    private val onCameraReady: () -> Unit,
    private val onPhotoCaptured: (jpegBytes: ByteArray, jpegOrientation: Int) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var textureView: TextureView? = null
    private var previewSurface: Surface? = null
    private var imageReader: ImageReader? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequest: CaptureRequest? = null

    private var sensorOrientation = 0
    private var captureAeMode = CaptureRequest.CONTROL_AE_MODE_ON
    private var opening = false
    private var started = false
    private var capturing = false

    /** Activity.onResume 调用。SurfaceTexture 尚未创建时，控制器会等待 TextureView 回调。 */
    fun start(preview: TextureView) {
        textureView = preview
        started = true
        startCameraThread()
        if (!hasCameraPermission()) {
            notifyError("需要相机权限才能预览")
            return
        }
        if (cameraDevice != null || opening) return
        if (preview.isAvailable) {
            openCamera(preview.width, preview.height)
        } else {
            preview.surfaceTextureListener = textureListener
        }
    }

    /** Activity.onPause 调用，及时释放系统独占的 CameraDevice 和后台线程。 */
    fun stop() {
        started = false
        closeCamera()
        stopCameraThread()
    }

    /** 确认页返回取景状态时，重新启动预览 repeating request。 */
    fun resumePreview() {
        val session = captureSession ?: return
        val request = previewRequest ?: return
        runCatching {
            session.setRepeatingRequest(request, null, cameraHandler)
            capturing = false
            mainHandler.post(onCameraReady)
        }.onFailure { notifyError(it.message ?: "恢复相机预览失败") }
    }

    /**
     * 下发一次 STILL_CAPTURE。JPEG_ORIENTATION 同时返回给页面，用于把 JPEG 像素转成正向位图。
     * 返回 false 表示 Session 还没准备好，调用方应恢复按钮状态。
     */
    fun takePhoto(): Boolean {
        if (capturing) return false
        val device = cameraDevice ?: return false
        val session = captureSession ?: return false
        val reader = imageReader ?: return false

        return runCatching {
            capturing = true
            val orientation = jpegOrientation()
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, captureAeMode)
                set(CaptureRequest.JPEG_ORIENTATION, orientation)
                setTag(orientation)
            }.build()

            session.capture(
                request,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) = Unit
                },
                cameraHandler,
            )
            true
        }.getOrElse {
            capturing = false
            notifyError(it.message ?: "拍照请求失败")
            false
        }
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            if (started) openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeCamera()
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            opening = false
            if (!started) {
                camera.close()
                return
            }
            cameraDevice = camera
            createCaptureSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            opening = false
            camera.close()
            cameraDevice = null
            notifyError("相机连接已断开")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            opening = false
            camera.close()
            cameraDevice = null
            notifyError("打开相机失败，错误码：$error")
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(viewWidth: Int, viewHeight: Int) {
        if (!started || opening || cameraDevice != null || !hasCameraPermission()) return
        opening = true
        runCatching {
            val cameraId = selectBackCamera() ?: error("设备没有可用摄像头")
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: error("相机没有可用的输出尺寸")
            val previewSize = choosePreviewSize(
                map.getOutputSizes(SurfaceTexture::class.java).orEmpty(),
                viewWidth,
                viewHeight,
            )
            val captureSize = chooseCaptureSize(map.getOutputSizes(ImageFormat.JPEG).orEmpty())

            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            captureAeMode = chooseCaptureAeMode(
                characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf(),
            )

            textureView?.surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            imageReader?.close()
            imageReader = ImageReader.newInstance(
                captureSize.width,
                captureSize.height,
                ImageFormat.JPEG,
                IMAGE_BUFFER_COUNT,
            ).apply {
                setOnImageAvailableListener({ reader -> consumeJpeg(reader) }, cameraHandler)
            }
            cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler)
        }.onFailure {
            opening = false
            notifyError(it.message ?: "初始化相机失败")
        }
    }

    /** Session 的输出 Surface 在创建时固定：一个负责实时预览，一个负责 JPEG 成片。 */
    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val texture = textureView?.surfaceTexture ?: return
        val readerSurface = imageReader?.surface ?: return
        val surface = Surface(texture)
        previewSurface = surface
        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }.build()
        previewRequest = request

        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (!started || cameraDevice == null) {
                    session.close()
                    return
                }
                captureSession = session
                session.setRepeatingRequest(request, null, cameraHandler)
                mainHandler.post(onCameraReady)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                notifyError("相机输出会话配置失败")
            }
        }
        createSessionCompat(device, listOf(surface, readerSurface), callback)
    }

    /** Android 9+ 使用 SessionConfiguration；旧设备保留集中隔离的兼容调用。 */
    private fun createSessionCompat(
        device: CameraDevice,
        surfaces: List<Surface>,
        callback: CameraCaptureSession.StateCallback,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val configuration = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                surfaces.map { surface -> OutputConfiguration(surface) },
                cameraExecutor(),
                callback,
            )
            device.createCaptureSession(configuration)
        } else {
            createLegacySession(device, surfaces, callback)
        }
    }

    @Suppress("DEPRECATION")
    private fun createLegacySession(
        device: CameraDevice,
        surfaces: List<Surface>,
        callback: CameraCaptureSession.StateCallback,
    ) {
        device.createCaptureSession(surfaces, callback, cameraHandler)
    }

    private fun cameraExecutor(): Executor = Executor { command ->
        cameraHandler?.post(command) ?: mainHandler.post(command)
    }

    /** ImageReader buffer 数量有限，必须在 finally 中 close，否则连续拍照会因无空闲 buffer 卡住。 */
    private fun consumeJpeg(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val buffer = image.planes.first().buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val orientation = jpegOrientation()
            captureSession?.stopRepeating()
            mainHandler.post { onPhotoCaptured(bytes, orientation) }
        } catch (throwable: Throwable) {
            capturing = false
            notifyError(throwable.message ?: "读取照片数据失败")
        } finally {
            image.close()
        }
    }

    private fun closeCamera() {
        opening = false
        capturing = false
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        previewRequest = null
        previewSurface?.release()
        previewSurface = null
    }

    private fun startCameraThread() {
        if (cameraThread != null) return
        cameraThread = HandlerThread("WatermarkCamera").also {
            it.start()
            cameraHandler = Handler(it.looper)
        }
    }

    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
    }

    private fun hasCameraPermission(): Boolean {
        return appContext.checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun selectBackCamera(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull()
    }

    private fun choosePreviewSize(sizes: Array<out Size>, viewWidth: Int, viewHeight: Int): Size {
        val targetRatio = if (viewWidth > 0 && viewHeight > 0) {
            maxOf(viewWidth, viewHeight).toFloat() / minOf(viewWidth, viewHeight)
        } else {
            DEFAULT_RATIO
        }
        return sizes
            .filter { it.width <= MAX_PREVIEW_WIDTH && it.height <= MAX_PREVIEW_HEIGHT }
            .minByOrNull { abs(it.width.toFloat() / it.height - targetRatio) }
            ?: sizes.firstOrNull()
            ?: Size(DEFAULT_WIDTH, DEFAULT_HEIGHT)
    }

    /** 限制成片在约 8MP 内，避免解码成 ARGB 水印位图时占用过多内存。 */
    private fun chooseCaptureSize(sizes: Array<out Size>): Size {
        return sizes
            .filter { it.width.toLong() * it.height <= MAX_CAPTURE_PIXELS }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.minByOrNull { abs(it.width.toLong() * it.height - MAX_CAPTURE_PIXELS) }
            ?: Size(DEFAULT_WIDTH, DEFAULT_HEIGHT)
    }

    /** 优先自动闪光；不支持时退回普通自动曝光。 */
    private fun chooseCaptureAeMode(modes: IntArray): Int {
        return if (modes.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
        } else {
            CaptureRequest.CONTROL_AE_MODE_ON
        }
    }

    private fun jpegOrientation(): Int {
        // TextureView 已附着到当前 Display，直接读取它的 rotation，避免使用废弃的 defaultDisplay。
        val rotation = textureView?.display?.rotation ?: Surface.ROTATION_0
        val deviceDegrees = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return (sensorOrientation - deviceDegrees + FULL_ROTATION) % FULL_ROTATION
    }

    private fun notifyError(message: String) {
        mainHandler.post { onError(message) }
    }

    private companion object {
        const val IMAGE_BUFFER_COUNT = 2
        const val MAX_PREVIEW_WIDTH = 1920
        const val MAX_PREVIEW_HEIGHT = 1080
        const val MAX_CAPTURE_PIXELS = 8_000_000L
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 960
        const val DEFAULT_RATIO = 3f / 4f
        const val FULL_ROTATION = 360
    }
}
