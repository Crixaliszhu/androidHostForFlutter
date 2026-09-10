package com.example.hybriddemo.ipc.aidl

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.TransactionTooLargeException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.hybriddemo.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/** AIDL 交互实验页；主进程显示结果，远程服务进程完成计算和异步任务。 */
class AidlDemoActivity : ComponentActivity() {
    private lateinit var client: AidlDemoClient
    private val logs = mutableStateListOf<String>()
    private var state by mutableStateOf(AidlDemoClient.State.DISCONNECTED)
    private var remotePid by mutableStateOf<Int?>(null)
    private val operations = mutableListOf<Job>()
    private var currentTaskId: String? = null
    private var runningTask by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = AidlDemoClient(this)
        lifecycleScope.launch { client.events.collect { log(it) } }
        lifecycleScope.launch { client.state.collect { state = it } }
        lifecycleScope.launch { client.info.collect { remotePid = it?.getInt("pid") } }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("AIDL 双进程通信", style = MaterialTheme.typography.headlineSmall)
                        Text("客户端 PID=${Process.myPid()} / 服务端 PID=${remotePid ?: "—"}")
                        Text("连接状态：$state")
                        Text("主进程 → Binder → :aidl_demo。离开页面自动解绑；重新进入重新绑定。")
                        Button(onClick = { client.connect() }, modifier = Modifier.fillMaxWidth()) { Text("连接 / 重试") }
                        Button(onClick = { client.disconnect() }, modifier = Modifier.fillMaxWidth()) { Text("主动断开") }
                        val connected = state == AidlDemoClient.State.CONNECTED
                        Button(onClick = { basics() }, enabled = connected) { Text("基本类型、Parcelable、null、分页") }
                        Button(onClick = { directions() }, enabled = connected) { Text("验证 in / out / inout") }
                        Button(onClick = { concurrency() }, enabled = connected) { Text("20 次并发累加 + 回调") }
                        Button(onClick = { startTask() }, enabled = connected && !runningTask) { Text("提交 3 秒异步任务") }
                        Button(onClick = { demo("取消任务") {
                            val id = currentTaskId ?: error("没有本页发起的任务")
                            log("cancelTask=${client.cancelTask(id)}；false 表示已结束或不存在")
                        } }, enabled = connected && runningTask) { Text("取消当前任务") }
                        Button(onClick = { demo("等待超时") {
                            client.runTask(delayMillis = 3_000, timeoutMillis = 500)
                        } }, enabled = connected) { Text("3 秒任务只等 500ms") }
                        Button(onClick = { validation() }, enabled = connected) { Text("非法参数 / 业务异常") }
                        Button(onClick = { largePayload() }, enabled = connected) { Text("通过 FD 读取 256 KiB 数据") }
                        Button(onClick = { demo("进程死亡") {
                            client.call { it.simulateProcessDeath() }
                            log("远程进程即将退出，观察状态、PID 和回调重新注册；计数器会重置")
                        } }, enabled = connected && BuildConfig.DEBUG) { Text("终止远程进程并自动重连（Debug）") }
                        Button(onClick = { demo("查询快照") {
                            val snapshot = client.call { it.serviceInfo }
                            log("PID=${snapshot.getInt("pid")} counter=${snapshot.getLong("counter")} " +
                                "callbacks=${snapshot.getInt("callbacks")} pending=${snapshot.getInt("pendingTasks")} " +
                                "thread=${snapshot.getString("thread")}")
                        } }, enabled = connected) { Text("查询服务状态") }
                        Button(onClick = { logs.clear() }) { Text("清空日志") }
                        Text("实验日志（最多 100 条）", style = MaterialTheme.typography.titleMedium)
                        logs.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        client.connect()
    }

    override fun onStop() {
        // 页面不可见后不继续占有 bound Service；未完成任务由会话注销和取消协议共同清理。
        operations.toList().forEach { it.cancel() }
        operations.clear()
        client.disconnect()
        super.onStop()
    }

    override fun onDestroy() {
        client.close()
        super.onDestroy()
    }

    private fun basics() = demo("基本调用") {
        val text = client.call { api ->
            val record = IpcRecord(7, "客户端对象", 42)
            val copy = api.echo(record)
            "add(3,5)=${api.add(3, 5)}\necho=$copy，同一引用=${record === copy}" +
                "\nnullable=${api.echo(null)}\n分页=${api.getRecords(0, 3)}"
        }
        log(text)
    }

    private fun directions() = demo("参数方向") {
        log(client.call { api ->
            val input = IpcRecord(1, "原始 in", 10)
            val output = IpcRecord(2, "out 的旧值不会传到服务端", 20)
            val both = IpcRecord(3, "原始 inout", 30)
            api.demonstrateDirections(input, output, both)
            "in=$input\nout=$output\ninout=$both"
        })
    }

    private fun concurrency() = demo("并发调用") {
        val before = client.call { it.serviceInfo.getLong("counter") }
        val results = coroutineScope {
            (1..20).map { async { client.call { it.incrementCounter(1) } } }.awaitAll()
        }
        val after = client.call { it.serviceInfo.getLong("counter") }
        log("并发返回 ${results.sorted()}；前=$before 后=$after（独占本实验时应增加 20）")
    }

    private fun startTask() = demo("异步任务") {
        val id = UUID.randomUUID().toString()
        currentTaskId = id
        runningTask = true
        try {
            val result = client.runTask(3_000, 5_000, id)
            log("任务结束 status=${result.status} ${result.record}")
        } finally {
            if (currentTaskId == id) {
                currentTaskId = null
                runningTask = false
            }
        }
    }

    private fun validation() = demo("业务异常") {
        try {
            client.call { it.getRecords(-1, 1000) }
        } catch (e: IllegalArgumentException) {
            log("服务端参数异常：${e.message}")
        }
        try {
            client.runTask(-1, 1_000)
        } catch (e: AidlDemoClient.TaskRejectedException) {
            log("任务受理失败 code=${e.code} message=${e.message}")
        }
        // 发送前先限制长度；服务端也校验，不能只依赖接收后检查来避免大事务。
        val tooLong = "测".repeat(AidlDemoService.MAX_TEXT_LENGTH + 1)
        try {
            client.echo(IpcRecord(1, tooLong, 0))
        } catch (e: IllegalArgumentException) {
            log("客户端发送前拦截：${e.message}")
        }
        log("业务异常后 Binder 仍可调用：add(1,2)=${client.call { it.add(1, 2) }}")
    }

    private fun largePayload() = demo("文件描述符") {
        val bytes = client.call { api ->
            // AutoCloseInputStream 关闭时同时关闭描述符，防止 FD 泄漏。
            ParcelFileDescriptor.AutoCloseInputStream(api.openPayload()).use { input ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                }
                total
            }
        }
        log("通过 FD 流式读取 $bytes 字节；数据没有放进 Binder Parcel")
    }

    private fun demo(label: String, block: suspend () -> Unit) {
        val job = lifecycleScope.launch {
            try {
                block()
            } catch (_: TimeoutCancellationException) {
                log("$label：等待超时，已尝试取消远端任务")
            } catch (e: CancellationException) {
                throw e
            } catch (e: TransactionTooLargeException) {
                log("$label：事务过大，不能假定服务端未执行；改用分页或 FD")
            } catch (e: Exception) {
                log("$label：${e.javaClass.simpleName} ${e.message}")
            }
        }
        operations.add(job)
        job.invokeOnCompletion { operations.remove(job) }
    }

    private fun log(message: String) {
        logs.add(0, message)
        if (logs.size > 100) logs.removeAt(logs.lastIndex)
    }
}
