package com.example.camera.water

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.example.camera.CameraRouterPaths
import com.example.camera.databinding.ActivityWatermarkCameraBinding
import com.example.camera.water.intent.WaterCameraActionHandler
import com.example.camera.water.intent.WaterCameraEffect
import com.example.camera.water.intent.WaterCameraUserIntent
import com.example.camera.water.permission.PermissionUtils
import com.example.camera.water.vm.WaterCameraViewModel
import kotlinx.coroutines.launch

/**
 * 简单版水印相机页面，使用 XML DataBinding 实现。
 *
 * Activity 是纯 View 层：负责绑定布局、申请权限、执行 Camera2 操作和渲染 ViewModel 状态。
 * 业务状态流转、定位、生成水印图、保存相册都放在 ViewModel/Controller 中。
 */
@Route(path = CameraRouterPaths.WATERMARK_CAMERA)
class WatermarkCameraActivity : ComponentActivity(), WaterCameraActionHandler {

    private lateinit var binding: ActivityWatermarkCameraBinding
    private val viewModel by viewModels<WaterCameraViewModel>()

    private val cameraController by lazy {
        WatermarkCameraController(
            context = this,
            onCameraReady = {
                viewModel.dispatch(WaterCameraUserIntent.CameraReady)
            },
            onPhotoCaptured = { jpegBytes, jpegOrientation ->
                viewModel.dispatch(
                    WaterCameraUserIntent.PhotoCaptured(
                        jpegBytes = jpegBytes,
                        jpegOrientation = jpegOrientation,
                    ),
                )
            },
            onError = { message ->
                viewModel.dispatch(WaterCameraUserIntent.CameraError(message))
            },
        )
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.dispatch(WaterCameraUserIntent.CameraPermissionResult(granted))
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.dispatch(WaterCameraUserIntent.LocationPermissionResult(result.values.any { it }))
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.dispatch(WaterCameraUserIntent.StoragePermissionResult(granted))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatermarkCameraBinding.inflate(layoutInflater).apply {
            vm = viewModel
            actions = this@WatermarkCameraActivity
            lifecycleOwner = this@WatermarkCameraActivity
            executePendingBindings()
        }
        setContentView(binding.root)
        configureSystemBars()
        collectMviState()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = onBack()
            },
        )
    }

    /**
     * targetSdk 35 在 Android 15 上会默认进入边到边显示，内容可能绘制到状态栏/导航栏下面。
     * 这里主动按系统栏高度给根布局加 padding，让顶部工具栏和底部按钮始终避开系统栏。
     */
    private fun configureSystemBars() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )

        val root = binding.watermarkRoot
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                baseLeft,
                baseTop + systemBars.top,
                baseRight,
                baseBottom + systemBars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun collectMviState() {
        lifecycleScope.launch {
            // onResume 执行期间 Lifecycle 可能仍为 STARTED。此时消费启动相机的 Effect，
            // 会被 RESUMED 检查直接丢弃，导致黑屏；等 ON_RESUME 后再消费 Channel 中的指令。
            // 权限弹窗或切后台期间也暂停消费，返回前台后再执行相机和权限操作。
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.effects.collect(::handleEffect)
            }
        }
    }

    private fun handleEffect(effect: WaterCameraEffect) {
        when (effect) {
            WaterCameraEffect.RequestCameraPermission -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }

            WaterCameraEffect.RequestLocationPermission -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }

            WaterCameraEffect.RequestStoragePermission -> {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            WaterCameraEffect.StartCameraPreview -> startCameraPreviewIfResumed()
            WaterCameraEffect.CapturePhoto -> {
                if (!cameraController.takePhoto()) {
                    viewModel.dispatch(WaterCameraUserIntent.CameraCaptureUnavailable)
                }
            }

            WaterCameraEffect.ResumeCameraPreview -> {
                binding.photoPreview.setImageDrawable(null)
                cameraController.resumePreview()
                startCameraPreviewIfResumed()
            }

            WaterCameraEffect.OpenLocationSettings -> {
                startActivity(android.content.Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }

            WaterCameraEffect.FinishPage -> finish()
            is WaterCameraEffect.Toast -> {
                Toast.makeText(this, effect.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCameraPreviewIfResumed() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            cameraController.start(binding.cameraPreview)
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.dispatch(WaterCameraUserIntent.PageStarted)
    }

    override fun onResume() {
        super.onResume()
        viewModel.dispatch(
            WaterCameraUserIntent.PageResumed(
                hasCameraPermission = PermissionUtils.hasPermission(
                    applicationContext,
                    Manifest.permission.CAMERA,
                ),
            ),
        )
    }

    override fun onPause() {
        cameraController.stop()
        super.onPause()
    }

    override fun onStop() {
        viewModel.dispatch(WaterCameraUserIntent.PageStopped)
        super.onStop()
    }

    override fun onBack() {
        viewModel.dispatch(WaterCameraUserIntent.BackClicked)
    }

    override fun onLocate() {
        viewModel.dispatch(WaterCameraUserIntent.LocateClicked)
    }

    override fun onCapture() {
        viewModel.dispatch(WaterCameraUserIntent.CaptureClicked)
    }

    override fun onDiscard() {
        viewModel.dispatch(WaterCameraUserIntent.DiscardClicked)
    }

    override fun onSave() {
        viewModel.dispatch(WaterCameraUserIntent.SaveClicked)
    }
}
