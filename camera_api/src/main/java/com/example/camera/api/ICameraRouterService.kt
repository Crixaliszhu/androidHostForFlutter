package com.example.camera.api

import android.content.Context
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.facade.template.IProvider

interface ICameraRouterService : IProvider {
    fun open(context: Context)

    fun openWatermarkCamera(context: Context)

    fun openWatermark2Camera(context: Context)
}
