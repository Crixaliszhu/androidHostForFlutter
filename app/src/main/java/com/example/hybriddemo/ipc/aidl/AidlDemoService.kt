package com.example.hybriddemo.ipc.aidl

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Log
import com.example.hybriddemo.BuildConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** 独立 :aidl_demo 进程的服务；Binder 线程只做短操作，延迟任务交给受限调度器。 */
class AidlDemoService : Service() {
    companion object {
        const val PROCESS_SUFFIX = ":aidl_demo"
        const val MAX_TEXT_LENGTH = 2048
        const val PAYLOAD_BYTES = 256 * 1024
        private const val TAG = "AidlDemoService"
    }

    /** 每个回调 Binder 对应一个会话，避免不同客户端使用相同 requestId 时互相取消。 */
    private class ClientSession(val callback: IAidlDemoCallback) {
        val tasks = mutableMapOf<String, PendingTask>()
    }

    /** 身份令牌防止旧任务与取消后同名的新任务发生竞态，误删新任务的登记。 */
    private class PendingTask {
        var future: ScheduledFuture<*>? = null
    }

    private val lock = Any()
    private val fileLock = Any()
    private val counter = AtomicLong()
    private val clients = mutableMapOf<IBinder, ClientSession>()
    private var destroyed = false
    private val scheduler = ScheduledThreadPoolExecutor(2).apply {
        // 被取消的延迟任务立即移出队列，避免长时间持有客户端引用。
        removeOnCancelPolicy = true
    }
    private val events = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(128),
    )
    private val callbacks = object : RemoteCallbackList<IAidlDemoCallback>() {
        override fun onCallbackDied(callback: IAidlDemoCallback) {
            // 此回调来自 Binder 线程，和主动注销走相同的任务清理路径。
            removeClient(callback.asBinder())
            Log.i(TAG, "客户端死亡，已清理任务")
        }
    }

    private val binder = object : IAidlDemoService.Stub() {
        override fun getServiceInfo(): Bundle {
            enforceCaller()
            return synchronized(lock) {
                Bundle().apply {
                    putInt("protocolVersion", IAidlDemoService.PROTOCOL_VERSION)
                    putInt("pid", Process.myPid())
                    putInt("uid", Process.myUid())
                    putString("thread", Thread.currentThread().name)
                    putLong("counter", counter.get())
                    putInt("callbacks", clients.size)
                    putInt("pendingTasks", clients.values.sumOf { it.tasks.size })
                }
            }
        }

        override fun add(a: Int, b: Int): Int {
            enforceCaller()
            val sum = a.toLong() + b
            if (sum !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) invalid("加法溢出")
            return sum.toInt()
        }

        override fun echo(record: IpcRecord?): IpcRecord? {
            enforceCaller()
            record?.let { validateRecord(it) }
            return record?.let { IpcRecord(it.id, it.text, it.value) }
        }

        override fun getRecords(offset: Int, limit: Int): MutableList<IpcRecord> {
            enforceCaller()
            if (offset !in 0..100 || limit !in 1..20) invalid("offset 必须为 0..100，limit 为 1..20")
            return (offset until minOf(offset + limit, 100)).map {
                IpcRecord(it, "远程记录 $it", it.toLong())
            }.toMutableList()
        }

        override fun demonstrateDirections(input: IpcRecord, output: IpcRecord, both: IpcRecord) {
            enforceCaller()
            validateRecord(input)
            validateRecord(both)
            input.text = "服务端修改 in，客户端不会收到"
            output.id = 20
            output.text = "服务端填充 out"
            output.value = 200
            both.text = "${both.text.orEmpty()} → 服务端"
            both.value += 1
        }

        override fun incrementCounter(delta: Int): Long {
            enforceCaller()
            if (delta !in 1..100) invalid("delta 必须为 1..100")
            val value = counter.addAndGet(delta.toLong())
            postEvent { broadcastCounter(value) }
            return value
        }

        override fun registerCallback(callback: IAidlDemoCallback) {
            enforceCaller()
            synchronized(lock) {
                check(!destroyed) { "服务已销毁" }
                val key = callback.asBinder()
                if (clients.containsKey(key)) return
                check(clients.size < 16) { "客户端数量已满" }
                if (!callbacks.register(callback)) throw RemoteException("回调 Binder 已死亡")
                clients[key] = ClientSession(callback)
            }
        }

        override fun unregisterCallback(callback: IAidlDemoCallback) {
            enforceCaller()
            synchronized(lock) {
                callbacks.unregister(callback)
                removeClient(callback.asBinder())
            }
        }

        override fun submitTask(callback: IAidlDemoCallback, requestId: String, delayMillis: Long): TaskReceipt {
            enforceCaller()
            if (requestId.isBlank() || requestId.length > 64 || delayMillis !in 0..10_000) {
                return TaskReceipt(IAidlDemoService.ERROR_INVALID_ARGUMENT, "requestId 长度为 1..64，delayMillis 为 0..10000")
            }
            synchronized(lock) {
                val client = clients[callback.asBinder()]
                    ?: return TaskReceipt(IAidlDemoService.ERROR_NOT_REGISTERED, "请先注册回调")
                if (client.tasks.containsKey(requestId)) {
                    return TaskReceipt(IAidlDemoService.ERROR_DUPLICATE_REQUEST, "任务正在执行")
                }
                if (client.tasks.size >= 16) return TaskReceipt(IAidlDemoService.ERROR_BUSY, "任务队列已满")
                // 调度和登记在同一个锁内，零延迟任务也不能先于登记完成。
                val pending = PendingTask()
                client.tasks[requestId] = pending
                pending.future = scheduler.schedule({
                    val completed = synchronized(lock) {
                        if (client.tasks[requestId] === pending) {
                            client.tasks.remove(requestId)
                            true
                        } else false
                    }
                    if (completed) postEvent {
                        deliver(client.callback, requestId, IAidlDemoService.STATUS_OK,
                            IpcRecord(1, "异步任务完成，PID=${Process.myPid()}", delayMillis))
                    }
                }, delayMillis, TimeUnit.MILLISECONDS)
            }
            return TaskReceipt(IAidlDemoService.STATUS_OK, "任务已受理")
        }

        override fun cancelTask(callback: IAidlDemoCallback, requestId: String): Boolean {
            enforceCaller()
            validateRequest(requestId)
            val client: ClientSession
            synchronized(lock) {
                client = clients[callback.asBinder()] ?: return false
                val task = client.tasks.remove(requestId) ?: return false
                task.future?.cancel(false)
            }
            postEvent { deliver(client.callback, requestId, IAidlDemoService.STATUS_CANCELLED,
                IpcRecord(0, "任务已取消", 0)) }
            return true
        }

        override fun openPayload(): ParcelFileDescriptor {
            enforceCaller()
            // 文件名固定且只读，不接受任意路径。数据走 FD，Binder 只传递句柄。
            return synchronized(fileLock) {
                try {
                    val file = File(cacheDir, "aidl-demo-payload.bin")
                    if (!file.exists() || file.length() != PAYLOAD_BYTES.toLong()) {
                        file.writeBytes(ByteArray(PAYLOAD_BYTES) { (it % 251).toByte() })
                    }
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } catch (e: IOException) {
                    // Binder 不支持透传任意异常类型，转换成公开且可封送的状态异常。
                    throw IllegalStateException("创建示例数据文件失败：${e.message}")
                }
            }
        }

        override fun simulateProcessDeath() {
            enforceCaller()
            if (!BuildConfig.DEBUG) throw SecurityException("只有 Debug 可模拟进程死亡")
            scheduler.schedule({ Process.killProcess(Process.myPid()) }, 300, TimeUnit.MILLISECONDS)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun enforceCaller() {
        // 必须在 Binder 入口取调用方 UID，切换到线程池以后读取会变成服务端身份。
        if (Binder.getCallingUid() != Process.myUid()) throw SecurityException("仅允许本应用 UID")
    }

    private fun validateRecord(record: IpcRecord) {
        if ((record.text?.length ?: 0) > MAX_TEXT_LENGTH) invalid("文本超过 $MAX_TEXT_LENGTH 字符，请使用 FD")
    }

    private fun validateRequest(requestId: String) {
        if (requestId.isBlank() || requestId.length > 64) invalid("requestId 长度必须为 1..64")
    }

    private fun invalid(message: String): Nothing =
        throw IllegalArgumentException(message)

    private fun removeClient(key: IBinder) = synchronized(lock) {
        clients.remove(key)?.tasks?.let { tasks ->
            tasks.values.forEach { it.future?.cancel(false) }
            tasks.clear()
        }
        Unit
    }

    private fun postEvent(block: () -> Unit) {
        try {
            events.execute(block)
        } catch (_: RejectedExecutionException) {
            // 极端背压或销毁时允许丢弃通知，调用方应查询快照/超时，不把回调当可靠消息队列。
            Log.w(TAG, "通知队列已满或已关闭，丢弃本次事件")
        }
    }

    private fun deliver(callback: IAidlDemoCallback, id: String, status: Int, record: IpcRecord) {
        try {
            callback.onTaskFinished(id, status, record)
        } catch (e: RemoteException) {
            if (!callback.asBinder().isBinderAlive) removeClient(callback.asBinder())
            Log.w(TAG, "任务回调投递失败，客户端通过超时恢复", e)
        }
    }

    private fun broadcastCounter(value: Long) {
        // 所有 beginBroadcast 在同一个事件线程串行执行，且不持有业务锁调用客户端。
        val count = callbacks.beginBroadcast()
        try {
            repeat(count) { index ->
                try {
                    callbacks.getBroadcastItem(index).onCounterChanged(value)
                } catch (_: RemoteException) {
                    // 单个客户端死亡不能中断对其他客户端的通知。
                }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    override fun onDestroy() {
        synchronized(lock) {
            destroyed = true
            clients.keys.toList().forEach { removeClient(it) }
        }
        scheduler.shutdownNow()
        // kill 清除注册和死亡监听；已取得的广播快照仍由事件线程的 finally 释放。
        callbacks.kill()
        events.shutdownNow()
        super.onDestroy()
    }
}
