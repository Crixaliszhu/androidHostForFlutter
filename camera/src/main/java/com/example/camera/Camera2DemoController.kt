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
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Camera2 示例控制器。
 *
 * 这个类故意不放到 Activity 里，是为了把 Camera2 的“设备控制”和 Compose 的“界面展示”分开：
 * - Activity 负责权限、生命周期和 UI。
 * - Controller 负责 CameraManager、CameraDevice、CaptureSession、CaptureRequest、ImageReader。
 *
 * Camera2 的开发主线可以理解成：
 * 1. 从 CameraManager 枚举设备并读取 CameraCharacteristics。
 * 2. 用 StreamConfigurationMap 决定预览和拍照可以使用哪些格式/尺寸。
 * 3. 打开 CameraDevice。
 * 4. 用一组输出 Surface 创建 CameraCaptureSession。
 * 5. 通过 setRepeatingRequest 持续驱动预览，通过 capture 下发单帧拍照。
 * 6. 从 CaptureCallback 接收 result metadata，从 ImageReader 消费图片 buffer。
 */
class Camera2DemoController(
    /** 这里使用 Activity context 读取 display rotation；其它持久引用都转成 applicationContext。 */
    private val context: Context,
    /** 所有 Camera2 异步回调最终都折叠成 CameraUiState 推给 UI。 */
    private val onStateChanged: (CameraUiState) -> Unit,
) {
    // 使用 applicationContext 避免后台 Handler 持有 Activity 造成泄漏。
    private val appContext = context.applicationContext

    // CameraManager 是 Framework 暴露给 App 的相机总入口，对应系统侧 CameraService 的客户端代理。
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)

    // Camera2 回调不应该直接改 Compose state；先切回主线程再通知 UI。
    private val mainHandler = Handler(Looper.getMainLooper())
    private var state = CameraUiState()

    // Camera 打开、Session 配置、request 下发和 ImageReader 保存都放在专用线程。
    // 这样预览和拍照回调不会挤占主线程，也更接近真实业务代码的写法。
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null

    // TextureView 提供预览 SurfaceTexture；Camera2 只认识 Surface，不直接认识 Compose 节点。
    private var textureView: TextureView? = null

    // CameraDevice 表示已经打开的物理/逻辑相机设备。
    private var cameraDevice: CameraDevice? = null

    // CaptureSession 绑定一组固定输出 Surface。后续 request 的 target 必须来自这组 Surface。
    private var captureSession: CameraCaptureSession? = null

    // 预览 request builder 会被复用，比如切换 Torch 时只改 FLASH_MODE 再重新 setRepeatingRequest。
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    // 记录预览输出 Surface，拍照闪光状态机需要创建全新的预览 request，避免复用 builder 时残留 FLASH_MODE_OFF。
    private var previewSurface: Surface? = null

    // ImageReader 是拍照输出端。它背后有有限个 buffer，拿到 Image 后必须 close。
    private var imageReader: ImageReader? = null

    // 记录当前 Camera ID 和传感器方向，拍照时用来计算 JPEG_ORIENTATION。
    private var activeCameraId: String? = null
    private var preferredFacing = CameraCharacteristics.LENS_FACING_BACK
    private var sensorOrientation = 0

    // Compose 的 AndroidView 创建、Activity.onResume、TextureView 回调可能相互靠得很近。
    // 用 openingCamera 防止重复 openCamera 造成 “camera already opened / in use” 类问题。
    private var openingCamera = false

    private enum class CaptureState {
        PREVIEW,
        WAITING_AF_LOCK,
        WAITING_PRECAPTURE,
        WAITING_NON_PRECAPTURE,
        PICTURE_TAKEN,
    }

    // 拍照闪光不是单个 still request 就能稳定完成，很多设备需要 AF/AE 状态机配合。
    // 这个状态只驱动“点击拍照后的一次闪光拍照流程”。
    private var captureState = CaptureState.PREVIEW

    /**
     * Activity 权限结果同步到 UI。
     *
     * 这里只更新状态，不直接打开相机；真正打开需要等 TextureView 的 SurfaceTexture 可用。
     */
    fun markPermission(granted: Boolean) {
        updateState {
            copy(
                permissionGranted = granted,
                status = if (granted) "已授权，等待预览 Surface" else "等待相机权限",
                lastError = null,
            )
        }
    }

    /**
     * 启动相机链路。
     *
     * start 可能来自两个地方：
     * - Activity.onResume：页面回到前台。
     * - AndroidView factory：TextureView 第一次创建完成。
     *
     * Camera2 要求先有可用的输出 Surface，再配置 Session，所以这里会等待 TextureView 可用。
     */
    fun start(preview: TextureView) {
        textureView = preview
        if (!hasCameraPermission()) {
            markPermission(false)
            return
        }
        markPermission(true)
        startBackgroundThread()
        if (openingCamera || cameraDevice != null || captureSession != null) return
        if (preview.isAvailable) {
            openCamera(preview.width, preview.height)
        } else {
            preview.surfaceTextureListener = textureListener
        }
    }

    /**
     * 停止相机链路。
     *
     * Camera 是系统独占资源，页面不可见时要尽快释放：
     * - 否则其它 App/页面可能打不开相机。
     * - 后台继续占用相机也容易触发厂商系统限制或隐私提示。
     */
    fun stop() {
        closeCamera()
        stopBackgroundThread()
        updateState { copy(isPreviewing = false, status = "相机已释放") }
    }

    /**
     * 切换前后摄。
     *
     * Camera2 没有“在同一个 CameraDevice 上切换镜头”的通用 API，
     * 可靠做法是关闭当前 device/session，再按新的 lens facing 重新选择 cameraId 并打开。
     */
    fun switchCamera() {
        preferredFacing = if (preferredFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val preview = textureView ?: return
        closeCamera()
        start(preview)
    }

    /**
     * 切换预览 Torch。
     *
     * Torch 在这里作为 CaptureRequest.FLASH_MODE 的一部分写进 repeating request。
     * 真实业务也可以用 CameraManager.setTorchMode(cameraId, enabled)，但那更偏全局手电筒控制；
     * 本 Demo 选择 request 方式，是为了展示“修改 request 参数后重新下发 repeating request”的模型。
     */
    fun toggleTorch() {
        runCatching {
            val nextEnabled = !state.torchEnabled
            if (!state.torchAvailable) {
                updateState { copy(lastError = "当前摄像头没有闪光灯") }
                return
            }
            previewRequestBuilder?.set(
                CaptureRequest.FLASH_MODE,
                if (nextEnabled) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF,
            )
            captureSession?.setRepeatingRequest(
                previewRequestBuilder?.build() ?: return,
                captureCallback,
                cameraHandler,
            )
            updateState {
                copy(
                    torchEnabled = nextEnabled,
                    status = if (nextEnabled) "预览闪光灯已打开" else "预览闪光灯已关闭",
                )
            }
        }.onFailure { throwable ->
            updateState {
                copy(
                    status = "切换闪光灯失败",
                    lastError = throwable.message ?: throwable.javaClass.simpleName,
                )
            }
        }
    }

    /**
     * 启动一次带闪光灯策略的拍照流程。
     *
     * 日志里已经看到 still request 的 resultAeState=FLASH_REQUIRED、flashState=READY，
     * 这说明 HAL 认为“需要闪光”，但直接发 still request 没有触发完整预闪/主闪流程。
     *
     * 因此这里改成更接近系统相机的流程：
     * 1. 预览 request 切到 ON_ALWAYS_FLASH，告诉 3A 本次拍照需要强制闪光策略。
     * 2. 发 AF_TRIGGER_START，等待对焦锁定或得到可继续的 AF 状态。
     * 3. 发 AE_PRECAPTURE_TRIGGER_START，让 HAL 做预曝光/预闪准备。
     * 4. 等 AE 进入并离开 PRECAPTURE 后，再发真正的 STILL_CAPTURE。
     * 5. 拍完取消 trigger 并恢复普通预览。
     */
    fun takePhoto() {
        runCatching {
            startFlashCaptureSequence()
        }.onFailure { throwable ->
            Log.e(TAG, "takePhoto failed", throwable)
            updateState {
                copy(
                    status = "拍照失败",
                    lastError = throwable.message ?: throwable.javaClass.simpleName,
                )
            }
        }
    }

    /**
     * TextureView 生命周期回调。
     *
     * SurfaceTexture 可用之前不能创建预览 Surface；SurfaceTexture 被销毁时要关闭相机，
     * 否则 Session 还在向一个失效 Surface 写 buffer，常见表现是黑屏或 session error。
     */
    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeCamera()
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    /**
     * CameraDevice 状态回调。
     *
     * onOpened 之后才真正拿到 CameraDevice；接下来必须创建 CaptureSession，
     * 因为 request 必须依附于某个 Session 的输出 Surface 集合。
     */
    private val deviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            openingCamera = false
            camera.close()
            cameraDevice = null
            updateState { copy(isPreviewing = false, status = "相机连接已断开") }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            openingCamera = false
            camera.close()
            cameraDevice = null
            updateState {
                copy(
                    isPreviewing = false,
                    status = "相机打开失败",
                    lastError = "CameraDevice error=$error",
                )
            }
        }
    }

    /**
     * Capture result 回调。
     *
     * Camera2 是 request/result 模型：App 不断发 request，HAL/Framework 异步返回 result metadata。
     * 这里每隔若干帧读取一次 AF/AE/AWB，既能观察 3A 状态，又避免每一帧都触发 Compose 重组。
     */
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureStarted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            timestamp: Long,
            frameNumber: Long,
        ) {
//            if (isStillFlashRequest(request)) {
//                Log.e(
//                    TAG,
//                    "onCaptureStarted frame=$frameNumber timestamp=$timestamp " +
//                        "aeMode=${aeModeLabel(request.get(CaptureRequest.CONTROL_AE_MODE))}, " +
//                        "flashMode=${flashModeLabel(request.get(CaptureRequest.FLASH_MODE))}",
//                )
//            }
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            val shouldUpdateUi = result.frameNumber % RESULT_UPDATE_INTERVAL == 0L
//            val shouldLog = shouldUpdateUi || isStillFlashRequest(request) || isFlashResultInteresting(result)
//            if (shouldLog) {
//                logCaptureResult("onCaptureCompleted", request, result)
//            }
            runCatching {
                processCaptureState(request, result)
            }.onFailure { throwable ->
                Log.e(TAG, "processCaptureState failed", throwable)
                captureState = CaptureState.PREVIEW
            }
            if (shouldUpdateUi) {
                updateState {
                    copy(
                        frameNumber = result.frameNumber,
                        afState = afStateLabel(result.get(CaptureResult.CONTROL_AF_STATE)),
                        aeState = aeStateLabel(result.get(CaptureResult.CONTROL_AE_STATE)),
                        awbState = awbStateLabel(result.get(CaptureResult.CONTROL_AWB_STATE)),
                    )
                }
            }
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure,
        ) {
//            Log.e(
//                TAG,
//                "onCaptureFailed frame=${failure.frameNumber}, reason=${failure.reason}, " +
//                    "wasImageCaptured=${failure.wasImageCaptured()}, sequenceId=${failure.sequenceId}",
//            )
//            logRequest("failed request", request)
        }
    }

    /**
     * 打开指定方向的相机。
     *
     * 这一步集中展示 Camera2 初始化的关键 metadata：
     * - LENS_FACING：前置/后置/外接。
     * - INFO_SUPPORTED_HARDWARE_LEVEL：硬件能力级别。
     * - SENSOR_ORIENTATION：传感器安装方向。
     * - REQUEST_AVAILABLE_CAPABILITIES：RAW、手动控制、逻辑多摄等能力。
     * - SCALER_STREAM_CONFIGURATION_MAP：可用输出格式、尺寸和帧率相关信息。
     */
    @SuppressLint("MissingPermission")
    private fun openCamera(viewWidth: Int, viewHeight: Int) {
        openingCamera = true
        runCatching {
            val cameraId = selectCameraId(preferredFacing) ?: run {
                openingCamera = false
                updateState { copy(lastError = "没有可用摄像头") }
                return
            }
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: run {
                openingCamera = false
                updateState { copy(lastError = "摄像头没有 StreamConfigurationMap") }
                return
            }

//            val flashAvailable = characteristics.get(
//                CameraCharacteristics.FLASH_INFO_AVAILABLE
//            ) == true
//
//            val aeModes = characteristics.get(
//                CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES
//            )

            val previewSize = choosePreviewSize(map, viewWidth, viewHeight)
            val captureSize = chooseCaptureSize(map)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: preferredFacing
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            activeCameraId = cameraId
//            logCameraCharacteristics(
//                cameraId = cameraId,
//                characteristics = characteristics,
//                flashAvailable = flashAvailable,
//                aeModes = aeModes,
//                previewSize = previewSize,
//                captureSize = captureSize,
//            )

            // 每次切换 cameraId 都重新创建 ImageReader。
            // ImageReader 的尺寸/格式必须参与当前 Session 的 stream 配置，不能在 Session 外临时乱加 target。
            imageReader?.close()
            imageReader = ImageReader.newInstance(
                captureSize.width,
                captureSize.height,
                ImageFormat.JPEG,
                IMAGE_BUFFER_COUNT,
            ).apply {
                setOnImageAvailableListener({ reader -> saveJpeg(reader) }, cameraHandler)
            }

            updateState {
                copy(
                    permissionGranted = true,
                    cameraCount = cameraManager.cameraIdList.size,
                    cameraId = cameraId,
                    lensFacing = lensFacingLabel(facing),
                    hardwareLevel = hardwareLevelLabel(
                        characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL),
                    ),
                    sensorOrientation = "${sensorOrientation}deg",
                    previewSize = previewSize.format(),
                    captureSize = captureSize.format(),
                    capabilities = capabilityLabels(
                        characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
                    ),
                    torchAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                    torchEnabled = false,
                    status = "正在打开 CameraDevice",
                    lastError = null,
                )
            }
            cameraManager.openCamera(cameraId, deviceCallback, cameraHandler)
        }.onFailure { throwable ->
            Log.e(TAG, "openCamera failed", throwable)
            openingCamera = false
            closeCamera()
            updateState {
                copy(
                    isPreviewing = false,
                    status = "打开 CameraDevice 失败",
                    lastError = throwable.message ?: throwable.javaClass.simpleName,
                )
            }
        }
    }

    /**
     * 创建预览 Session。
     *
     * createCaptureSession 的 Surface 列表就是这次会话的“输出合同”：
     * - previewSurface 用于实时预览。
     * - readerSurface 用于 JPEG 拍照。
     *
     * Android P(API 28) 起推荐使用 SessionConfiguration + OutputConfiguration 创建 Session；
     * 老的 createCaptureSession(List<Surface>, StateCallback, Handler) 已被标记 deprecated。
     *
     * 之后无论 repeating request 还是 capture request，addTarget 的 Surface 都必须来自这个列表。
     */
    private fun createPreviewSession() {
        openingCamera = false
        val preview = textureView ?: return
        val texture = preview.surfaceTexture ?: return
        val size = state.previewSize.toSizeOrNull() ?: return
        texture.setDefaultBufferSize(size.width, size.height)
        val surface = Surface(texture)
        previewSurface = surface
        val readerSurface = imageReader?.surface ?: return
        val device = cameraDevice ?: return

        // TEMPLATE_PREVIEW 是系统为预览场景提供的一组默认参数。
        // 这里显式打开连续 AF/AE，方便在页面上观察 3A result metadata。
        previewRequestBuilder = createPreviewRequestBuilder(
            aeMode = CaptureRequest.CONTROL_AE_MODE_ON,
            requestTag = REQUEST_TAG_PREVIEW,
        )
        previewRequestBuilder?.build()?.let { request ->
//            logRequest("preview repeating request", request)
        }

        createCameraCaptureSession(
            device = device,
            surfaces = listOf(surface, readerSurface),
            callback = previewSessionCallback(),
        )
    }

    /**
     * 用新 API 创建 CameraCaptureSession。
     *
     * 新写法把每个 Surface 包装成 OutputConfiguration，再放进 SessionConfiguration：
     * - OutputConfiguration：描述一个输出流，比如预览 Surface 或 JPEG ImageReader Surface。
     * - SessionConfiguration：描述整个 Session 的类型、输出流、回调线程和状态回调。
     *
     * minSdk 仍是 24，所以 API 24-27 只能走旧 API；这里把 deprecated fallback 隔离在一个小方法里。
     */
    private fun createCameraCaptureSession(
        device: CameraDevice,
        surfaces: List<Surface>,
        callback: CameraCaptureSession.StateCallback,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val outputConfigurations = surfaces.map { surface ->
                OutputConfiguration(surface)
            }
            val sessionConfiguration = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigurations,
                cameraExecutor(),
                callback,
            )
            device.createCaptureSession(sessionConfiguration)
        } else {
            createCameraCaptureSessionCompat(device, surfaces, callback)
        }
    }

    @Suppress("DEPRECATION")
    private fun createCameraCaptureSessionCompat(
        device: CameraDevice,
        surfaces: List<Surface>,
        callback: CameraCaptureSession.StateCallback,
    ) {
        device.createCaptureSession(surfaces, callback, cameraHandler)
    }

    /**
     * SessionConfiguration 要求传 Executor，不再直接传 Handler。
     *
     * 这里仍然把回调投递到 cameraHandler 所在线程，保证和 openCamera / request 下发在同一条相机线程上。
     */
    private fun cameraExecutor(): Executor {
        return Executor { command ->
            val handler = cameraHandler
            if (handler != null) {
                handler.post(command)
            } else {
                mainHandler.post(command)
            }
        }
    }

    /** 预览 Session 状态回调。抽成方法后，新旧 createCaptureSession 写法可以共用同一套回调逻辑。 */
    private fun previewSessionCallback(): CameraCaptureSession.StateCallback {
        return object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                session.setRepeatingRequest(
                    previewRequestBuilder?.build() ?: return,
                    captureCallback,
                    cameraHandler,
                )
                updateState { copy(isPreviewing = true, status = "预览 repeating request 已启动") }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                updateState {
                    copy(
                        isPreviewing = false,
                        status = "CaptureSession 配置失败",
                        lastError = "检查输出 Surface、尺寸和格式是否属于同一个 session",
                    )
                }
            }
        }
    }

    /**
     * 第一步：进入闪光拍照流程并触发 AF。
     *
     * 先把 repeating request 切到 ON_ALWAYS_FLASH，是为了让后续 3A result 属于“闪光拍照语境”；
     * 否则点击拍照后队列里还会回调一些普通预览 result，容易误判 AE/AF 状态。
     */
    private fun startFlashCaptureSequence() {
        val session = captureSession ?: return
        val builder = createPreviewRequestBuilder(
            aeMode = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH,
            requestTag = REQUEST_TAG_PREVIEW_FLASH,
        ) ?: return

        captureState = CaptureState.WAITING_AF_LOCK
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
        session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)

        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        builder.setTag(REQUEST_TAG_AF_LOCK)
        val request = builder.build()
