package com.example.hybriddemo.settings

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.hybriddemo.databinding.ActivitySettingsBinding
import com.example.qualitymonitor.export.QualityMonitorLogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Demo 设置页。
 *
 * 当前只承载自研质量监控日志导出能力；正式包不能 run-as 读取私有目录，
 * 所以这里通过系统文件选择器把日志 zip 写到用户指定位置。
 */
@com.alibaba.android.arouter.facade.annotation.Route(path = com.example.hybriddemo.router.DemoRouterPaths.SETTINGS)
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private val createLogZip = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            exportQualityMonitorLogs(uri)
        } else {
            showStatus("已取消导出。")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnExportQualityLogs.setOnClickListener { requestExportQualityMonitorLogs() }
        refreshSummary()
    }

    private fun requestExportQualityMonitorLogs() {
        val summary = QualityMonitorLogExporter.summary(this)
        if (summary.fileCount <= 0) {
            showStatus("暂无 quality_monitor 日志可导出。\n目录：${summary.rootPath}")
            return
        }
        createLogZip.launch(QualityMonitorLogExporter.defaultFileName())
    }

    private fun exportQualityMonitorLogs(uri: Uri) {
        showStatus("正在导出 quality_monitor 日志...")
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        QualityMonitorLogExporter.exportToZip(this@SettingsActivity, output)
                    } ?: error("无法打开目标文件。")
                }
            }

            result.onSuccess { exportResult ->
                refreshSummary()
                val message = "导出完成：${exportResult.fileCount} 个文件，${formatBytes(exportResult.totalBytes)}"
                Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
                showStatus(message)
            }.onFailure { throwable ->
                showStatus("导出失败：${throwable.message.orEmpty()}")
            }
        }
    }

    private fun refreshSummary() {
        val summary = QualityMonitorLogExporter.summary(this)
        binding.tvQualityLogSummary.text = buildString {
            append("日志目录：").append(summary.rootPath).append('\n')
            append("文件数量：").append(summary.fileCount).append('\n')
            append("占用大小：").append(formatBytes(summary.totalBytes))
        }
    }

    private fun showStatus(message: String) {
        binding.tvSettingsStatus.text = message
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        return String.format("%.1f MB", kb / 1024.0)
    }
}
