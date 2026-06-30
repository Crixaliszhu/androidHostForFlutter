package com.example.hybriddemo.storage.demo

data class StorageDemoUiState(
    val mmkvResult: String = "not executed",
    val dataStoreResult: String = "not executed",
    val roomResult: String = "not executed",
    val autoSaveDraft: Boolean = true,
    val isLoading: Boolean = false,
)
