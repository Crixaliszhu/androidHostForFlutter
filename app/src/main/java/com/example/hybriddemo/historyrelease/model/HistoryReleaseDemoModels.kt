package com.example.hybriddemo.historyrelease.model

import androidx.annotation.Keep

enum class HistoryReleaseDemoUiType {
    Full,
    TopAndNotFull,
    NotTopAndNotFull,
}

data class HistoryReleaseDemoItem(
    val jobId: String,
    val title: String,
    val salary: String,
    val location: String,
    val publishTime: String,
    val topEndTime: String?,
    val workerCountText: String,
    val uiType: HistoryReleaseDemoUiType,
)

data class HistoryReleaseSecondItem(
    val jobId: String,
    val title: String,
    val subTitle: String
)

data class HistoryReleaseDemoUiState(
    val title: String,
    val salary: String,
    val location: String,
    val publishTime: String,
    val topEndTime: String,
    val workerCountText: String,
    val statusText: String,
    val showTopTime: Boolean,
    val showClose: Boolean,
    val showFlush: Boolean,
    val showTop: Boolean,
    val showModifyTop: Boolean,
    val showModify: Boolean,
    val showRedo: Boolean,
    val showManageRecruit: Boolean,
)

@Keep
data class HistorySecondUiState(
    val title:String,
    val subTitle:String,
)