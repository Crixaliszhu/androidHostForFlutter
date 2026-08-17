package com.example.resume.api

import android.content.Context
import com.alibaba.android.arouter.facade.template.IProvider

interface IResumeRouterService : IProvider {
    fun open(context: Context)
}
