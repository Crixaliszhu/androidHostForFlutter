package com.example.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.alibaba.android.arouter.facade.annotation.Route

/**
 * Camera2 教学页面。
 *
 * 这个 Activity 只承担三件事：
 * 1. 通过 ARouter 暴露页面入口。
 * 2. 处理 CAMERA 运行时权限和 Activity 生命周期。
 * 3. 用 Compose 展示 Camera2DemoController 输出的状态。
 *
 * 真正的 Camera2 操作放在 Camera2DemoController 中，避免 Activity 变成“大杂烩”。
 */
@Route(path = CameraRouterPaths.CAMERA_DEMO)
class CameraDemoActivity : ComponentActivity() {
    // Compose 会自动观察 mutableStateOf；Controller 每次推新状态，页面都会按需重组。
    private var uiState by mutableStateOf(CameraUiState())

    // TextureView 是预览 Surface 的宿主。它由 AndroidView 创建，所以这里保存引用给生命周期回调复用。
    private var previewView: TextureView? = null

    // lazy 确保 Controller 在 Activity onCreate 后才创建，并且整个页面生命周期内只有一个实例。
    private val cameraController by lazy {
        Camera2DemoController(this) { state ->
            uiState = state
        }
    }

    // 使用 Activity Result API 请求 CAMERA 权限，避免重写 onRequestPermissionsResult。
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraController.markPermission(granted)
        // 权限回来时 TextureView 可能已经创建好了；如果是这样，立刻继续 open camera 流程。
        if (granted) {
            previewView?.let(cameraController::start)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 先把当前权限状态同步给 UI：已授权就显示预览容器，未授权就显示授权按钮。
        cameraController.markPermission(hasCameraPermission())
        setContent {
            MaterialTheme {
                Surface(color = Color(0xFFF6F7F9)) {
                    CameraDemoScreen(
                        state = uiState,
                        onPreviewReady = { preview ->
                            previewView = preview
                            // AndroidView 创建 TextureView 后，Controller 才有可用的预览载体。
                            // 如果权限已经存在，就开始等待 SurfaceTexture 并打开 CameraDevice。
                            if (uiState.permissionGranted == true) {
                                cameraController.start(preview)
                            }
                        },
                        onRequestPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onTakePhoto = cameraController::takePhoto,
                        onSwitchCamera = cameraController::switchCamera,
                        onToggleTorch = cameraController::toggleTorch,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 页面回到前台时重新打开相机。Controller 内部有防重入逻辑，重复调用是安全的。
        if (hasCameraPermission()) {
            previewView?.let(cameraController::start)
        }
    }

    override fun onPause() {
        // Camera 是系统独占硬件资源，进入后台前释放，避免影响其它 App 或下一个页面。
        cameraController.stop()
        super.onPause()
    }

    /** 只做权限检查，不触发请求；请求动作集中在按钮和 ActivityResultLauncher。 */
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun CameraDemoScreen(
    state: CameraUiState,
    onPreviewReady: (TextureView) -> Unit,
    onRequestPermission: () -> Unit,
    onTakePhoto: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleTorch: () -> Unit,
) {
    // 页面整体可滚动，避免小屏手机上预览、控制区和 metadata 信息互相挤压。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Camera2 开发要点",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "从权限、设备枚举、能力查询、Session、Request、ImageReader 到释放链路。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4F5965),
        )
        PreviewPanel(
            permissionGranted = state.permissionGranted,
            onPreviewReady = onPreviewReady,
            onRequestPermission = onRequestPermission,
        )
        ControlPanel(
            state = state,
            onTakePhoto = onTakePhoto,
            onSwitchCamera = onSwitchCamera,
            onToggleTorch = onToggleTorch,
        )
        CameraInfoPanel(state = state)
        CameraStepsPanel()
    }
}

@Composable
private fun PreviewPanel(
    permissionGranted: Boolean,
    onPreviewReady: (TextureView) -> Unit,
    onRequestPermission: () -> Unit,
) {
    // 预览区使用固定 3:4 比例，接近手机竖屏拍摄体验。
    // 真正的 Camera 输出尺寸由 Controller 通过 StreamConfigurationMap 决定。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .background(Color.Black, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (permissionGranted) {
            // Compose 没有直接给 Camera2 使用的 Surface，必须通过 AndroidView 嵌入传统 View。
            // TextureView.surfaceTexture 会被包装成 Surface 后交给 CameraCaptureSession。
            AndroidView(
                factory = { context ->
                    TextureView(context).also(onPreviewReady)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 未授权时不创建 CameraDevice，只展示权限入口，符合 Android 隐私模型。
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "需要 CAMERA 权限才能打开 CameraDevice",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRequestPermission) {
                    Text("授予相机权限")
                }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    state: CameraUiState,
    onTakePhoto: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleTorch: () -> Unit,
) {
    // 控制按钮按 Camera2 状态启用/禁用，避免在 session 未就绪时下发无效 request。
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = state.permissionGranted && state.isPreviewing,
                modifier = Modifier.weight(1f),
                onClick = onTakePhoto,
            ) {
                Text("拍照")
            }
            OutlinedButton(
                enabled = state.permissionGranted,
                modifier = Modifier.weight(1f),
                onClick = onSwitchCamera,
            ) {
                Text("切换前后摄")
            }
        }
        OutlinedButton(
            enabled = state.permissionGranted && state.torchAvailable,
            modifier = Modifier.fillMaxWidth(),
            onClick = onToggleTorch,
        ) {
            Text(if (state.torchEnabled) "关闭闪光灯 Torch" else "打开闪光灯 Torch")
        }
        StatusText(state)
    }
}

@Composable
private fun StatusText(state: CameraUiState) {
    // 状态区用于观察异步链路：打开设备、配置 session、下发 request、保存 JPEG、异常恢复。
    val statusColor = if (state.lastError == null) Color(0xFF245A3D) else Color(0xFF9E2F2F)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEAF0ED), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(text = state.status, color = statusColor, fontWeight = FontWeight.SemiBold)
        state.lastError?.let {
            Text(text = it, color = Color(0xFF9E2F2F), style = MaterialTheme.typography.bodySmall)
        }
        state.lastPhotoPath?.let {
            Text(text = "最近照片：$it", color = Color(0xFF47515C), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CameraInfoPanel(state: CameraUiState) {
    // 这里展示的是 CameraCharacteristics 和 CaptureResult 的关键字段。
    // 这些信息在排查“黑屏、变形、拍照慢、能力不一致”时很有用。
    SectionCard(title = "运行时信息") {
        InfoRow("Camera 数量", state.cameraCount.toString())
        InfoRow("当前 ID", state.cameraId)
        InfoRow("镜头方向", state.lensFacing)
        InfoRow("硬件级别", state.hardwareLevel)
        InfoRow("Sensor 方向", state.sensorOrientation)
        InfoRow("预览流", state.previewSize)
        InfoRow("拍照流", state.captureSize)
        InfoRow("闪光灯", if (state.torchAvailable) "支持" else "不支持")
        InfoRow("Frame", state.frameNumber.toString())
        InfoRow("AF", state.afState)
        InfoRow("AE", state.aeState)
        InfoRow("AWB", state.awbState)
        Spacer(modifier = Modifier.height(8.dp))
        Text("能力标签", fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.capabilities.ifEmpty { listOf("-") }.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier
                        .background(Color(0xFFE6EEF8), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF243B55),
                )
            }
        }
    }
}

@Composable
private fun CameraStepsPanel() {
    // 把源码里的关键步骤也显示到页面上，便于边运行边对照代码阅读。
    SectionCard(title = "代码观察点") {
        val steps = listOf(
            "Manifest 声明 CAMERA 权限与 camera.any feature",
            "CameraManager 枚举 ID，并读取 CameraCharacteristics",
            "StreamConfigurationMap 选择 TextureView 预览流和 JPEG 拍照流",
            "CameraDevice.createCaptureSession 绑定预览 Surface 与 ImageReader Surface",
            "setRepeatingRequest 驱动连续预览，capture 下发单帧拍照",
            "CaptureCallback 读取 AF/AE/AWB result metadata，观察 3A 收敛状态",
            "ImageReader 最大 buffer 数为 2，保存后必须 image.close()",
            "onPause 释放 CaptureSession、CameraDevice、ImageReader 和 HandlerThread",
        )
        for (index in steps.indices) {
            val text = steps[index]
            Text(
                text = "${index + 1}. $text",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF303841),
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 简单复用的内容容器，保持页面结构清楚；不承载任何 Camera 逻辑。
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    // 固定 label 宽度让 metadata 列表更容易扫读。
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(88.dp),
            color = Color(0xFF68717C),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(text = value, color = Color(0xFF20262D), style = MaterialTheme.typography.bodyMedium)
    }
}
