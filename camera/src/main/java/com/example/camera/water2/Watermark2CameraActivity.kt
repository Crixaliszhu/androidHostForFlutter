package com.example.camera.water2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.example.camera.CameraRouterPaths
import com.example.camera.databinding.ActivityWatermark2CameraBinding
import com.example.camera.water.intent.WaterCameraEffect
import com.example.camera.water2.vm.Watermark2CameraViewModel
import com.example.permission.PermissionController
import com.example.permission.PermissionUtils
import com.example.widget.titlebar.toolbar.ToolBarManager2
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@Route(path = CameraRouterPaths.WATERMARK_CAMERA2)
@AndroidEntryPoint
class Watermark2CameraActivity : FragmentActivity() {
    private lateinit var binding: ActivityWatermark2CameraBinding
    private val viewModel by viewModels<Watermark2CameraViewModel>()
    private lateinit var permissionController: PermissionController

    companion object {
        private const val TAG = "Watermark2CameraActivity"

        fun start(context: Context) {
            context.startActivity(Intent(context, Watermark2CameraActivity::class.java))
        }
    }

    private val cameraController by lazy {
        Watermark2CameraController(
            context = this,
            onCameraReady = {
                viewModel.dispatch(Water2CameraUserIntent.CameraReady)
            },
            onPhotoCaptured = { jpgBytes, jpegOrientation ->
                viewModel.dispatch(
                    Water2CameraUserIntent.PhotoCaptured(
                        jpegBytes = jpgBytes,
                        jpegOrientation = jpegOrientation
                    )
                )
            },
            onError = { message ->
                viewModel.dispatch(Water2CameraUserIntent.CameraError(message))
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionController = PermissionController.newInstance(this)
        binding = ActivityWatermark2CameraBinding.inflate(layoutInflater).apply {
            vm = viewModel
            clickProxy = viewModel
            lifecycleOwner = this@Watermark2CameraActivity
        }

        setContentView(binding.root)
        ToolBarManager2.attach(
            this,
            config = ToolBarManager2.Config(
                title = "水印相机复制页",
                statusBarColor = Color.BLACK,
                toolbarColor = Color.BLACK,
                titleColor = Color.WHITE,
                backIconColor = Color.WHITE,
                darkStatusBarIcons = false
            )
        )
        initObserver()
    }

    private fun initObserver() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                viewModel.onBack()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.effect.collect(::handleEffect)
            }
        }
    }

    private fun handleEffect(effect: WaterCameraEffect) {
        when (effect) {
            WaterCameraEffect.RequestCameraPermission -> {
                permissionController.request(Manifest.permission.CAMERA) { granted, type ->
                    viewModel.dispatch(Water2CameraUserIntent.CameraPermissionResult(granted))
                }
            }

            WaterCameraEffect.RequestLocationPermission -> {
                permissionController.request(
                    listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                ) { granted, type ->
                    viewModel.dispatch(Water2CameraUserIntent.LocationPermissionResult(granted))
                }
            }

            WaterCameraEffect.RequestStoragePermission -> {
                permissionController.request(Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted, type ->
                    viewModel.dispatch(Water2CameraUserIntent.StoragePermissionResult(granted))
                }
            }

            WaterCameraEffect.StartCameraPreview -> startCameraPreviewIfResumed()
            WaterCameraEffect.CapturePhoto -> {
                if (!cameraController.takePhoto()) {
                    viewModel.dispatch(Water2CameraUserIntent.CameraCaptureUnavailable)
                }
            }

            WaterCameraEffect.ResumeCameraPreview -> {
                binding.photoPreview.setImageDrawable(null)
                cameraController.resumePreview()
                startCameraPreviewIfResumed()
            }

            WaterCameraEffect.OpenLocationSettings -> {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
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
        viewModel.dispatch(Water2CameraUserIntent.PageStarted)
    }

    override fun onResume() {
        super.onResume()
        viewModel.dispatch(
            Water2CameraUserIntent.PageResumed(
                hasCameraPermission = PermissionUtils.hasPermission(
                    applicationContext,
                    Manifest.permission.CAMERA,
                )
            )
        )
    }

    override fun onPause() {
        cameraController.stop()
        super.onPause()
    }

    override fun onStop() {
        viewModel.dispatch(Water2CameraUserIntent.PageStopped)
        super.onStop()
    }

}