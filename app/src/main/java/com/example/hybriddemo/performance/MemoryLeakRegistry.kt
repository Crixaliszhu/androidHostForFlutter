package com.example.hybriddemo.performance

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.view.View
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 仅用于 Demo 的静态持有器：通过故意不释放对象，放大 Memory Profiler 中的内存变化。
 */
object MemoryLeakRegistry {
    val leakedActivities = mutableListOf<Activity>()
    val leakedClickListeners = mutableListOf<View.OnClickListener>()
    val javaHeapBlocks = mutableListOf<ByteArray>()
    val bitmapBlocks = mutableListOf<Bitmap>()
    val nativeBuffers = mutableListOf<ByteBuffer>()
    val retainedThreads = mutableListOf<Thread>()
    private val delayedCallbacks = mutableListOf<Pair<Handler, Runnable>>()
    private val threadRunning = AtomicBoolean(false)

    fun leak(
        activity: Activity,
        listener: View.OnClickListener,
        handler: Handler,
        delayedTask: Runnable,
    ) {
        leakedActivities += activity
        leakedClickListeners += listener
        delayedCallbacks += handler to delayedTask
    }

    fun addJavaHeapBlock(block: ByteArray) {
        javaHeapBlocks += block
    }

    fun addBitmap(bitmap: Bitmap) {
        bitmapBlocks += bitmap
    }

    fun addNativeBuffer(buffer: ByteBuffer) {
        nativeBuffers += buffer
    }

    fun javaHeapBytes(): Int = javaHeapBlocks.sumOf { it.size }

    fun bitmapBytes(): Int = bitmapBlocks.sumOf { it.allocationByteCount }

    fun nativeBufferBytes(): Int = nativeBuffers.sumOf { it.capacity() }

    fun startRetainedThreads(count: Int) {
        threadRunning.set(true)
        repeat(count) { index ->
            val thread = Thread({
                while (threadRunning.get()) {
                    try {
                        Thread.sleep(1_000L)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }, "memory-demo-retained-thread-$index")
            thread.start()
            retainedThreads += thread
        }
    }

    fun clear() {
        delayedCallbacks.forEach { (handler, runnable) ->
            handler.removeCallbacks(runnable)
        }
        delayedCallbacks.clear()
        leakedActivities.clear()
        leakedClickListeners.clear()
        javaHeapBlocks.clear()
        bitmapBlocks.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        bitmapBlocks.clear()
        nativeBuffers.clear()
        threadRunning.set(false)
        retainedThreads.forEach { thread ->
            thread.interrupt()
        }
        retainedThreads.clear()
    }
}