//        logRequest("AF lock request", request)
        updateState { copy(status = "正在锁焦，准备触发 AE 预闪") }
        session.capture(request, captureCallback, cameraHandler)

        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        builder.setTag(REQUEST_TAG_PREVIEW_FLASH)
        previewRequestBuilder = builder
    }

    /** 根据 AF/AE result 推进闪光拍照状态机。 */
    private fun processCaptureState(request: CaptureRequest, result: TotalCaptureResult) {
        when (captureState) {
            CaptureState.PREVIEW, CaptureState.PICTURE_TAKEN -> Unit

            CaptureState.WAITING_AF_LOCK -> {
                if (request.tag != REQUEST_TAG_AF_LOCK) return
                val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                if (isAfReadyForCapture(afState)) {
                    Log.e(TAG, "AF ready: frame=${result.frameNumber}, afState=${afStateLabel(afState)}")
                    runPrecaptureSequence()
                }
            }

            CaptureState.WAITING_PRECAPTURE -> {
                if (request.tag != REQUEST_TAG_AE_PRECAPTURE) return
                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                if (aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                    aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                ) {
                    captureState = CaptureState.WAITING_NON_PRECAPTURE
                    Log.e(TAG, "AE precapture started: frame=${result.frameNumber}, aeState=${aeStateLabel(aeState)}")
                } else if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    Log.e(TAG, "AE precapture skipped by HAL: frame=${result.frameNumber}, aeState=${aeStateLabel(aeState)}")
                    captureStillPicture()
                }
            }

            CaptureState.WAITING_NON_PRECAPTURE -> {
                if (!isFlashSequenceRequest(request)) return
                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                    Log.e(TAG, "AE precapture finished: frame=${result.frameNumber}, aeState=${aeStateLabel(aeState)}")
                    captureStillPicture()
                }
            }
        }
    }

    /** 第二步：触发 AE precapture，让 HAL 有机会执行预曝光/预闪准备。 */
    private fun runPrecaptureSequence() {
        val session = captureSession ?: return
        val builder = createPreviewRequestBuilder(
            aeMode = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH,
            requestTag = REQUEST_TAG_AE_PRECAPTURE,
        ) ?: return

        captureState = CaptureState.WAITING_PRECAPTURE
        builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)

        val request = builder.build()
