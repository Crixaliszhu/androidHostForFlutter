package com.example.hybriddemo.xbus.vm

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class XBusMainViewModel() : ViewModel() {
    private val _uiState = MutableLiveData<XBusMainUIState>()
    val uiState = _uiState

    init {
        pageInit()
    }

    private fun pageInit() {
        _uiState.value = XBusMainUIState(
            notice = ""
        )
    }

    fun updateNotice(msg:String){
        _uiState.value = XBusMainUIState(
            notice = msg
        )
    }
}