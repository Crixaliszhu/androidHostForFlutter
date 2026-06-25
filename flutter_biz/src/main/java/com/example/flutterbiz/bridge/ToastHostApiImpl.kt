package com.example.flutterbiz.bridge

import android.widget.Toast
import com.example.flutterengine.entity.FlutterApiContext
import com.example.flutterengine.pigeon.ToastHostApi
import com.example.flutterengine.registry.ApiRegistrar

/**
 * 简单 Toast HostApi。
 */
class ToastHostApiImpl(
    private val apiContext: FlutterApiContext,
    private val applicationContext: android.content.Context,
) : ToastHostApi, ApiRegistrar {

    override fun register() {
        ToastHostApi.setUp(apiContext.messenger, this)
    }

    override fun show(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
