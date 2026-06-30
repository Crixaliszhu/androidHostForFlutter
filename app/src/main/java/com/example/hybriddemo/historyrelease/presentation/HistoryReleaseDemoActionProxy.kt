package com.example.hybriddemo.historyrelease.presentation

class HistoryReleaseDemoActionProxy(
    private val vm: HistoryReleaseDemoViewModel,
) {
    fun onCopy() = vm.onCopy()
    fun onModify() = vm.onModify()
    fun onClose() = vm.onClose()
    fun onFlush() = vm.onFlush()
    fun onTop() = vm.onTop()
    fun onModifyTop() = vm.onModifyTop()
    fun onRedo() = vm.onRedo()
    fun onManageRecruit() = vm.onManageRecruit()
}
