package com.example.hybriddemo.historyrelease.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.hybriddemo.databinding.ActivityHistoryReleaseViewBindingBinding
import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoUiState
import com.example.hybriddemo.historyrelease.presentation.HistoryReleaseDemoViewModel

class HistoryReleaseViewBindingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryReleaseViewBindingBinding
    private val vm by viewModels<HistoryReleaseDemoViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryReleaseViewBindingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topBar.title.text = "ViewBinding 写法"
        binding.topBar.subtitle.text = "所有渲染逻辑都在 Kotlin 中完成"
        bindActions()
        observeState()
    }

    private fun bindActions() {
        binding.btnCopy.setOnClickListener { vm.onCopy() }
        binding.btnModify.setOnClickListener { vm.onModify() }
        binding.btnClose.setOnClickListener { vm.onClose() }
        binding.btnFlush.setOnClickListener { vm.onFlush() }
        binding.btnTop.setOnClickListener { vm.onTop() }
        binding.btnModifyTop.setOnClickListener { vm.onModifyTop() }
        binding.btnRedo.setOnClickListener { vm.onRedo() }
        binding.tvManageRecruit.setOnClickListener { vm.onManageRecruit() }
    }

    private fun observeState() {
        vm.uiState.observe(this, ::render)
        vm.actionLog.observe(this) { binding.tvActionLog.text = it }
    }

    private fun render(state: HistoryReleaseDemoUiState) = with(binding) {
        tvJobTitle.text = state.title
        tvSalary.text = state.salary
        tvLocation.text = state.location
        tvPublishTime.text = state.publishTime
        tvWorkerCount.text = state.workerCountText
        tvTopEndTime.text = "置顶到期：${state.topEndTime}"
        HistoryReleaseBindingAdapters.bindHistoryReleaseStatusText(tvJobStatus, state.statusText)
        flTopTime.visibility = if (state.showTopTime) android.view.View.VISIBLE else android.view.View.GONE
        btnClose.visibility = if (state.showClose) android.view.View.VISIBLE else android.view.View.GONE
        btnFlush.visibility = if (state.showFlush) android.view.View.VISIBLE else android.view.View.GONE
        btnTop.visibility = if (state.showTop) android.view.View.VISIBLE else android.view.View.GONE
        btnModifyTop.visibility = if (state.showModifyTop) android.view.View.VISIBLE else android.view.View.GONE
        btnModify.visibility = if (state.showModify) android.view.View.VISIBLE else android.view.View.GONE
        btnRedo.visibility = if (state.showRedo) android.view.View.VISIBLE else android.view.View.GONE
        tvManageRecruit.visibility = if (state.showManageRecruit) android.view.View.VISIBLE else android.view.View.GONE
    }
}
