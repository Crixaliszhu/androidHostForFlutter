package com.example.hybriddemo.xbus

import android.os.Bundle
import com.alibaba.android.arouter.facade.annotation.Route
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appapi.IDemoRouterService
import com.example.hybriddemo.router.DemoRouterPaths
import com.example.hybriddemo.xbus.vm.XBusMainUIState
import com.example.hybriddemo.xbus.vm.XBusMainViewModel
import com.example.router.RouterApi

@Route(path = DemoRouterPaths.XBUS_MAIN)
class XBusMainActivity : ComponentActivity() {
    private val vm by viewModels<XBusMainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface {
                    val state by vm.uiState.observeAsState()
                    state?.let {
                        XBusMainPage(state = it) { this.routeToSend() }
                    }
                }
            }
        }
        initObserver()
    }

    private fun routeToSend() {
        RouterApi.getByClass(IDemoRouterService::class.java)?.openXBusSend(this)
    }

    private fun initObserver(){
        XBus.get(this).of(MainEvent::class.java).listen {
            vm.updateNotice(it.msg)
        }
    }
}

@Composable
private fun XBusMainPage(state: XBusMainUIState, toSendPage: () -> Unit) {

    Column(modifier = Modifier.padding(16.dp)) {
        Text("打印XBus接收事件内容：", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(state.notice ?: "", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = toSendPage) { Text("前往Send页") }
    }
}
