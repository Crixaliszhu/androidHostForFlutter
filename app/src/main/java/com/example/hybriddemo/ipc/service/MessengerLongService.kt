package com.example.hybriddemo.ipc.service

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process.killProcess
import android.util.Log


/**
 * ipc通信-Messenger
 */
class MessengerLongService : Service() {

    companion object {
        const val MESSAGE_START = 1
        const val MESSAGE_SUCCESS = 2
        const val MESSAGE_FAIL = 3
        const val TAG = "MessengerLongService"
    }

    private val mHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != MESSAGE_START) return
            val replyTo = msg.replyTo
            Thread {
                try {
                    Thread.sleep(15000)
                    replyTo?.send(Message.obtain(null, MESSAGE_SUCCESS))
                } catch (e: Exception) {
                    replyTo?.send(Message.obtain(null, MESSAGE_FAIL))
                }
            }.start()
        }
    }


    override fun onBind(intent: Intent?): IBinder {
        return Messenger(mHandler).binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        killProcess(android.os.Process.myPid())
    }
}