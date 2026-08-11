package com.example.qualitymonitor.memory.leak

import android.content.Context
import android.os.Debug
import java.io.File

/**
 * HPROF 堆快照导出器。
 *
 * HPROF 文件体积大且 dump 时会暂停进程，因此只在明确配置允许时由泄露确认流程调用。
 */
internal class HprofDumper(context: Context) {
    private val hprofDir = File(context.filesDir, "quality_monitor/hprof").apply {
        if (!exists()) mkdirs()
    }

    fun dump(reason: String): File? {
        val file = File(hprofDir, "${System.currentTimeMillis()}_${reason}.hprof")
        return runCatching {
            Debug.dumpHprofData(file.absolutePath)
            file
        }.getOrNull()
    }
}
