package com.example.hybriddemo.anrdemo

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock

class AnrDemoService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service 生命周期回调也在主线程，长时间阻塞会让系统判定服务执行超时。
        SystemClock.sleep(BLOCK_DURATION_MS)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val BLOCK_DURATION_MS = 25_000L
    }
}
