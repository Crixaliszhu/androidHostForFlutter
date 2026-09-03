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
import com.example.hybriddemo.router.DemoRouterPaths
import com.example.widget.titlebar.YpPageScaffold

@Route(path = DemoRouterPaths.PROFILE_PATH)
class ProfileDemoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YpPageScaffold(
                title = "Android 优化示例",
                onBackClick = { finish() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.SERVICE)
                                .navigation(this@ProfileDemoActivity)
                        }
                    ) {
                        Text("Service展示")
                    }
                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.WORK_MANAGER)
                                .navigation(this@ProfileDemoActivity)
                        }
                    ) {
                        Text("WorkManager 展示")
                    }
                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.JANK_ON_ENTER)
                                .navigation(this@ProfileDemoActivity)
                        }
                    ) {
                        Text("进入页面卡顿示例")
                    }
                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.MEMORY_LEAK)
                                .navigation(this@ProfileDemoActivity)
                        }
                    ) {
                        Text("Memory profile 内存占用示例")
                    }
                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.SENTRY)
                                .navigation(this@ProfileDemoActivity)
                        }
                    ) {
                        Text("Sentry分析 示例")
                    }
                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.ANR_CASES)
                                .navigation(this@ProfileDemoActivity)
                        }
                    ) {
                        Text("八大ANR场景演示")
                    }
                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.SETTINGS)
                                .navigation(this@ProfileDemoActivity)
                        }
                    ) {
                        Text("设置页面")
                    }
                }
            }
        }
    }
}