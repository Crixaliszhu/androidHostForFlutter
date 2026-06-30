package com.example.hybriddemo.historyrelease.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.hybriddemo.databinding.ActivityHistoryReleaseDataBindingBinding
import com.example.hybriddemo.historyrelease.presentation.HistoryReleaseDemoActionProxy
import com.example.hybriddemo.historyrelease.presentation.HistoryReleaseDemoViewModel

class HistoryReleaseDataBindingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryReleaseDataBindingBinding
    private val vm by viewModels<HistoryReleaseDemoViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryReleaseDataBindingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.lifecycleOwner = this
        binding.vm = vm
        binding.action = HistoryReleaseDemoActionProxy(vm)
        binding.topBar.title.text = "DataBinding 写法"
        binding.topBar.subtitle.text = "渲染与点击表达式部分进入 XML"
    }
}
