package com.example.hybriddemo.service.bind_ervice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.example.hybriddemo.R

/**
 * LifecycleService-前台服务
 */
class FloatDemoService : LifecycleService() {

    companion object {
        private const val TAG = "FloatDemoService"
        private const val CHANNEL_ID = "float_demo_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var contentView: View? = null
    private var btnStop: Button? = null

    override fun onCreate() {
        super.onCreate()
        initView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 必须第一时间调用 startForeground，否则 5 秒内会 ANR
        startForegroundWithNotification()
        showFloatView()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForegroundWithNotification() {
        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮窗前台服务通知"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_logo)
            .setContentTitle("悬浮窗运行中")
            .setContentText("点击关闭悬浮窗按钮可停止")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
    }

    private fun initView() {
        windowManager = applicationContext.getSystemService(WINDOW_SERVICE) as WindowManager

        val windowParams = WindowManager.LayoutParams()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            windowParams.type = WindowManager.LayoutParams.TYPE_PHONE
        }
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams.format = PixelFormat.TRANSPARENT

        layoutParams = windowParams
        Log.d(TAG, "initView")
    }

    private fun showFloatView() {
        if (contentView == null) {
            contentView =
                LayoutInflater.from(applicationContext).inflate(R.layout.float_demo_layout, null)
            btnStop = contentView?.findViewById(R.id.btnStop)
            btnStop?.setOnClickListener {
                Log.d(TAG, "关闭悬浮窗")
                stopSelf()
            }
            windowManager?.addView(contentView, layoutParams)
            Log.d(TAG, "showFloatView")
        }
    }

    private fun removeFloatView() {
        Log.d(TAG, "removeFloatView")
        if (contentView == null) return
        if (contentView?.parent == null) return
        windowManager?.removeView(contentView)
        contentView = null
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        removeFloatView()
    }
}