package com.example.hybriddemo.flowcompose

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@com.alibaba.android.arouter.facade.annotation.Route(path = com.example.hybriddemo.router.DemoRouterPaths.FLOW_COMPOSE)
class FlowComposeActivity : ComponentActivity() {

    private val viewModel by viewModels<FlowComposeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val context = LocalContext.current
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(viewModel) {
                        viewModel.events.collect { event ->
                            when (event) {
                                is FlowComposeEvent.Toast -> {
                                    Toast.makeText(
                                        context,
                                        event.message,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    }

                    FlowComposePage(
                        uiState = uiState,
                        onIncrease = viewModel::increase,
                        onDecrease = viewModel::decrease,
                        onRefresh = viewModel::refresh,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlowComposePage(
    uiState: FlowComposeUiState,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F7F9))
            .padding(16.dp),
    ) {
        Text(
            text = uiState.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "StateFlow 驱动页面状态，Channel 发送 Toast 等一次性事件",
            color = Color(0xFF666666),
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(uiState.countText, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDecrease) {
                        Text("减少")
                    }
                    Button(onClick = onIncrease) {
                        Text("增加")
                    }
                    Button(
                        onClick = onRefresh,
                        enabled = !uiState.loading,
                    ) {
                        Text(if (uiState.loading) "刷新中" else "刷新列表")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(uiState.latestMessage, color = Color(0xFF444444))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (uiState.loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = uiState.jobs,
                    key = { it.id },
                ) { job ->
                    JobCard(job)
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: FlowComposeJobUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(job.name, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(job.salary, color = Color(0xFFFF6B00))
            Text(job.city, color = Color(0xFF666666))
        }
    }
}
