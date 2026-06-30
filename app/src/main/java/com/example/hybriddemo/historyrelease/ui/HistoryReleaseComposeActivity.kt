package com.example.hybriddemo.historyrelease.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import com.example.hybriddemo.historyrelease.model.HistoryReleaseDemoUiState
import com.example.hybriddemo.historyrelease.presentation.HistoryReleaseDemoViewModel

class HistoryReleaseComposeActivity : ComponentActivity() {

    private val vm by viewModels<HistoryReleaseDemoViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val state by vm.uiState.observeAsState()
                    val actionLog by vm.actionLog.observeAsState("等待操作")
                    state?.let {
                        HistoryReleaseComposePage(
                            state = it,
                            actionLog = actionLog,
                            onCopy = vm::onCopy,
                            onModify = vm::onModify,
                            onClose = vm::onClose,
                            onFlush = vm::onFlush,
                            onTop = vm::onTop,
                            onModifyTop = vm::onModifyTop,
                            onRedo = vm::onRedo,
                            onManageRecruit = vm::onManageRecruit,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryReleaseComposePage(
    state: HistoryReleaseDemoUiState,
    actionLog: String,
    onCopy: () -> Unit,
    onModify: () -> Unit,
    onClose: () -> Unit,
    onFlush: () -> Unit,
    onTop: () -> Unit,
    onModifyTop: () -> Unit,
    onRedo: () -> Unit,
    onManageRecruit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F8))
            .padding(16.dp)
    ) {
        Text("Compose 写法", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("完全由状态驱动 UI，去掉 XML 绑定表达式", color = Color(0xFF666666))
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatusTag(state.statusText)
                Spacer(modifier = Modifier.height(12.dp))
                Text(state.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.salary, color = Color(0xFFFF6B00), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(state.location)
                Text(state.publishTime, color = Color(0xFF888888))
                Text(state.workerCountText, color = Color(0xFF444444))
                if (state.showTopTime) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "置顶到期：${state.topEndTime}",
                        color = Color(0xFFFF6B00),
                        modifier = Modifier
                            .background(Color(0xFFFFF2E8), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onCopy) { Text("复制招工") }
                    if (state.showModify) Button(onClick = onModify) { Text("修改招工") }
                    if (state.showClose) Button(onClick = onClose) { Text("关闭招工") }
                    if (state.showFlush) Button(onClick = onFlush) { Text("刷新招工") }
                    if (state.showTop) Button(onClick = onTop) { Text("置顶招工") }
                    if (state.showModifyTop) Button(onClick = onModifyTop) { Text("修改置顶") }
                    if (state.showRedo) Button(onClick = onRedo) { Text("重新发布") }
                }
                if (state.showManageRecruit) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "管理招工",
                        color = Color(0xFF1366EC),
                        modifier = Modifier.clickable(onClick = onManageRecruit)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("操作日志", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(actionLog)
            }
        }
    }
}

@Composable
private fun StatusTag(text: String) {
    val valid = text.contains("招工中")
    Row(
        modifier = Modifier
            .background(
                if (valid) Color(0xFFE9FFF4) else Color(0xFFF1F3F5),
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (valid) Color(0xFF06B578) else Color(0xFF98A1B2),
            fontWeight = FontWeight.Medium
        )
    }
}
