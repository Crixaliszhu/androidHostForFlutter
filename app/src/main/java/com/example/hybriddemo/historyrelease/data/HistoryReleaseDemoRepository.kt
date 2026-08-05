package com.example.hybriddemo.historyrelease.data

import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoItem
import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoUiType
import com.example.hybriddemo.historyrelease.model.HistoryReleaseSecondItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface HistoryReleaseDemoRepository {
    fun loadHistoryRelease(): HistoryReleaseDemoItem

    fun loadSecondHistory(index: Int): Flow<HistoryReleaseSecondItem>
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

    override fun loadSecondHistory(index: Int): Flow<HistoryReleaseSecondItem> {
        return flow {
            println("HistoryReleaseDemoViewModel-loadSecondHistory ${index}")
            emit(
                HistoryReleaseSecondItem(
                    jobId = "1111-${index}",
                    title = "second-title-${index}",
                    subTitle = "second-sub title-${index}",
                )
            )
        }.flowOn(Dispatchers.IO)
    }
}
