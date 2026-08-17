package com.example.resume.route

import android.content.Context
import com.alibaba.android.arouter.facade.annotation.Route
import com.example.resume.ResumeMainActivity
import com.example.resume.api.IResumeRouterService
import com.example.resume.api.ResumeRouterApiPaths

@Route(path = ResumeRouterApiPaths.RESUME_ROUTER_SERVICE)
class ResumeRouterImpl : IResumeRouterService {
    companion object {
        const val RESUME_MAIN = "/resume/page/main"
    }

    override fun open(context: Context) {
        // 在自己模块，一般直接调用静态启动函数启动页面即可
        ResumeMainActivity.startActivity(context)
    }

    override fun init(context: Context?) {
        //
    }
}