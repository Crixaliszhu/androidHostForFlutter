package com.example.hybriddemo.performance

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.hybriddemo.databinding.ActivityMemoryLeakDemoBinding
import java.nio.ByteBuffer

class MemoryLeakDemoActivity : AppCompatActivity() {

    companion object {
        private const val MB = 1024 * 1024
        private const val JAVA_HEAP_BLOCK_MB = 6
        private const val MAX_JAVA_HEAP_MB = 72
        private const val BITMAP_WIDTH = 720
        private const val BITMAP_HEIGHT = 1280
        private const val MAX_BITMAP_COUNT = 8
        private const val NATIVE_BUFFER_MB = 8
        private const val MAX_NATIVE_BUFFER_MB = 64
        private const val MAX_RETAINED_THREAD_COUNT = 48
    }

    private lateinit var binding: ActivityMemoryLeakDemoBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val demoPayload = ByteArray(2 * MB)

    private val delayedTask = Runnable {
        binding.tvLeakStatus.text = "延迟任务执行完成"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryLeakDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCreateLeak.setOnClickListener {
            createActivityLeak()
        }

        binding.btnAddJavaHeap.setOnClickListener {
            addJavaHeapPressure()
        }

        binding.btnAddBitmap.setOnClickListener {
            addBitmapPressure()
        }

        binding.btnAddNativeBuffer.setOnClickListener {
            addNativePressure()
        }

        binding.btnStartThreads.setOnClickListener {
            startThreadPressure()
        }

        binding.btnClearLeak.setOnClickListener {
            clearMemoryDemo()
        }

        binding.btnRequestGc.setOnClickListener {
            requestGc()
        }

        binding.btnFinishPage.setOnClickListener {
            finish()
        }
    }

    private fun createActivityLeak() {
        demoPayload[0] = 1
        val listener = View.OnClickListener {
            binding.tvLeakStatus.text = "静态 listener 仍然持有旧 Activity"
        }
        MemoryLeakRegistry.leak(this, listener, mainHandler, delayedTask)
        mainHandler.postDelayed(delayedTask, 10 * 60 * 1000L)
        binding.tvLeakStatus.text =
            "已制造泄漏：点击返回或关闭页面后，Activity 仍会被静态对象和 Handler 回调持有"
    }

    private fun addJavaHeapPressure() {
        val currentMb = MemoryLeakRegistry.javaHeapBytes() / MB
        if (currentMb >= MAX_JAVA_HEAP_MB) {
            updateStatus("Java Heap 示例已达到 ${MAX_JAVA_HEAP_MB}MB 上限，请先清理后再继续")
            return
        }
        runCatching {
            val block = ByteArray(JAVA_HEAP_BLOCK_MB * MB)
            touchEveryMemoryPage(block)
            MemoryLeakRegistry.addJavaHeapBlock(block)
            updateStatus("已新增约 ${JAVA_HEAP_BLOCK_MB}MB Java Heap：实时曲线会更容易看到 Java 区域上升")
        }.onFailure { error ->
            updateStatus("Java Heap 分配失败：${error.javaClass.simpleName}，请先清理示例内存")
        }
    }

