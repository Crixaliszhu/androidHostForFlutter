package com.example.hybriddemo.historyrelease.domain

import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoItem
import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoUiState
import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoUiType

object HistoryReleaseDemoStateFactory {

    fun create(item: HistoryReleaseDemoItem): HistoryReleaseDemoUiState {
        return when (item.uiType) {
            HistoryReleaseDemoUiType.Full -> HistoryReleaseDemoUiState(
                title = item.title,
                salary = item.salary,
                location = item.location,
                publishTime = item.publishTime,
                topEndTime = item.topEndTime.orEmpty(),
                workerCountText = item.workerCountText,
                statusText = "已招满",
                showTopTime = false,
                showClose = false,
                showFlush = false,
                showTop = false,
                showModifyTop = false,
                showModify = true,
                showRedo = true,
                showManageRecruit = true,
            )

            HistoryReleaseDemoUiType.TopAndNotFull -> HistoryReleaseDemoUiState(
                title = item.title,
                salary = item.salary,
                location = item.location,
                publishTime = item.publishTime,
                topEndTime = item.topEndTime.orEmpty(),
                workerCountText = item.workerCountText,
                statusText = "招工中（已置顶）",
                showTopTime = true,
                showClose = true,
                showFlush = false,
                showTop = false,
                showModifyTop = true,
                showModify = true,
                showRedo = false,
                showManageRecruit = true,
            )

            HistoryReleaseDemoUiType.NotTopAndNotFull -> HistoryReleaseDemoUiState(
                title = item.title,
                salary = item.salary,
                location = item.location,
                publishTime = item.publishTime,
                topEndTime = item.topEndTime.orEmpty(),
                workerCountText = item.workerCountText,
                statusText = "招工中",
                showTopTime = false,
                showClose = true,
                showFlush = true,
                showTop = true,
                showModifyTop = false,
                showModify = true,
                showRedo = false,
                showManageRecruit = true,
            )
        }
    }
}
