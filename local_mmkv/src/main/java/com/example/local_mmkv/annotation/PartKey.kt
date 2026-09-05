package com.example.local_mmkv.annotation

@MustBeDocumented
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class PartKey(val value: String = "")
