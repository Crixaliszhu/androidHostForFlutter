package com.example.hybriddemo.type

abstract class BaseInt(val a: Int) {}

interface IInter {
    fun iDo()
}

@JvmInline
value class InlineValueInt(val num: Int) {

    init{
        test()
    }

    fun test(){
        //
    }

    fun function1(){

    }
}