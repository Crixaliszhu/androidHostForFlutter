package com.example.hybriddemo.anrdemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import com.alibaba.android.arouter.facade.annotation.Route
import com.example.hybriddemo.BuildConfig
import com.example.hybriddemo.databinding.ActivityAnrCasesDemoBinding
import com.example.qualitymonitor.QualityMonitorInitializer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.math.sqrt

@Route(path = com.example.hybriddemo.router.DemoRouterPaths.ANR_CASES)
class AnrCasesDemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnrCasesDemoBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Object()
    private var busyLoopGuard = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnrCasesDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMainCpuAnr.setOnClickListener {
            showStatus("主线程即将执行 15 秒 CPU 忙等。")
            mainHandler.postDelayed({ blockMainThreadWithCpu() }, START_DELAY_MS)
        }

        binding.btnMainIoAnr.setOnClickListener {
            showStatus("主线程即将持续写入并同步文件，观察 trace 中的文件 IO 栈。")
            mainHandler.postDelayed({ blockMainThreadWithFileIo() }, START_DELAY_MS)
        }

        binding.btnIpcAnr.setOnClickListener {
            showStatus("主线程即将同步访问远程 ContentProvider，观察 BinderProxy.transact。")
            mainHandler.postDelayed({ blockMainThreadWithRemoteProvider() }, START_DELAY_MS)
        }

        binding.btnLockAnr.setOnClickListener {
            showStatus("后台线程先持有锁，主线程稍后进入锁等待。")
            mainHandler.postDelayed({ blockMainThreadWithLock() }, START_DELAY_MS)
        }

        binding.btnBroadcastAnr.setOnClickListener {
            showStatus("即将发送显式广播，Receiver 会在主线程阻塞。")
            sendBroadcast(Intent(this, AnrDemoReceiver::class.java))
        }

        binding.btnServiceAnr.setOnClickListener {
            showStatus("即将启动 Service，Service 生命周期回调会阻塞主线程。")
            startService(Intent(this, AnrDemoService::class.java))
        }

        binding.btnProviderAnr.setOnClickListener {
            showStatus("即将访问慢 Provider，模拟 Provider 查询导致调用方卡住。")
            mainHandler.postDelayed({ blockMainThreadWithRemoteProvider() }, START_DELAY_MS)
        }

        binding.btnInputAnr.setOnClickListener {
            showStatus("300ms 后主线程阻塞 15 秒；阻塞期间继续点击屏幕，更容易触发 Input dispatching timed out。")
            mainHandler.postDelayed({ blockMainThreadWithCpu() }, START_DELAY_MS)
        }

        binding.btnNativeCrash.setOnClickListener {
            showStatus("300ms 后触发 Native 崩溃；重启后查看 files/quality_monitor/native_crash/*.qmon。")
            mainHandler.postDelayed({
                if (!QualityMonitorInitializer.triggerNativeCrashForTest()) {
                    showStatus("Native crash 未触发：质量监控未初始化或 native so 加载失败。")
                }
            }, START_DELAY_MS)
        }
    }

    private fun blockMainThreadWithCpu() {
        val startTime = SystemClock.elapsedRealtime()
        var counter = 0L
        while (SystemClock.elapsedRealtime() - startTime < BLOCK_DURATION_MS) {
            counter++
            busyLoopGuard += sqrt(counter.toDouble())
            if (counter % 100_000L == 0L) {
                Thread.yield()
            }
        }
        showStatus("CPU 忙等结束。")
    }

    private fun blockMainThreadWithFileIo() {
        val file = File(cacheDir, "anr_main_io_demo.bin")
        val buffer = ByteArray(256 * 1024) { index -> (index % 128).toByte() }
        val startTime = SystemClock.elapsedRealtime()
        FileOutputStream(file, false).use { output ->
            // 每轮写入后 fsync，目的是让 trace 更容易停在真实磁盘 IO，而不是只停在普通循环里。
            while (SystemClock.elapsedRealtime() - startTime < BLOCK_DURATION_MS) {
                output.write(buffer)
                output.fd.sync()
                if (file.length() > MAX_IO_FILE_BYTES) {
                    output.channel.truncate(0)
                }
            }
        }
        file.delete()
        showStatus("主线程文件 IO 结束。")
    }

    private fun blockMainThreadWithRemoteProvider() {
        val uri = Uri.parse("content://${BuildConfig.APPLICATION_ID}.anr.provider/slow")
        contentResolver.query(uri, null, null, null, null)?.close()
        showStatus("远程 Provider 查询结束。")
    }

    private fun blockMainThreadWithLock() {
        val lockReady = CountDownLatch(1)
        thread(name = "anr-demo-lock-owner") {
            synchronized(lock) {
                lockReady.countDown()
                SystemClock.sleep(BLOCK_DURATION_MS)
            }
        }
        lockReady.await()
        synchronized(lock) {
            showStatus("主线程锁等待结束。")
        }
    }

    private fun showStatus(message: String) {
        binding.tvAnrStatus.text = message
    }

    companion object {
        private const val START_DELAY_MS = 300L
        private const val BLOCK_DURATION_MS = 15_000L
        private const val MAX_IO_FILE_BYTES = 64L * 1024L * 1024L
    }
}
