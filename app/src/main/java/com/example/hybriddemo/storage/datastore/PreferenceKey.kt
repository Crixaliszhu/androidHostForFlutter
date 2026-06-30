package com.example.hybriddemo.storage.datastore

data class PreferenceKey<T : Any>(
    val name: String,
    val defaultValue: T,
)
