package com.example.camera.api

import android.content.Context
import com.alibaba.android.arouter.facade.annotation.Route
import com.example.camera.water.WatermarkCameraActivity
import com.example.camera.water2.Watermark2CameraActivity

@Route(path = CameraRouterApiPaths.CAMERA_ROUTER_SERVICE)
class CameraRouteServiceImpl : ICameraRouterService {
    override fun open(context: Context) {
        //
    }

    override fun openWatermarkCamera(context: Context) {
        //
        WatermarkCameraActivity.start(context)
    }

    override fun openWatermark2Camera(context: Context) {
        Watermark2CameraActivity.start(context)
    }

    override fun init(context: Context?) {
        //
    }
}