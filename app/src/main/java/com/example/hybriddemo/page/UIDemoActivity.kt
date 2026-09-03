package com.example.hybriddemo.page

import android.os.Bundle
import android.os.PersistableBundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
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
import com.example.recruit.api.IRecruitRouterService
import com.example.recruit.api.RecruitRouterApiPaths
import com.example.resume.api.IResumeRouterService
import com.example.resume.api.ResumeRouterApiPaths
import com.example.router.RouterApi
import com.example.widget.titlebar.YpPageScaffold

@Route(path = DemoRouterPaths.UI_PATH)
class UIDemoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YpPageScaffold(
                title = "UI层代码展示",
                onBackClick = { finish() }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.HISTORY_DATA_BINDING)
                                .navigation(this@UIDemoActivity)
                        }) {
                        Text("Databinding 版本")
                    }

                    Button(modifier = Modifier.fillMaxWidth(),

                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.HISTORY_VIEW_BINDING)
                                .navigation(this@UIDemoActivity)
                        }
                    ) {
                        Text("ViewBinding 版本")
                    }

                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.HISTORY_COMPOSE)
                                .navigation(this@UIDemoActivity)
                        }) {
                        Text("Compose版本")
                    }

                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.FLOW_COMPOSE)
                                .navigation(this@UIDemoActivity)
                        }) {
                        Text("VM + Flow + Compose")
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.JOB_SEARCH_COLLAPSE)
                                .navigation(this@UIDemoActivity)
                        }) {
                        Text("搜索框滑动动画")
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            ARouter.getInstance().build(DemoRouterPaths.PATH_ANIMATION)
                                .navigation(this@UIDemoActivity)
                        }) {
                        Text("PathMeasure动画")
                    }

                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            RouterApi.getByPath(
                                RecruitRouterApiPaths.RECRUIT_ROUTER_SERVICE,
                                IRecruitRouterService::class.java,
                            )?.open(this@UIDemoActivity)
                        }) {
                        Text("找工作首页")
                    }

                    Button(modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            RouterApi.getByPath(
                                ResumeRouterApiPaths.RESUME_ROUTER_SERVICE,
                                IResumeRouterService::class.java
                            )?.open(this@UIDemoActivity)
                        }) {
                        Text("找活首页")
                    }
                }
            }
        }
    }
}