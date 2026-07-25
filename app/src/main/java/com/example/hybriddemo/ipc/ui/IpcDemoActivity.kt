package com.example.hybriddemo.ipc.ui

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.coroutineScope
import com.example.hybriddemo.databinding.ActivityIcpDemoLayoutBinding
import com.example.hybriddemo.ipc.message.MessengerCaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 进程通信demo
 */
class IpcDemoActivity : AppCompatActivity() {
    companion object{
        const val TAG = "IpcDemoActivity"
    }

    private lateinit var _binding: ActivityIcpDemoLayoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MessengerCaller.init(this)
        _binding = ActivityIcpDemoLayoutBinding.inflate(layoutInflater)
        setContentView(_binding.root)
        initAction()
        initState()
    }

    private fun initAction(){
        _binding.btnMessager.setOnClickListener {
            // 启动服务
            startMessenger()
        }
    }

    private fun startMessenger(){
        Log.d(TAG, "启动服务")
        lifecycle.coroutineScope.launch(Dispatchers.IO){
            val result = MessengerCaller.runService()
            Log.d(TAG, "执行结果：${result.getResultMsg()}")
        }
    }

    private fun initState(){

    }
}