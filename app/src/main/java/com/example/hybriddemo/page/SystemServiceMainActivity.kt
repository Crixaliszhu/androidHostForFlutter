package com.example.hybriddemo.page

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.example.camera.api.CameraRouterApiPaths
import com.example.camera.api.ICameraRouterService
import com.example.hybriddemo.router.DemoRouterPaths
import com.example.router.RouterApi
import com.example.widget.titlebar.YpPageScaffold

/** 系统能力示例入口，集中导航到相机、IPC 和系统服务演示。 */
@Route(path = DemoRouterPaths.SYSTEM_PAGE)
class SystemServiceMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YpPageScaffold(
                title = "系统服务功能详解",
                onBackClick = { finish() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.PHOTO_PICKER)
                                .navigation(this@SystemServiceMainActivity)
                        }
                    ) {
                        Text("展示 Photo Picker选图")
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.CAMERA)
                                .navigation(this@SystemServiceMainActivity)
                        }
                    ) {
                        Text("Camera2相机开发要点")
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.IPC)
                                .navigation(this@SystemServiceMainActivity)
                        }
                    ) {
                        Text("进程通信：Messenger / AIDL")
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            RouterApi.getByClass(ICameraRouterService::class.java)
                                ?.openWatermarkCamera(this@SystemServiceMainActivity)
                        }
                    ) {
                        Text("水印相机-Camera")
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            RouterApi.getByClass(ICameraRouterService::class.java)
                                ?.openWatermark2Camera(this@SystemServiceMainActivity)
                        }
                    ) {
                        Text("水印相机2-Camera2")
                    }
                }
            }
        }
    }
}
