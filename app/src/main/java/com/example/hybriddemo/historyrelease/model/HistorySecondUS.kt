package com.example.hybriddemo.historyrelease.model

import com.example.hybriddemo.util.noneNullStateIn
import com.example.hybriddemo.util.nullStateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistorySecondUS constructor(
    val scope: CoroutineScope,
    val data: Flow<HistorySecondUiState?>,
) {

    val title = data.map {
        it?.title
    }.noneNullStateIn(scope, "标题：")

    val subTitle = data.map {
        it?.subTitle
    }.nullStateIn(scope, "副标题：")
}