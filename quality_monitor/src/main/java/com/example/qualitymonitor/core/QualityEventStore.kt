package com.example.qualitymonitor.core

import android.content.Context
import com.example.qualitymonitor.QualityMonitorConfig
import com.example.qualitymonitor.breadcrumb.QualityBreadcrumbs
import com.example.qualitymonitor.util.ProcessName
import java.io.File
import java.util.UUID
import org.json.JSONObject

/**
 * 质量事件本地队列。
 *
 * 所有采集器先把事件同步写入 App 私有目录，再由后台线程尝试上传；这样崩溃现场、
 * 无网络或进程即将退出时也能尽量保住现场数据。
 */
class QualityEventStore(
    context: Context,
    private val config: QualityMonitorConfig,
    private val breadcrumbs: QualityBreadcrumbs,
) {
    private val rootDir = File(context.filesDir, "quality_monitor")

    @Synchronized
    fun enqueue(eventType: String, payload: JSONObject): File {
        // 不同事件类型分目录存储，便于本地排查时快速定位崩溃、ANR 或性能事件。
        val dir = File(rootDir, eventType).apply {
            if (!exists()) mkdirs()
        }
        val event = QualityEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            timestampMillis = System.currentTimeMillis(),
            payload = payload,
        )
        val file = File(dir, "${event.timestampMillis}_${event.eventId}.json")
        file.writeText(event.toJson(commonPayload(), breadcrumbs.snapshot()).toString())
        trim(dir)
        return file
    }

    @Synchronized
    fun pendingFiles(): List<File> {
        if (!rootDir.exists()) return emptyList()
        return rootDir.walkTopDown()
            // .json 是 Kotlin 侧事件，.qmon 是 Native dumper 直接生成的 tombstone。
            .filter { it.isFile && (it.extension == "json" || it.extension == "qmon") }
            .sortedBy { it.lastModified() }
            .toList()
    }

    @Synchronized
    fun markUploaded(file: File) {
        file.delete()
    }

    fun nativeCrashDir(): File {
        // Native 信号处理器不能安全调用 Java API，因此启动时提前把可写目录传给 so。
        return File(rootDir, "native_crash").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun commonPayload(): JSONObject {
        return JSONObject()
            .put("appId", config.appId)
            .put("versionName", config.versionName)
            .put("versionCode", config.versionCode)
            .put("processName", ProcessName.current())
            .put("foreground", breadcrumbs.foreground)
            .put("currentActivity", breadcrumbs.currentActivity)
    }

    private fun trim(dir: File) {
        val files = dir.listFiles { file -> file.isFile }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
        if (files.size <= MAX_FILES_PER_TYPE) return
        // 本地队列按类型限流，避免服务端异常或长期离线时把 App 私有存储写满。
        files.take(files.size - MAX_FILES_PER_TYPE).forEach { it.delete() }
    }

    companion object {
        private const val MAX_FILES_PER_TYPE = 30
    }
}
