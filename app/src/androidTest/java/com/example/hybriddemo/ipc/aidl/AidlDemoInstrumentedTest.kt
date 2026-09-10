package com.example.hybriddemo.ipc.aidl

import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** 必须在设备运行，验证真实 Binder 序列化、线程并发、任务归属和进程死亡，不能用本地 Stub 替代。 */
@RunWith(AndroidJUnit4::class)
class AidlDemoInstrumentedTest {
    private lateinit var client: AidlDemoClient

    @Before
    fun connect() = onMain {
        client = AidlDemoClient(ApplicationProvider.getApplicationContext())
        client.connect()
        awaitConnected(client)
        // 上一个测试的解绑清理在 IO 线程执行，等待会话归一后再断言客户端数量。
        withTimeout(3_000) {
            while (client.call { it.serviceInfo.getInt("callbacks") } != 1) delay(20)
        }
    }

    @After
    fun close() = onMain { client.close() }

    @Test
    fun remotePidAndParcelableRoundTrip() = onMain {
        val snapshot = client.call { it.serviceInfo }
        assertNotEquals(Process.myPid(), snapshot.getInt("pid"))
        assertEquals(Process.myUid(), snapshot.getInt("uid"))
        assertEquals(IAidlDemoService.PROTOCOL_VERSION, snapshot.getInt("protocolVersion"))
        val original = IpcRecord(7, "跨进程中文", 42)
        val copy = client.echo(original)!!
        assertNotSame(original, copy)
        assertEquals(original.text, copy.text)
        assertEquals(42L, copy.value)
        assertNull(client.echo(null))
        assertEquals(8, client.call { it.add(3, 5) })
        assertEquals(listOf(98, 99), client.call { it.getRecords(98, 20).map { row -> row.id } })
        assertTrue(client.call { it.getRecords(100, 1).isEmpty() })
    }

    @Test
    fun parcelDirectionsCopyBackOnlyOutAndInout() = onMain {
        val input = IpcRecord(1, "原始", 10)
        val output = IpcRecord(2, "旧输出", 20)
        val both = IpcRecord(3, "双向", 30)
        client.call { it.demonstrateDirections(input, output, both) }
        assertEquals("原始", input.text)
        assertEquals(10L, input.value)
        assertEquals(20, output.id)
        assertEquals(200L, output.value)
        assertEquals("双向 → 服务端", both.text)
        assertEquals(31L, both.value)
    }

    @Test
    fun concurrentWritesAreAtomic() = onMain {
        val before = client.call { it.serviceInfo.getLong("counter") }
        val results = coroutineScope {
            (1..40).map { async { client.call { it.incrementCounter(1) } } }.awaitAll()
        }
        assertEquals((before + 1..before + 40).toList(), results.sorted())
        assertEquals(before + 40, client.call { it.serviceInfo.getLong("counter") })
    }

    @Test
    fun businessFailuresDoNotKillConnection() = onMain {
        expectArgumentError { client.call { it.add(Int.MAX_VALUE, 1) } }
        expectArgumentError { client.call { it.getRecords(-1, 1) } }
        expectArgumentError {
            client.call { it.echo(IpcRecord(1, "a".repeat(2049), 0)) }
        }
        val localError = runCatching { client.echo(IpcRecord(1, "a".repeat(2049), 0)) }.exceptionOrNull()
        assertTrue(localError is IllegalArgumentException)
        val taskError = runCatching { client.runTask(-1, 1000) }.exceptionOrNull()
        assertTrue(taskError is AidlDemoClient.TaskRejectedException)
        assertEquals(IAidlDemoService.ERROR_INVALID_ARGUMENT, (taskError as AidlDemoClient.TaskRejectedException).code)
        assertEquals(3, client.call { it.add(1, 2) })
    }

    @Test
    fun immediateAndDelayedCallbacksCompleteWaiters() = onMain {
        assertEquals(IAidlDemoService.STATUS_OK, client.runTask(0, 3_000).status)
        assertEquals(IAidlDemoService.STATUS_OK, client.runTask(100, 3_000).status)
    }

    @Test
    fun timeoutAndCoroutineCancellationCleanRemoteTasks() = onMain {
        val error = runCatching { client.runTask(8_000, 100) }.exceptionOrNull()
        assertTrue(error is kotlinx.coroutines.TimeoutCancellationException)
        assertEquals(0, client.call { it.serviceInfo.getInt("pendingTasks") })
        coroutineScope {
            val task = async { client.runTask(8_000, 10_000, "cancel-coroutine") }
            waitForTasks(1)
            task.cancel()
            task.join()
        }
        assertEquals(0, client.call { it.serviceInfo.getInt("pendingTasks") })
    }

    @Test
    fun twoClientsOwnIndependentTasksWithSameId() = onMain {
        val second = AidlDemoClient(ApplicationProvider.getApplicationContext())
        try {
            second.connect()
            awaitConnected(second)
            assertEquals(2, client.call { it.serviceInfo.getInt("callbacks") })
            coroutineScope {
                val firstTask = async { client.runTask(2_000, 5_000, "shared-id") }
                val secondTask = async { second.runTask(500, 5_000, "shared-id") }
                waitForTasks(2)
                assertTrue(client.cancelTask("shared-id"))
                assertEquals(IAidlDemoService.STATUS_CANCELLED, firstTask.await().status)
                assertEquals(IAidlDemoService.STATUS_OK, secondTask.await().status)
                assertFalse(second.cancelTask("shared-id"))
            }
        } finally {
            second.close()
        }
        withTimeout(3_000) {
            while (client.call { it.serviceInfo.getInt("callbacks") } != 1) delay(20)
        }
    }

