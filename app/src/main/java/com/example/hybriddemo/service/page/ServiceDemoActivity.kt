package com.example.hybriddemo.service.page

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.hybriddemo.databinding.ActivityServiceDemoBinding
import com.example.hybriddemo.service.bind_ervice.FloatDemoService
import com.example.hybriddemo.service.bind_ervice.SyncDemoService

@com.alibaba.android.arouter.facade.annotation.Route(path = com.example.hybriddemo.router.DemoRouterPaths.SERVICE)
class ServiceDemoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ServiceDemoActivity"
    }

    private lateinit var _binding: ActivityServiceDemoBinding

    private val syncConnect = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected: $name")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityServiceDemoBinding.inflate(layoutInflater)
        setContentView(_binding.root)
        Log.d(TAG, "onCreate - about to bindService")
        bindServices()
        bindAction()
        bindState()
    }

    private fun bindServices() {
        bindService(
            Intent(this, SyncDemoService::class.java),
            syncConnect,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun bindAction() {
        _binding.btnStart.setOnClickListener {
            //
        }
    }

    private fun bindState() {

    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(syncConnect)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        checkAndShowFloat()
    }

    private fun checkAndShowFloat() {
        if (!checkHasPermission()) {
            Log.d(TAG, "未开启悬浮窗权限")
            startOverlaySetting()
            return
        }
        showFloatAndFinish()
    }

    private fun startOverlaySetting() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        val uri = Uri.parse("package:" + this.packageName)
        intent.data = uri
        startActivity(intent)
    }

    private fun checkHasPermission(): Boolean {
        return Settings.canDrawOverlays(
            this
        )
    }

    private fun showFloatAndFinish() {
        try {
            val intent = Intent(this, FloatDemoService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            Log.e(TAG, "${e.message}")
        }
        finish()
    }
}
