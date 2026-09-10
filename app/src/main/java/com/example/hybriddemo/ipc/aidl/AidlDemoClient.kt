package com.example.hybriddemo.ipc.aidl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** 页面持有的连接管理器；状态只在主线程变更，所有同步 Binder 业务调用都切到 IO。 */
class AidlDemoClient(context: Context) {
    /** 区分正在绑定和真正可调用，防止 bindService 返回后立即使用空代理。 */
    enum class State { DISCONNECTED, BINDING, CONNECTED, RETRY_WAIT, CLOSED }

    /** 业务完成状态与传输失败分离；取消任务也是一个明确的业务结果。 */
    class TaskResult(val status: Int, val record: IpcRecord)

    /** 将远程结构化回执转换为本地业务异常；该自定义异常本身不会穿过 Binder。 */
    class TaskRejectedException(val code: Int, message: String) : RuntimeException(message)

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(State.DISCONNECTED)
    val state = mutableState.asStateFlow()
    private val mutableInfo = MutableStateFlow<Bundle?>(null)
    val info = mutableInfo.asStateFlow()
    private val mutableEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = mutableEvents.asSharedFlow()
    private var current: Attempt? = null
    private var wanted = false
    private var closed = false
    private var retries = 0
    private var retryJob: Job? = null

    /** 每次绑定创建独立连接、回调和死亡监听，旧连接的迟到事件不能覆盖新连接。 */
    private inner class Attempt : ServiceConnection {
        var bindRequested = false
        var remote: IAidlDemoService? = null
        var rawBinder: IBinder? = null
        var timeout: Job? = null
        val pending = mutableMapOf<String, CompletableDeferred<TaskResult>>()
        val deathRecipient = IBinder.DeathRecipient {
            scope.launch { lost(this@Attempt, "DeathRecipient：服务进程死亡") }
        }
        val callback = object : IAidlDemoCallback.Stub() {
            override fun onTaskFinished(requestId: String, status: Int, result: IpcRecord) {
                val thread = Thread.currentThread().name
                scope.launch {
                    if (current !== this@Attempt) return@launch
                    val waiter = pending.remove(requestId)
                    waiter?.complete(TaskResult(status, result))
                    emit("回调[$thread] $requestId status=$status ${result.text}" +
                        if (waiter == null) "（已超时/无等待者）" else "")
                }
            }

            override fun onCounterChanged(value: Long) {
                val thread = Thread.currentThread().name
                scope.launch {
                    if (current === this@Attempt) emit("计数通知[$thread] value=$value")
                }
            }
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (current !== this) return
            rawBinder = service
            remote = IAidlDemoService.Stub.asInterface(service)
            scope.launch {
                try {
                    // 连接握手完成前不发布 CONNECTED。非取消区保证迟到注册随后一定能被注销。
                    val snapshot = withContext(NonCancellable + Dispatchers.IO) {
                        service.linkToDeath(deathRecipient, 0)
                        val api = checkNotNull(remote)
                        val result = api.serviceInfo
                        check(result.getInt("protocolVersion") == IAidlDemoService.PROTOCOL_VERSION) {
                            "AIDL 协议版本不匹配"
                        }
                        api.registerCallback(callback)
                        result
                    }
                    if (current === this@Attempt && wanted) {
                        timeout?.cancel()
                        retries = 0
                        mutableInfo.value = snapshot
                        mutableState.value = State.CONNECTED
                        emit("连接成功，远程 PID=${snapshot.getInt("pid")}，回调已注册")
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (current !== this@Attempt) return@launch
                    if (e is SecurityException || e is IllegalStateException) {
                        emit("连接被拒绝：${e.message}")
                        disconnect()
                    } else {
                        lost(this@Attempt, "握手失败：${e.message}")
                    }
                } finally {
                    if (current !== this@Attempt) cleanupRemote(this@Attempt)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            lost(this, "onServiceDisconnected")
        }

        override fun onBindingDied(name: ComponentName) {
            lost(this, "onBindingDied：需要重新绑定")
        }

        override fun onNullBinding(name: ComponentName) {
            if (current !== this) return
            emit("onNullBinding：服务未提供 Binder，停止重试")
            disconnect()
        }
    }

    fun connect() {
        assertMainThread()
        check(!closed) { "连接管理器已关闭" }
        wanted = true
        if (current != null || retryJob?.isActive == true) return
        retries = 0
        bind()
    }

    private fun bind() {
        if (!wanted || closed) return
        val attempt = Attempt()
        current = attempt
        mutableState.value = State.BINDING
        try {
            attempt.bindRequested = true
            val accepted = appContext.bindService(
                Intent(appContext, AidlDemoService::class.java), attempt, Context.BIND_AUTO_CREATE,
            )
            if (!accepted) {
                lost(attempt, "bindService 返回 false")
                return
            }
            attempt.timeout = scope.launch {
                delay(5_000)
                lost(attempt, "绑定/握手等待超过 5 秒")
            }
        } catch (e: SecurityException) {
            emit("绑定权限错误：${e.message}")
            disconnect()
        } catch (e: IllegalArgumentException) {
            emit("绑定配置错误：${e.message}")
            disconnect()
        }
    }

    private fun lost(attempt: Attempt, reason: String) {
        if (current !== attempt) return
        emit(reason)
        release(attempt)
        if (!wanted || closed) return
        if (retries >= 5) {
            wanted = false
            mutableState.value = State.DISCONNECTED
            emit("连续重试失败，请手动连接")
            return
        }
        val waitMillis = minOf(500L shl retries++, 8_000L)
        mutableState.value = State.RETRY_WAIT
        retryJob = scope.launch {
            delay(waitMillis)
            retryJob = null
            bind()
        }
    }

    fun disconnect() {
        assertMainThread()
        wanted = false
        retryJob?.cancel()
        retryJob = null
        current?.let { release(it) }
        if (!closed) mutableState.value = State.DISCONNECTED
    }

    private fun release(attempt: Attempt) {
        if (current === attempt) current = null
        attempt.timeout?.cancel()
        attempt.pending.values.forEach { it.completeExceptionally(DeadObjectException()) }
        attempt.pending.clear()
        mutableInfo.value = null
        if (attempt.bindRequested) {
            attempt.bindRequested = false
            try {
                // 即使返回 false、空绑定或绑定死亡，也成对释放曾申请的连接。
                appContext.unbindService(attempt)
            } catch (_: IllegalArgumentException) {
                emit("系统已移除绑定")
            }
        }
        scope.launch { cleanupRemote(attempt) }
    }

    private suspend fun cleanupRemote(attempt: Attempt) = withContext(NonCancellable + Dispatchers.IO) {
        try {
            attempt.rawBinder?.unlinkToDeath(attempt.deathRecipient, 0)
        } catch (_: NoSuchElementException) {
            // 死亡通知已经移除了监听，重复清理是允许的。
        }
        try {
            attempt.remote?.unregisterCallback(attempt.callback)
        } catch (_: RemoteException) {
            // 服务已经死亡，无需继续注销。
        }
    }

    /** 一次调用只使用一个连接快照，传输失败绝不自动重放可能已经执行的写操作。 */
    suspend fun <T> call(block: (IAidlDemoService) -> T): T {
        assertMainThread()
        val attempt = readyAttempt()
        return invoke(attempt, block)
    }

    /** 发送前限制对象大小，服务端的事后校验不能防止请求本身超过 Binder 缓冲区。 */
    suspend fun echo(record: IpcRecord?): IpcRecord? {
        require((record?.text?.length ?: 0) <= AidlDemoService.MAX_TEXT_LENGTH) {
            "文本超过 ${AidlDemoService.MAX_TEXT_LENGTH} 字符，请使用 FD"
        }
        return call { it.echo(record) }
    }

    private suspend fun <T> invoke(attempt: Attempt, block: (IAidlDemoService) -> T): T {
        try {
            return withContext(Dispatchers.IO) { block(checkNotNull(attempt.remote)) }
        } catch (e: DeadObjectException) {
            lost(attempt, "调用期间 Binder 死亡；本次调用结果不确定，不自动重放")
            throw e
        }
    }

    suspend fun runTask(delayMillis: Long, timeoutMillis: Long, requestId: String = UUID.randomUUID().toString()): TaskResult {
        assertMainThread()
        require(timeoutMillis > 0)
        val attempt = readyAttempt()
        check(!attempt.pending.containsKey(requestId)) { "客户端已有同名等待任务" }
        val result = CompletableDeferred<TaskResult>()
        attempt.pending[requestId] = result
        try {
            // 先登记再提交，服务端零延迟完成也不会丢失回调。
            val receipt = invoke(attempt) { it.submitTask(attempt.callback, requestId, delayMillis) }
            if (receipt.code != IAidlDemoService.STATUS_OK) {
                result.cancel()
                throw TaskRejectedException(receipt.code, receipt.message)
            }
            return withTimeout(timeoutMillis) { result.await() }
        } catch (e: TimeoutCancellationException) {
            emit("任务 $requestId 等待超时；发起独立 cancelTask，不代表远端必然未完成")
            throw e
        } finally {
            if (attempt.pending[requestId] === result) attempt.pending.remove(requestId)
            if (!result.isCompleted) {
                result.cancel()
                // 协程取消不能中断已发出的 Binder transact；通过独立协议尽力撤销远端任务。
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        attempt.remote?.cancelTask(attempt.callback, requestId)
                    } catch (_: RemoteException) {
                        // 断线后不会把旧任务转发给新进程。
                    }
                }
            }
        }
    }

    suspend fun cancelTask(requestId: String): Boolean {
        assertMainThread()
        val attempt = readyAttempt()
        return invoke(attempt) { it.cancelTask(attempt.callback, requestId) }
    }

    private fun readyAttempt(): Attempt {
        check(state.value == State.CONNECTED) { "尚未连接，请等待 CONNECTED" }
        return checkNotNull(current)
    }

    fun close() {
        assertMainThread()
        if (closed) return
        disconnect()
        closed = true
        mutableState.value = State.CLOSED
        scope.cancel()
    }

    private fun emit(message: String) {
        mutableEvents.tryEmit(message)
    }

    private fun assertMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "连接状态操作必须在主线程调用" }
    }
}