    @Test
    fun registrationIsIdempotentAndTaskQueueIsBounded() = onMain {
        val callback = RecordingCallback()
        expectReceipt(IAidlDemoService.ERROR_NOT_REGISTERED) {
            client.call { it.submitTask(callback, "unregistered", 100) }
        }
        try {
            client.call { it.registerCallback(callback); it.registerCallback(callback) }
            assertEquals(2, client.call { it.serviceInfo.getInt("callbacks") })
            client.call { it.submitTask(callback, "duplicate", 10_000) }
            expectReceipt(IAidlDemoService.ERROR_DUPLICATE_REQUEST) {
                client.call { it.submitTask(callback, "duplicate", 10_000) }
            }
            repeat(15) { index -> client.call { it.submitTask(callback, "task-$index", 10_000) } }
            expectReceipt(IAidlDemoService.ERROR_BUSY) {
                client.call { it.submitTask(callback, "overflow", 10_000) }
            }
        } finally {
            client.call { it.unregisterCallback(callback); it.unregisterCallback(callback) }
        }
        assertEquals(0, client.call { it.serviceInfo.getInt("pendingTasks") })
    }

    @Test
    fun callbackRunsOffMainThreadAndCanBeUnregistered() = onMain {
        val callback = RecordingCallback()
        client.call { it.registerCallback(callback); it.incrementCounter(1) }
        val event = withContext(Dispatchers.IO) { callback.threads.poll(3, TimeUnit.SECONDS) }
        assertTrue(event?.startsWith("Binder:") == true)
        client.call { it.unregisterCallback(callback); it.incrementCounter(1) }
        assertNull(withContext(Dispatchers.IO) { callback.threads.poll(200, TimeUnit.MILLISECONDS) })
    }

    @Test
    fun largePayloadUsesDescriptorAndClosesStream() = onMain {
        val content = client.call {
            ParcelFileDescriptor.AutoCloseInputStream(it.openPayload()).use { stream -> stream.readBytes() }
        }
        assertEquals(AidlDemoService.PAYLOAD_BYTES, content.size)
        assertTrue(content.indices.all { content[it] == (it % 251).toByte() })
    }

    @Test
    fun serviceDeathFailsPendingTaskAndReconnectsWithoutReplayingWrites() = onMain {
        val oldPid = client.call { it.serviceInfo.getInt("pid") }
        client.call { it.incrementCounter(5) }
        coroutineScope {
            val pending = async { runCatching { client.runTask(9_000, 12_000) } }
            waitForTasks(1)
            client.call { it.simulateProcessDeath() }
            assertTrue(withTimeout(10_000) { pending.await() }.isFailure)
        }
        withTimeout(20_000) { client.info.first { it != null && it.getInt("pid") != oldPid } }
        awaitConnected(client)
        assertEquals(0L, client.call { it.serviceInfo.getLong("counter") })
        assertEquals(1, client.call { it.serviceInfo.getInt("callbacks") })
        assertEquals(IAidlDemoService.STATUS_OK, client.runTask(0, 3_000).status)
    }

    @Test
    fun explicitDisconnectDoesNotReconnectAndRejectsCalls() = onMain {
        client.disconnect()
        delay(800)
        assertEquals(AidlDemoClient.State.DISCONNECTED, client.state.value)
        assertTrue(runCatching { client.call { it.add(1, 2) } }.exceptionOrNull() is IllegalStateException)
        client.connect()
        awaitConnected(client)
        assertEquals(3, client.call { it.add(1, 2) })
    }

    private suspend fun awaitConnected(target: AidlDemoClient) {
        withTimeout(15_000) { target.state.first { it == AidlDemoClient.State.CONNECTED } }
    }

    private suspend fun waitForTasks(count: Int) {
        withTimeout(3_000) {
            while (client.call { it.serviceInfo.getInt("pendingTasks") } != count) delay(10)
        }
    }

    private suspend fun expectArgumentError(block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue("实际异常=$error", error is IllegalArgumentException)
    }

    private suspend fun expectReceipt(code: Int, block: suspend () -> TaskReceipt) {
        assertEquals(code, block().code)
    }

    private fun onMain(block: suspend () -> Unit) = runBlocking {
        withContext(Dispatchers.Main) { block() }
    }

    /** 测试用 oneway 回调，只收集线程名，不在 Binder 线程调用 JUnit 断言。 */
    private class RecordingCallback : IAidlDemoCallback.Stub() {
        val threads = LinkedBlockingQueue<String>()
        override fun onTaskFinished(requestId: String, status: Int, result: IpcRecord) = Unit
        override fun onCounterChanged(value: Long) {
            threads.offer(Thread.currentThread().name)
        }
    }
}