//        logRequest("AE precapture request", request)
        updateState { copy(status = "正在执行 AE 预曝光/预闪") }
        session.capture(request, captureCallback, cameraHandler)

        builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
        builder.setTag(REQUEST_TAG_PREVIEW_FLASH)
        previewRequestBuilder = builder
    }

    /** 第三步：AE 准备完成后下发真正的 JPEG still capture。 */
    private fun captureStillPicture() {
        if (captureState == CaptureState.PICTURE_TAKEN) return

        val device = cameraDevice ?: return
        val reader = imageReader ?: return
        val session = captureSession ?: return

        captureState = CaptureState.PICTURE_TAKEN
        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
            set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
            setTag(REQUEST_TAG_STILL)
        }.build()

//        logRequest("still capture request", request)
        updateState { copy(status = "AE 预闪完成，正在拍照") }
        session.stopRepeating()
        session.abortCaptures()
        session.capture(
            request,
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureStarted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    timestamp: Long,
                    frameNumber: Long,
                ) {
                    Log.e(TAG, "still onCaptureStarted frame=$frameNumber timestamp=$timestamp")
//                    logRequest("still started request", request)
                }

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
//                    logCaptureResult("still onCaptureCompleted", request, result)
                    resumePreviewAfterCapture()
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure,
                ) {
                    Log.e(
                        TAG,
                        "still onCaptureFailed frame=${failure.frameNumber}, reason=${failure.reason}, " +
                            "wasImageCaptured=${failure.wasImageCaptured()}, sequenceId=${failure.sequenceId}",
                    )
//                    logRequest("still failed request", request)
                    resumePreviewAfterCapture()
                }
            },
            cameraHandler,
        )
    }

    /** 第四步：拍照结束后取消 trigger，恢复普通 AE 预览。 */
    private fun resumePreviewAfterCapture() {
        val session = captureSession ?: return
        val unlockBuilder = createPreviewRequestBuilder(
            aeMode = CaptureRequest.CONTROL_AE_MODE_ON,
            requestTag = REQUEST_TAG_UNLOCK,
        ) ?: return

        unlockBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
        unlockBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL)
        session.capture(unlockBuilder.build(), captureCallback, cameraHandler)

        val previewBuilder = createPreviewRequestBuilder(
            aeMode = CaptureRequest.CONTROL_AE_MODE_ON,
            requestTag = REQUEST_TAG_PREVIEW,
        ) ?: return
        previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        previewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
        session.setRepeatingRequest(previewBuilder.build(), captureCallback, cameraHandler)
        previewRequestBuilder = previewBuilder
        captureState = CaptureState.PREVIEW
        updateState { copy(status = "拍照完成，已恢复预览") }
    }

    /** 创建干净的预览 request builder，不携带 Torch 开关留下的 FLASH_MODE_OFF/TORCH。 */
    private fun createPreviewRequestBuilder(
        aeMode: Int,
        requestTag: String,
    ): CaptureRequest.Builder? {
        val device = cameraDevice ?: return null
        val surface = previewSurface ?: return null
        return device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, aeMode)
            setTag(requestTag)
        }
    }

    /**
     * 消费 ImageReader 中的 JPEG buffer。
     *
     * 关键点是 finally 中的 image.close()：
     * ImageReader 内部 buffer 数量有限，本 Demo 设置为 2。
     * 如果 App 拿到 Image 后不 close，HAL 很快就没有空 buffer 可写，拍照/预览会卡住。
     */
    private fun saveJpeg(reader: ImageReader) {
        val image = reader.acquireNextImage() ?: return
        val file = nextPhotoFile()
        try {
            Log.e(TAG, "ImageReader image available: format=${image.format}, size=${image.width}x${image.height}")
            val buffer = image.planes.first().buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            FileOutputStream(file).use { it.write(bytes) }
            updateState {
                copy(
                    status = "JPEG 已保存",
                    lastPhotoPath = file.absolutePath,
                    lastError = null,
                )
            }
            Log.e(TAG, "JPEG saved: ${file.absolutePath}, bytes=${bytes.size}")
        } catch (throwable: Throwable) {
            Log.e(TAG, "saveJpeg failed", throwable)
            updateState {
                copy(
                    status = "保存 JPEG 失败",
                    lastError = throwable.message ?: throwable.javaClass.simpleName,
                )
            }
        } finally {
            image.close()
        }
    }

    /**
     * 关闭 Camera2 资源。
     *
     * 顺序上先关 Session，再关 Device，最后关 ImageReader。
     * 真实业务可进一步用 Semaphore 防止 open/close 并发；本 Demo 用 openingCamera 做轻量防重入。
     */
    private fun closeCamera() {
        openingCamera = false
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        previewRequestBuilder = null
        previewSurface?.release()
        previewSurface = null
    }

    /** 创建相机专用线程。 */
    private fun startBackgroundThread() {
        if (cameraThread != null) return
        cameraThread = HandlerThread("Camera2Demo").also { thread ->
            thread.start()
            cameraHandler = Handler(thread.looper)
        }
    }

    /** 结束相机专用线程。quitSafely 会等消息队列中已开始处理的任务自然收尾。 */
    private fun stopBackgroundThread() {
        cameraThread?.quitSafely()
        cameraThread = null
        cameraHandler = null
    }

    /** Android 6.0+ CAMERA 是危险权限，openCamera 前必须确认已授权。 */
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * 选择 Camera ID。
     *
     * Camera ID 是字符串，不能假设 "0" 一定是后置、"1" 一定是前置。
     * 正确方式是遍历 cameraIdList 后读取 LENS_FACING。
     */
    private fun selectCameraId(facing: Int): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing
        } ?: cameraManager.cameraIdList.firstOrNull()
    }

    /**
     * 选择预览尺寸。
     *
     * 这里只做教学版选择策略：
     * - 优先限制在 1920x1080 以内，避免预览过大导致带宽/功耗过高。
     * - 在候选尺寸里找最接近 TextureView 宽高比的尺寸，降低拉伸感。
     *
     * 生产代码还需要考虑屏幕旋转、View 实际显示区域、目标帧率、视频录制组合等。
     */
    private fun choosePreviewSize(map: StreamConfigurationMap, viewWidth: Int, viewHeight: Int): Size {
        val sizes = map.getOutputSizes(SurfaceTexture::class.java).orEmpty()
        val viewRatio = if (viewWidth > 0 && viewHeight > 0) {
            viewWidth.toFloat() / viewHeight.toFloat()
        } else {
            DEFAULT_PREVIEW_RATIO
        }
        return sizes
            .filter { it.width <= MAX_PREVIEW_WIDTH && it.height <= MAX_PREVIEW_HEIGHT }
            .minByOrNull { size ->
                kotlin.math.abs(size.width.toFloat() / size.height.toFloat() - viewRatio)
            }
            ?: sizes.firstOrNull()
            ?: Size(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT)
    }

    /**
     * 选择拍照尺寸。
     *
     * Demo 直接选最大 JPEG，方便看清输出能力；生产代码通常会在质量、耗时、内存、上传体积之间折中。
     */
    private fun chooseCaptureSize(map: StreamConfigurationMap): Size {
        return map.getOutputSizes(ImageFormat.JPEG)
            ?.maxByOrNull { it.width * it.height }
            ?: Size(DEFAULT_PREVIEW_WIDTH, DEFAULT_PREVIEW_HEIGHT)
    }

    /**
     * 计算 JPEG_ORIENTATION。
     *
     * JPEG 方向由传感器安装方向、设备当前旋转、镜头方向共同决定。
     * 如果这里算错，拍出来的图会横着、倒着，或者前置照片方向异常。
     */
    private fun jpegOrientation(): Int {
        val deviceRotation = currentDisplayRotation()
        val degrees = when (deviceRotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val isFront = activeCameraId?.let {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        } == true
        return if (isFront) {
            (sensorOrientation + degrees) % FULL_ROTATION
        } else {
            (sensorOrientation - degrees + FULL_ROTATION) % FULL_ROTATION
        }
    }

    /**
     * 获取当前屏幕旋转。
     *
     * defaultDisplay 已废弃，但 minSdk 24 的教学 Demo 用它兼容性最好；
     * 更现代的业务代码可以在 Android R+ 使用 DisplayManager/WindowMetrics 相关能力。
     */
    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int {
        return appContext.getSystemService(WindowManager::class.java).defaultDisplay.rotation
    }

    /** 保存到 app 专属外部图片目录，不需要额外申请读写外部存储权限。 */
    private fun nextPhotoFile(): File {
        val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: appContext.filesDir
        val name = "camera2-demo-${PHOTO_TIME_FORMAT.format(Date())}.jpg"
        return File(dir, name)
    }

    /** 统一从任意 Camera2 回调线程切回主线程更新 UI state。 */
    private fun updateState(block: CameraUiState.() -> CameraUiState) {
        val next = state.block()
        state = next
        mainHandler.post { onStateChanged(next) }
    }

    private fun Size.format(): String = "${width}x${height}"

    /** UI state 中用字符串展示尺寸，创建 SurfaceTexture buffer 时再转回 Size。 */
    private fun String.toSizeOrNull(): Size? {
        val parts = split('x')
        if (parts.size != 2) return null
        return Size(parts[0].toIntOrNull() ?: return null, parts[1].toIntOrNull() ?: return null)
    }

    /** 把 CameraCharacteristics.LENS_FACING 的整数常量转成便于页面展示的文案。 */
    private fun lensFacingLabel(facing: Int): String {
        return when (facing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "前置"
            CameraCharacteristics.LENS_FACING_BACK -> "后置"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "外接"
            else -> "未知"
        }
    }

    /** 硬件级别决定 Camera2 能力上限，也是排查兼容问题时最先看的 metadata 之一。 */
    @SuppressLint("InlinedApi")
    private fun hardwareLevelLabel(level: Int?): String {
        return when (level) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "-"
        }
    }

    /**
     * REQUEST_AVAILABLE_CAPABILITIES 是能力开关列表。
     *
     * 例如：
     * - MANUAL_SENSOR 表示可手动控制曝光时间、ISO 等。
     * - RAW 表示可输出 RAW。
     * - LOGICAL_MULTI_CAMERA 表示当前 ID 可能是逻辑多摄。
     */
    @SuppressLint("InlinedApi")
    private fun capabilityLabels(capabilities: IntArray?): List<String> {
        return capabilities?.map { capability: Int ->
            when (capability) {
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "PRIVATE_REPROCESSING"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV_REPROCESSING"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONOCHROME"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "SECURE_IMAGE_DATA"
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> "SYSTEM_CAMERA"
                else -> "CAPABILITY_$capability"
            }
        }.orEmpty()
    }

    /**
     * 打印静态能力。
     *
     * 这组日志回答“硬件/Camera HAL 是否宣称支持闪光灯”：
     * - FLASH_INFO_AVAILABLE 必须为 true。
     * - CONTROL_AE_AVAILABLE_MODES 里要包含 ON_ALWAYS_FLASH。
     * 如果这两项都满足，但拍照 still result 没有 FLASH_STATE_FIRED，就继续看 result 状态机日志。
     */
    private fun logCameraCharacteristics(
        cameraId: String,
        characteristics: CameraCharacteristics,
        flashAvailable: Boolean,
        aeModes: IntArray?,
        previewSize: Size,
        captureSize: Size,
    ) {
        val supportAlwaysFlash = aeModes?.contains(CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH) == true
        val supportAutoFlash = aeModes?.contains(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH) == true
        Log.e(
            TAG,
            "characteristics: cameraId=$cameraId, " +
                "lensFacing=${lensFacingLabel(characteristics.get(CameraCharacteristics.LENS_FACING) ?: -1)}, " +
                "hardwareLevel=${hardwareLevelLabel(characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))}, " +
                "sensorOrientation=${characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)}, " +
                "flashAvailable=$flashAvailable, " +
                "supportAlwaysFlash=$supportAlwaysFlash, supportAutoFlash=$supportAutoFlash, " +
                "aeModes=${aeModes?.joinToString(prefix = "[", postfix = "]") { aeModeLabel(it) }}, " +
                "previewSize=${previewSize.format()}, captureSize=${captureSize.format()}, " +
                "capabilities=${capabilityLabels(characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES))}",
        )
    }

    /** 打印 request 里和闪光灯最相关的控制项。 */
    private fun logRequest(label: String, request: CaptureRequest) {
        Log.e(
            TAG,
            "$label: " +
                "tag=${request.tag}, " +
                "aeMode=${aeModeLabel(request.get(CaptureRequest.CONTROL_AE_MODE))}, " +
                "afMode=${afModeLabel(request.get(CaptureRequest.CONTROL_AF_MODE))}, " +
                "flashMode=${flashModeLabel(request.get(CaptureRequest.FLASH_MODE))}, " +
                "aePrecaptureTrigger=${aePrecaptureTriggerLabel(request.get(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER))}, " +
                "afTrigger=${afTriggerLabel(request.get(CaptureRequest.CONTROL_AF_TRIGGER))}, " +
                "jpegOrientation=${request.get(CaptureRequest.JPEG_ORIENTATION)}",
        )
    }

    /** 打印 result 里的 3A 和 Flash 状态，用于判断 HAL 是否真的 fire。 */
    private fun logCaptureResult(
        label: String,
        request: CaptureRequest,
        result: TotalCaptureResult,
    ) {
        Log.e(
            TAG,
            "$label: frame=${result.frameNumber}, " +
                "requestTag=${request.tag}, " +
                "requestAeMode=${aeModeLabel(request.get(CaptureRequest.CONTROL_AE_MODE))}, " +
                "requestFlashMode=${flashModeLabel(request.get(CaptureRequest.FLASH_MODE))}, " +
                "resultAeState=${aeStateLabel(result.get(CaptureResult.CONTROL_AE_STATE))}, " +
                "resultAfState=${afStateLabel(result.get(CaptureResult.CONTROL_AF_STATE))}, " +
                "resultAwbState=${awbStateLabel(result.get(CaptureResult.CONTROL_AWB_STATE))}, " +
                "flashState=${flashStateLabel(result.get(CaptureResult.FLASH_STATE))}",
        )
    }

    /** still request 或强制/自动闪光 request 都要重点记录。 */
    private fun isStillFlashRequest(request: CaptureRequest): Boolean {
        val aeMode = request.get(CaptureRequest.CONTROL_AE_MODE)
        val flashMode = request.get(CaptureRequest.FLASH_MODE)
        return aeMode == CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH ||
            aeMode == CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH ||
            flashMode == CaptureRequest.FLASH_MODE_SINGLE ||
            flashMode == CaptureRequest.FLASH_MODE_TORCH
    }

    /** 这些 result 表示闪光灯状态机出现了关键变化，不能等 30 帧采样。 */
    private fun isFlashResultInteresting(result: TotalCaptureResult): Boolean {
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        val flashState = result.get(CaptureResult.FLASH_STATE)
        return aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                flashState == CaptureResult.FLASH_STATE_CHARGING ||
                flashState == CaptureResult.FLASH_STATE_READY ||
                flashState == CaptureResult.FLASH_STATE_FIRED ||
                flashState == CaptureResult.FLASH_STATE_PARTIAL
    }

    /** 判断 result 是否属于本次闪光拍照状态机，过滤队列里尚未消费完的普通预览帧。 */
    private fun isFlashSequenceRequest(request: CaptureRequest): Boolean {
        return when (request.tag) {
            REQUEST_TAG_PREVIEW_FLASH,
            REQUEST_TAG_AF_LOCK,
            REQUEST_TAG_AE_PRECAPTURE,
            REQUEST_TAG_STILL -> true
            else -> false
        }
    }

    /** 连续对焦模式下，不同 HAL 返回的 AF 状态略有差异；这些状态都可以继续 AE precapture。 */
    private fun isAfReadyForCapture(state: Int?): Boolean {
        return state == null ||
            state == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
            state == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
            state == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
            state == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED ||
            state == CaptureResult.CONTROL_AF_STATE_INACTIVE
    }

    private fun aeModeLabel(mode: Int?): String {
        return when (mode) {
            CaptureRequest.CONTROL_AE_MODE_OFF -> "OFF"
            CaptureRequest.CONTROL_AE_MODE_ON -> "ON"
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH -> "ON_AUTO_FLASH"
            CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH -> "ON_ALWAYS_FLASH"
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE -> "ON_AUTO_FLASH_REDEYE"
            else -> mode?.let { "AE_MODE_$it" } ?: "-"
        }
    }

    private fun afModeLabel(mode: Int?): String {
        return when (mode) {
            CaptureRequest.CONTROL_AF_MODE_OFF -> "OFF"
            CaptureRequest.CONTROL_AF_MODE_AUTO -> "AUTO"
            CaptureRequest.CONTROL_AF_MODE_MACRO -> "MACRO"
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "CONTINUOUS_VIDEO"
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "CONTINUOUS_PICTURE"
            CaptureRequest.CONTROL_AF_MODE_EDOF -> "EDOF"
            else -> mode?.let { "AF_MODE_$it" } ?: "-"
        }
    }

    private fun flashModeLabel(mode: Int?): String {
        return when (mode) {
            CaptureRequest.FLASH_MODE_OFF -> "OFF"
            CaptureRequest.FLASH_MODE_SINGLE -> "SINGLE"
            CaptureRequest.FLASH_MODE_TORCH -> "TORCH"
            else -> mode?.let { "FLASH_MODE_$it" } ?: "-"
        }
    }

    private fun flashStateLabel(state: Int?): String {
        return when (state) {
            CaptureResult.FLASH_STATE_UNAVAILABLE -> "UNAVAILABLE"
            CaptureResult.FLASH_STATE_CHARGING -> "CHARGING"
            CaptureResult.FLASH_STATE_READY -> "READY"
            CaptureResult.FLASH_STATE_FIRED -> "FIRED"
            CaptureResult.FLASH_STATE_PARTIAL -> "PARTIAL"
            else -> state?.let { "FLASH_STATE_$it" } ?: "-"
        }
    }

    private fun aePrecaptureTriggerLabel(trigger: Int?): String {
        return when (trigger) {
            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE -> "IDLE"
            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START -> "START"
            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL -> "CANCEL"
            else -> trigger?.let { "AE_PRECAPTURE_$it" } ?: "-"
        }
    }

    private fun afTriggerLabel(trigger: Int?): String {
        return when (trigger) {
            CaptureRequest.CONTROL_AF_TRIGGER_IDLE -> "IDLE"
            CaptureRequest.CONTROL_AF_TRIGGER_START -> "START"
            CaptureRequest.CONTROL_AF_TRIGGER_CANCEL -> "CANCEL"
            else -> trigger?.let { "AF_TRIGGER_$it" } ?: "-"
        }
    }

    /** 自动对焦状态，常用于判断用户点按对焦或拍照前锁焦是否完成。 */
    private fun afStateLabel(state: Int?): String {
        return when (state) {
            CaptureResult.CONTROL_AF_STATE_INACTIVE -> "INACTIVE"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> "PASSIVE_SCAN"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> "PASSIVE_FOCUSED"
            CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> "ACTIVE_SCAN"
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> "FOCUSED_LOCKED"
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> "NOT_FOCUSED_LOCKED"
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> "PASSIVE_UNFOCUSED"
            else -> "-"
        }
    }

    /** 自动曝光状态，拍照前如果需要闪光灯或预曝光，会通过 AE state 体现。 */
    private fun aeStateLabel(state: Int?): String {
        return when (state) {
            CaptureResult.CONTROL_AE_STATE_INACTIVE -> "INACTIVE"
            CaptureResult.CONTROL_AE_STATE_SEARCHING -> "SEARCHING"
            CaptureResult.CONTROL_AE_STATE_CONVERGED -> "CONVERGED"
            CaptureResult.CONTROL_AE_STATE_LOCKED -> "LOCKED"
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED -> "FLASH_REQUIRED"
            CaptureResult.CONTROL_AE_STATE_PRECAPTURE -> "PRECAPTURE"
            else -> "-"
        }
    }

    /** 自动白平衡状态，白平衡收敛后色温通常更稳定。 */
    private fun awbStateLabel(state: Int?): String {
        return when (state) {
            CaptureResult.CONTROL_AWB_STATE_INACTIVE -> "INACTIVE"
            CaptureResult.CONTROL_AWB_STATE_SEARCHING -> "SEARCHING"
            CaptureResult.CONTROL_AWB_STATE_CONVERGED -> "CONVERGED"
            CaptureResult.CONTROL_AWB_STATE_LOCKED -> "LOCKED"
            else -> "-"
        }
    }

    companion object {
        // ImageReader 队列深度。值越大越不容易卡住 HAL，但内存占用也越高。
        private const val IMAGE_BUFFER_COUNT = 2
        // 预览兜底尺寸，只有设备未返回 SurfaceTexture 输出尺寸时才会用到。
        private const val DEFAULT_PREVIEW_WIDTH = 1280
        private const val DEFAULT_PREVIEW_HEIGHT = 720
        // 教学 Demo 限制预览尺寸，避免部分高像素设备默认选择超大预览流。
        private const val MAX_PREVIEW_WIDTH = 1920
        private const val MAX_PREVIEW_HEIGHT = 1080
        private const val DEFAULT_PREVIEW_RATIO = 16f / 9f
        private const val FULL_ROTATION = 360
        // 每 30 帧更新一次 3A 状态，减少 UI 重组频率。
        private const val RESULT_UPDATE_INTERVAL = 30L
        private const val TAG = "Camera-2"
        private const val REQUEST_TAG_PREVIEW = "preview"
        private const val REQUEST_TAG_PREVIEW_FLASH = "preview_flash"
        private const val REQUEST_TAG_AF_LOCK = "af_lock"
        private const val REQUEST_TAG_AE_PRECAPTURE = "ae_precapture"
        private const val REQUEST_TAG_STILL = "still"
        private const val REQUEST_TAG_UNLOCK = "unlock"
        private val PHOTO_TIME_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
