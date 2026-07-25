package com.example.hybriddemo.ipc

sealed interface ServiceResult {
    data class Suc(val msg: String) : ServiceResult {
        override fun getResultMsg(): String {
            return msg
        }
    }

    data class OutTime(val msg: String) : ServiceResult {
        override fun getResultMsg(): String {
            return msg
        }
    }

    data class Fail(val msg: String) : ServiceResult {
        override fun getResultMsg(): String {
            return msg
        }
    }

    data class SendFail(val msg: String) : ServiceResult {
        override fun getResultMsg(): String {
            return msg
        }
    }

    fun getResultMsg(): String
}