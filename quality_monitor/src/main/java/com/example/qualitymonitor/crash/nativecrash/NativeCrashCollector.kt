package com.example.qualitymonitor.crash.nativecrash

import android.util.Log
import com.example.qualitymonitor.core.QualityEventStore

/**
 * Native 崩溃采集器。
 *
 * Kotlin 侧负责创建 tombstone 目录和加载 so；真正的信号处理、fork 子进程 dump、
 * frame pointer 回溯和 maps 读取都在 native 层完成，减少崩溃现场调用 Java 的风险。
 */
class NativeCrashCollector(
    private val store: QualityEventStore,
) {
    fun install() {
        val dir = store.nativeCrashDir()
        // 目录路径提前传入 native，信号处理器里只能使用已准备好的简单 C 数据。
        NativeCrashBridge.init(dir.absolutePath)
        Log.i("QualityMonitor", "Native crash 目录：${dir.absolutePath}")
    }

    fun triggerNativeCrashForTest(): Boolean {
        return NativeCrashBridge.crashForTest()
    }

    /**
     * Native crash JNI 桥接对象。
     *
     * 只暴露初始化和测试崩溃两个入口，避免上层业务直接接触信号处理细节。
     */
    private object NativeCrashBridge {
        private val loaded = runCatching {
            // so 加载失败只记录日志，避免低端或 ABI 异常设备因监控模块导致启动崩溃。
            System.loadLibrary("quality_native_crash")
        }.onFailure {
            Log.e("QualityMonitor", "加载 native crash 库失败", it)
        }.isSuccess

        fun init(tombstoneDir: String): Boolean {
            if (!loaded) return false
            nativeInit(tombstoneDir)
            return true
        }

        fun crashForTest(): Boolean {
            if (!loaded) return false
            nativeCrashForTest()
            return true
        }

        private external fun nativeInit(tombstoneDir: String)
        private external fun nativeCrashForTest()
    }
}
