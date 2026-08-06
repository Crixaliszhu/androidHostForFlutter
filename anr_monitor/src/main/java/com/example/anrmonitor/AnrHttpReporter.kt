package com.example.anrmonitor

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 独立模块内置的最小 HTTP 上报器。
 */
class AnrHttpReporter(private val config: SelfAnrConfig) {
    fun upload(file: File): Boolean {
        if (config.reportUrl.isBlank()) return false
        val connection = (URL(config.reportUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
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
}
