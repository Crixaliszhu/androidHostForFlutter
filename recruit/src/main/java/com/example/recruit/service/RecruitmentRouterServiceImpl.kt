package com.example.recruit.service

import android.content.Context
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.example.recruit.api.IRecruitRouterService
import com.example.recruit.api.RecruitRouterApiPaths

@Route(path = RecruitRouterApiPaths.RECRUIT_ROUTER_SERVICE)
class RecruitmentRouterServiceImpl : IRecruitRouterService {
    override fun open(context: Context) {
        ARouter
            .getInstance()
            .build(RecruitRouterApiPaths.RECRUIT_MAIN)
            .navigation(context)
    }

    override fun init(context: Context?) {
        //
    }
}
