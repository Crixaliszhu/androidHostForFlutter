package com.example.hybriddemo.ipc.message

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.example.hybriddemo.ipc.ServiceResult
import com.example.hybriddemo.ipc.service.MessengerLongService
import com.example.hybriddemo.ipc.service.MessengerLongService.Companion
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object MessengerCaller {
    const val TAG = "MessengerCaller"
    private var appContext: Context? = null

    private var mMessage: Messenger? = null

    private var isBind = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val disRunnable = Runnable { unbind() }

    private var bindCount: CountDownLatch? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mMessage = Messenger(service)
            isBind = true
            bindCount?.countDown()
            bindCount = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mMessage = null
            isBind = false
        }

    }

    fun init(context: Context) {
        appContext = context.applicationContext
        Log.d(TAG, "初始化Context")
    }

    @Synchronized
    fun runService(): ServiceResult {
        if (!isBind) {
            bindCount = CountDownLatch(1)
            mainHandler.post {
                Log.d(TAG, "开始绑定服务")
                appContext?.bindService(
                    Intent(appContext, MessengerLongService::class.java),
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )
            }
            val bindOk = bindCount?.await(15,TimeUnit.SECONDS)
        }
        var result: ServiceResult = ServiceResult.Fail("fail")
        val countDown = CountDownLatch(1)
        val replyToHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                if (msg.what == MessengerLongService.MESSAGE_SUCCESS) {
                    result = ServiceResult.Suc("Success")
                } else if (msg.what == MessengerLongService.MESSAGE_FAIL) {
                    result = ServiceResult.Fail("Fail")
                }
                countDown.countDown()
            }
        }

        val msg = Message.obtain(null, MessengerLongService.MESSAGE_START).apply {
            replyTo = Messenger(replyToHandler)
        }

        try {
            mMessage?.send(msg) ?: return ServiceResult.SendFail("SendFail")
        } catch (e: Exception) {
            return ServiceResult.SendFail("sendFail")
        }

        mainHandler.removeCallbacks(disRunnable)
        mainHandler.postDelayed(disRunnable, 20_000L)
        val awaited = countDown.await(20, TimeUnit.SECONDS)
        if (!awaited) return ServiceResult.OutTime("OutTime")

        return result
    }

    private fun unbind() {
        if (isBind) {
            appContext?.unbindService(serviceConnection)
            isBind = false
            mMessage = null
            Log.d(TAG, "解除绑定")
        }
    }

}