package com.example.camera

/**
 * Camera 页面展示用的不可变状态快照。
 *
 * Camera2 本身是典型异步 API：
 * - 权限回调来自 ActivityResult。
 * - CameraDevice.StateCallback 表示设备打开/断开/错误。
 * - CameraCaptureSession.StateCallback 表示 Session 配置结果。
 * - CaptureCallback 持续返回每一帧 result metadata。
 * - ImageReader 回调负责消费 JPEG buffer。
 *
 * 把这些回调都收敛成一个 data class，可以让 Compose UI 只关心“当前相机状态是什么”，
 * 避免 UI 层直接持有 CameraDevice、CameraCaptureSession 等需要严格释放的对象。
 */
class CameraUiState(
    /** 是否已经获得 android.permission.CAMERA 运行时权限。 */
    val permissionGranted: Boolean = false,
    /** CameraManager 枚举出的 camera id 数量，通常后置/前置各一个，多摄设备可能更多。 */
    val cameraCount: Int = 0,
    /** 当前打开的 Camera ID。注意它是系统分配的字符串，不保证是连续数字。 */
    val cameraId: String = "-",
    /** 当前镜头方向：前置、后置、外接或未知。 */
    val lensFacing: String = "-",
    /** Camera2 硬件级别：LEGACY、LIMITED、FULL、LEVEL_3、EXTERNAL。 */
    val hardwareLevel: String = "-",
    /** Sensor 固定安装方向，用于计算 JPEG_ORIENTATION 和预览旋转。 */
    val sensorOrientation: String = "-",
    /** 当前预览 SurfaceTexture 使用的尺寸。 */
    val previewSize: String = "-",
    /** ImageReader/JPEG 拍照流使用的尺寸。 */
    val captureSize: String = "-",
    /** 最近一次从 CaptureCallback 收到的 frame number，用来观察 request/result 是否持续流动。 */
    val frameNumber: Long = 0L,
    /** 自动对焦状态，来自 CaptureResult.CONTROL_AF_STATE。 */
    val afState: String = "-",
    /** 自动曝光状态，来自 CaptureResult.CONTROL_AE_STATE。 */
    val aeState: String = "-",
    /** 自动白平衡状态，来自 CaptureResult.CONTROL_AWB_STATE。 */
    val awbState: String = "-",
    /** REQUEST_AVAILABLE_CAPABILITIES，决定 RAW、手动控制、逻辑多摄等能力是否可用。 */
    val capabilities: List<String> = emptyList(),
    /** 当前 camera 是否声明有闪光灯。 */
    val torchAvailable: Boolean = false,
    /** 预览 request 是否正在使用 FLASH_MODE_TORCH。 */
    val torchEnabled: Boolean = false,
    /** repeating preview request 是否已经成功下发。 */
    val isPreviewing: Boolean = false,
    /** 给用户看的当前链路状态。 */
    val status: String = "等待相机权限",
    /** 最近一次拍照保存到 app external files 的绝对路径。 */
    val lastPhotoPath: String? = null,
    /** 最近一次可恢复错误，展示在页面上帮助调试。 */
    val lastError: String? = null,
)
