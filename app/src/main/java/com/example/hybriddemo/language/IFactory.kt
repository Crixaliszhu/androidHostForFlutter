package com.example.hybriddemo.language

import android.util.Log

interface IFactory {
    val name: String
    fun absFun()
    fun defFun() :String {
        Log.d("IFactory", "接口默认函数")
        return name
    }
}

class IFactoryImpl(override val name: String) : IFactory {
    override fun absFun() {
        Log.d("IFactoryImpl","接口实现")
    }

}