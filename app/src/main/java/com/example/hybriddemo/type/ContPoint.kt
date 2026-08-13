package com.example.hybriddemo.type

import android.util.Log

data class ContPoint(val a: Int, val b: Int) {
}

class NormalPoint(val a: Int, val b: Int) {

    init {
        println("NormalPoint---------init-----")
    }

    constructor(a: Int, b: Int, c: Int) : this(a, b) {
        println("NormalPoint 次 constructor =========")
    }

    operator fun component1(): Int = a

    operator fun component2(): Int = b

    operator fun plus(np: NormalPoint): NormalPoint {
        return NormalPoint(np.a + a, np.b + b)
    }

    fun add(vararg num: Int): Int {
        return num.size
    }

    tailrec fun diGui(num: Int, ecc: Int): Int {
        return if (num <= 1) {
            ecc
        } else {
            diGui(num - 1, ecc * num)
        }
    }
}