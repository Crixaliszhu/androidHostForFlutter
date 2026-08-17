package com.example.recruit.api

import android.content.Context
import com.alibaba.android.arouter.facade.template.IProvider

interface IRecruitRouterService : IProvider {
    fun open(context: Context)
}
