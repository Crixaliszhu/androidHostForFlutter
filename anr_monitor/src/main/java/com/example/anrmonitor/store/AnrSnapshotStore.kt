package com.example.anrmonitor.store

import android.content.Context
import com.example.anrmonitor.config.SelfAnrConfig
import com.example.anrmonitor.entity.AnrEvent
import java.io.File

/**
 * 用本地文件实现的小型 ANR 事件队列。
 *
 * ANR 采集不能依赖当时网络可用，所以每个事件都会先落盘，再尝试上传。
 */
class AnrSnapshotStore(
    context: Context,
    private val config: SelfAnrConfig,
) {
    private val dir = File(context.filesDir, "self_anr")

    @Synchronized
    fun enqueue(event: AnrEvent) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        File(dir, "${event.timestampMillis}_${event.id}.json").writeText(event.toJson().toString())
        trim()
    }

    @Synchronized
    fun pendingEvents(): List<File> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.lastModified() }
            .orEmpty()
    }

    @Synchronized
    fun markUploaded(file: File) {
        file.delete()
    }

    @Synchronized
    private fun trim() {
        val files = pendingEvents()
        if (files.size <= config.maxPendingEvents) return
        files.take(files.size - config.maxPendingEvents).forEach { it.delete() }
    }
}
