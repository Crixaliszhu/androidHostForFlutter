package com.example.qualitymonitor.export

import android.content.Context
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 自研质量监控日志导出器。
 *
 * 正式包无法通过 adb run-as 读取 App 私有目录，因此通过这个公开 API 把
 * files/quality_monitor 下的崩溃、ANR 和性能日志打包成 zip，再交给宿主页面导出。
 */
object QualityMonitorLogExporter {
    private const val LOG_ROOT_NAME = "quality_monitor"
    private const val BUFFER_SIZE = 8 * 1024

    fun defaultFileName(nowMillis: Long = System.currentTimeMillis()): String {
        val timeText = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMillis))
        return "quality_monitor_logs_$timeText.zip"
    }

    fun summary(context: Context): LogSummary {
        val rootDir = logRootDir(context)
        val files = collectLogFiles(rootDir)
        return LogSummary(
            exists = rootDir.exists(),
            fileCount = files.size,
            totalBytes = files.sumOf { it.length() },
            rootPath = rootDir.absolutePath,
        )
    }

    fun exportToZip(context: Context, outputStream: OutputStream): ExportResult {
        val rootDir = logRootDir(context)
        val files = collectLogFiles(rootDir)
        if (files.isEmpty()) {
            return ExportResult(fileCount = 0, totalBytes = 0L)
        }

        ZipOutputStream(outputStream.buffered()).use { zip ->
            val buffer = ByteArray(BUFFER_SIZE)
            files.forEach { file ->
                val entryName = file.relativeTo(rootDir)
                    .invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { input ->
                    while (true) {
                        val readSize = input.read(buffer)
                        if (readSize <= 0) break
                        zip.write(buffer, 0, readSize)
                    }
                }
                zip.closeEntry()
            }
        }

        return ExportResult(
            fileCount = files.size,
            totalBytes = files.sumOf { it.length() },
        )
    }

    private fun logRootDir(context: Context): File {
        return File(context.filesDir, LOG_ROOT_NAME)
    }

    private fun collectLogFiles(rootDir: File): List<File> {
        if (!rootDir.exists()) return emptyList()
        return rootDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.lastModified() }
            .toList()
    }

    /**
     * 导出前展示给用户看的日志概览，避免空目录时仍弹出系统保存面板。
     */
    data class LogSummary(
        val exists: Boolean,
        val fileCount: Int,
        val totalBytes: Long,
        val rootPath: String,
    )

    /**
     * 导出完成后的结果，用于设置页展示本次实际写出的日志数量和大小。
     */
    data class ExportResult(
        val fileCount: Int,
        val totalBytes: Long,
    )
}
