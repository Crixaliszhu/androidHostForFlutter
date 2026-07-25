package com.example.hybriddemo.service.bind_ervice

import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.coroutineScope
import com.example.hybriddemo.service.broadcast.NetWorkChangeReceiver
import com.example.hybriddemo.service.broadcast.NetWorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 生命周期服务-页面绑定
 */
class SyncDemoService : LifecycleService() {

    companion object {
        const val TAG = "SyncDemoService"
    }

    private val netWorkStateService = NetWorkChangeReceiver()

    override fun onCreate() {
        super.onCreate()
        @Suppress("DEPRECATION")
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        if (SDK_INT >= VERSION_CODES.TIRAMISU) {
            registerReceiver(netWorkStateService, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(netWorkStateService, filter)
        }
        netWorkStateService.setNetWorkChangeListener {
            Log.d(TAG, "网络状态变化: $it")
            if (it == NetWorkType.WIFI) {
                for (i in 0 until 10) {
                    serviceTaskMock(i.toString())
                }
            }
        }
        Log.d(TAG, "onCreate")
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Log.d(TAG, "onBind")
        return SysBinder()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(netWorkStateService)
    }

    /**
     * 耗时任务
     */
    fun serviceTaskMock(taskId: String) {
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            Log.d(TAG, "开始执行任务-$taskId")
            delay(5000)
            Log.d(TAG, "任务执行完毕-$taskId")
        }
    }

    inner class SysBinder : Binder() {

        /**
         * 暴露出去的主动上传任务
         */
        fun syncPhotoTask(taskId: String) {
            serviceTaskMock(taskId)
        }
    }
}