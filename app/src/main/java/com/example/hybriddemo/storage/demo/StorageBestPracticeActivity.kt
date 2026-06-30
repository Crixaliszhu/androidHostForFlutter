package com.example.hybriddemo.storage.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class StorageBestPracticeActivity : ComponentActivity() {

    private val vm by viewModels<StorageBestPracticeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by vm.uiState.collectAsState()
                StorageBestPracticePage(
                    state = state,
                    onBack = ::finish,
                    onRunMmkv = vm::runMmkvDemo,
                    onToggleDataStore = vm::toggleDataStoreDemo,
                    onRunRoom = vm::runRoomDemo,
                    onClear = vm::clearAllDemoData,
                )
            }
        }
    }
}

@Composable
private fun StorageBestPracticePage(
    state: StorageDemoUiState,
    onBack: () -> Unit,
    onRunMmkv: () -> Unit,
    onToggleDataStore: () -> Unit,
    onRunRoom: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEFF7F2),
                        Color(0xFFFFF8E8),
                    )
                )
            ),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = "Android 本地存储封装 Demo",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E352C),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "对照鱼泡正式项目：MMKV 管轻量 KV，DataStore 管可观察偏好，Room 管结构化数据。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF52645C),
            )
            Spacer(modifier = Modifier.height(18.dp))

            StorageCard(
                title = "MMKV: namespace + LDS 收口",
                description = "模拟保存最后一次招聘草稿，页面不直接散写 key。",
                result = state.mmkvResult,
                buttonText = "写入并读取 MMKV",
                onClick = onRunMmkv,
            )
            StorageCard(
                title = "DataStore: 类型安全 key + Flow",
                description = "模拟自动保存草稿开关，点击后 Flow 自动刷新页面状态。",
                result = state.dataStoreResult,
                buttonText = "切换 DataStore 开关",
                onClick = onToggleDataStore,
            )
            StorageCard(
                title = "Room: DAO + Repository",
                description = "模拟插入历史发布记录，Repository 对外提供业务语义。",
                result = if (state.isLoading) "loading..." else state.roomResult,
                buttonText = "写入并查询 Room",
                onClick = onRunRoom,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onClear,
                ) {
                    Text("清空示例数据")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onBack,
                ) {
                    Text("返回")
                }
            }
        }
    }
}

@Composable
private fun StorageCard(
    title: String,
    description: String,
    result: String,
    buttonText: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF223C32),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6E7F77),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = result,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF4F0E6), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF3C3322),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}