    private fun addBitmapPressure() {
        if (MemoryLeakRegistry.bitmapBlocks.size >= MAX_BITMAP_COUNT) {
            updateStatus("Bitmap 示例已达到 ${MAX_BITMAP_COUNT} 张上限，请先清理后再继续")
            return
        }
        // Bitmap 示例：ARGB_8888 每个像素约 4 字节，图片/截图/海报最容易制造内存尖峰。
        runCatching {
            val index = MemoryLeakRegistry.bitmapBlocks.size
            val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xFF6A50AE.toInt() + index * 0x00050505)
            MemoryLeakRegistry.addBitmap(bitmap)
            updateStatus("已新增 1 张 ${BITMAP_WIDTH}x${BITMAP_HEIGHT} Bitmap：观察 Graphics/Native/Heap 的变化")
        }.onFailure { error ->
            updateStatus("Bitmap 分配失败：${error.javaClass.simpleName}，请先清理示例内存")
        }
    }

    private fun addNativePressure() {
        val currentMb = MemoryLeakRegistry.nativeBufferBytes() / MB
        if (currentMb >= MAX_NATIVE_BUFFER_MB) {
            updateStatus("DirectBuffer 示例已达到 ${MAX_NATIVE_BUFFER_MB}MB 上限，请先清理后再继续")
            return
        }
        // DirectBuffer 示例：allocateDirect 使用堆外内存，Java Heap 不一定明显上涨，但 PSS/Native 会变大。
        runCatching {
            val buffer = ByteBuffer.allocateDirect(NATIVE_BUFFER_MB * MB)
            touchEveryMemoryPage(buffer)
            MemoryLeakRegistry.addNativeBuffer(buffer)
            updateStatus("已新增约 ${NATIVE_BUFFER_MB}MB DirectBuffer：更适合用 Native Allocations 或 PSS 观察")
        }.onFailure { error ->
            updateStatus("DirectBuffer 分配失败：${error.javaClass.simpleName}，请先清理示例内存")
        }
    }

    private fun startThreadPressure() {
        if (MemoryLeakRegistry.retainedThreads.size >= MAX_RETAINED_THREAD_COUNT) {
            updateStatus("常驻线程示例已达到 ${MAX_RETAINED_THREAD_COUNT} 个上限，请先清理后再继续")
            return
        }
        // 线程示例：每个线程都有栈空间，线程泄漏会让 Stack/PSS 上升。
        MemoryLeakRegistry.startRetainedThreads(12)
        updateStatus("已启动 12 个常驻线程：观察 Stack/PSS，并在 Thread 列表中搜索 memory-demo")
    }

    private fun clearMemoryDemo() {
        MemoryLeakRegistry.clear()
        mainHandler.removeCallbacksAndMessages(null)
        updateStatus("已清理所有示例对象，可请求 GC 后重新观察内存曲线")
    }

    private fun requestGc() {
        Runtime.getRuntime().gc()
        Runtime.getRuntime().runFinalization()
        Runtime.getRuntime().gc()
        updateStatus("已请求 GC，请回到 Memory Profiler 重新抓取 Heap Dump")
    }

    private fun updateStatus(message: String) {
        val javaMb = MemoryLeakRegistry.javaHeapBytes() / MB
        val bitmapMb = MemoryLeakRegistry.bitmapBytes() / MB
        val nativeMb = MemoryLeakRegistry.nativeBufferBytes() / MB
        binding.tvLeakStatus.text = buildString {
            appendLine(message)
            appendLine()
            appendLine("当前保留对象：")
            appendLine("Activity 泄漏：${MemoryLeakRegistry.leakedActivities.size} 个")
            appendLine("Java Heap：约 ${javaMb}MB")
            appendLine("Bitmap：约 ${bitmapMb}MB")
            appendLine("DirectBuffer：约 ${nativeMb}MB")
            append("常驻线程：${MemoryLeakRegistry.retainedThreads.size} 个")
        }
    }

    private fun touchEveryMemoryPage(block: ByteArray) {
        // 按 4KB 页写入，避免“只分配但未实际提交物理页”导致实时内存曲线不明显。
        var index = 0
        while (index < block.size) {
            block[index] = (index % Byte.MAX_VALUE).toByte()
            index += 4 * 1024
        }
        block[block.lastIndex] = 1
    }

    private fun touchEveryMemoryPage(buffer: ByteBuffer) {
        // DirectBuffer 属于堆外内存，同样按页写入，让 Native/PSS 曲线更容易观察。
        var index = 0
        while (index < buffer.capacity()) {
            buffer.put(index, (index % Byte.MAX_VALUE).toByte())
            index += 4 * 1024
        }
        buffer.put(buffer.capacity() - 1, 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Demo 反例：这里故意不清理 registry 和 Handler 回调，方便在 Heap Dump 中看到已销毁 Activity 被持有。
    }
}
