package com.example.hybriddemo.historyrelease.data

import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoItem
import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoUiType

interface HistoryReleaseDemoRepository {
    fun loadHistoryRelease(): HistoryReleaseDemoItem
}

class FakeHistoryReleaseDemoRepository : HistoryReleaseDemoRepository {
    override fun loadHistoryRelease(): HistoryReleaseDemoItem {
        return HistoryReleaseDemoItem(
            jobId = "123456",
            title = "木工/支模工 10人",
            salary = "330-380元/天",
            location = "成都 · 双流区 · 天府国际生物城",
            publishTime = "发布时间：2026-06-30 09:30",
            topEndTime = "2026-07-03 23:59",
            workerCountText = "已招 7/10 人",
            uiType = HistoryReleaseDemoUiType.TopAndNotFull,
        )
    }
}
