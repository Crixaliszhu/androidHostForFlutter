package com.example.qualitymonitor.core

import com.example.qualitymonitor.QualityMonitorConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 质量事件上传器。
 *
 * 第一阶段只做简单 HTTP POST，把本地文件原样传给服务端；后续可以在这里扩展批量压缩、
 * 重试退避、签名鉴权和网络类型限制。
 */
class QualityUploader(private val config: QualityMonitorConfig) {
    fun upload(file: File): Boolean {
        // 上报地址为空时表示只采集不上传，不能删除本地文件。
        if (config.uploadUrl.isBlank()) return false
        val connection = (URL(config.uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", contentType(file))
            setRequestProperty("X-Quality-Event-File", file.name)
        }

        return try {
            connection.outputStream.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }

    private fun contentType(file: File): String {
        return if (file.extension == "json") {
            "application/json; charset=utf-8"
        } else {
            "text/plain; charset=utf-8"
        }
    }
}
