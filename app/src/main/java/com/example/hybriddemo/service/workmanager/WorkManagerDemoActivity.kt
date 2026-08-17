package com.example.hybriddemo.service.workmanager

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import com.example.hybriddemo.databinding.ActivityWorkmanagerDemoBinding
import java.util.UUID

/**
 * WorkManager 媒体上传演示页面。
 *
 * 功能：
 * 1. 点击按钮发起模拟图片/视频上传任务。
 * 2. 实时显示上传进度。
 * 3. 支持取消任务。
 * 4. 展示任务最终结果（成功 URL / 失败原因）。
 */
@com.alibaba.android.arouter.facade.annotation.Route(path = com.example.hybriddemo.router.DemoRouterPaths.WORK_MANAGER)
class WorkManagerDemoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WorkManagerDemo"
    }

    private lateinit var binding: ActivityWorkmanagerDemoBinding
    private var currentWorkId: UUID? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkmanagerDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUploadImage.setOnClickListener {
            startUpload("image", "/storage/emulated/0/DCIM/demo_photo.jpg", "demo_photo.jpg")
        }

        binding.btnUploadVideo.setOnClickListener {
            startUpload("video", "/storage/emulated/0/DCIM/demo_video.mp4", "demo_video.mp4")
        }

        binding.btnCancel.setOnClickListener {
            currentWorkId?.let {
                MediaUploadManager.cancelUpload(this, it)
                appendLog("已请求取消任务")
            } ?: appendLog("没有正在执行的任务")
        }
    }

    private fun startUpload(mediaType: String, filePath: String, fileName: String) {
        appendLog("发起 $mediaType 上传: $fileName")
        updateProgress(0)

        val workId = MediaUploadManager.enqueueUpload(
            context = this,
            filePath = filePath,
            fileName = fileName,
            mediaType = mediaType,
        )
        currentWorkId = workId

        // 观察任务状态
        MediaUploadManager.observeWork(this, workId).observe(this) { workInfo ->
            workInfo ?: return@observe

            when (workInfo.state) {
                WorkInfo.State.ENQUEUED -> {
                    appendLog("任务已入队，等待约束条件满足...")
                    binding.tvStatus.text = "状态：等待中"
                }

                WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress.getInt(MediaUploadWorker.PROGRESS, 0)
                    val msg = workInfo.progress.getString(MediaUploadWorker.PROGRESS_MSG) ?: ""
                    updateProgress(progress)
                    binding.tvStatus.text = "状态：上传中 $progress%"
                    if (msg.isNotBlank()) {
                        Log.d(TAG, msg)
                    }
                }

                WorkInfo.State.SUCCEEDED -> {
                    updateProgress(100)
                    val url = workInfo.outputData.getString(MediaUploadWorker.OUTPUT_URL) ?: ""
                    val resId = workInfo.outputData.getString(MediaUploadWorker.OUTPUT_RESOURCE_ID) ?: ""
                    binding.tvStatus.text = "状态：上传成功 ✓"
                    appendLog("上传成功！")
                    appendLog("URL: $url")
                    appendLog("ResourceId: $resId")
                    currentWorkId = null
                }

                WorkInfo.State.FAILED -> {
                    val msg = workInfo.outputData.getString(MediaUploadWorker.PROGRESS_MSG) ?: "未知错误"
                    binding.tvStatus.text = "状态：上传失败 ✗"
                    appendLog("上传失败: $msg")
                    currentWorkId = null
                }

                WorkInfo.State.CANCELLED -> {
                    binding.tvStatus.text = "状态：已取消"
                    appendLog("任务已取消")
                    currentWorkId = null
                }

                WorkInfo.State.BLOCKED -> {
                    binding.tvStatus.text = "状态：阻塞（等待前置任务）"
                }
            }
        }
    }

    private fun updateProgress(progress: Int) {
        binding.progressBar.progress = progress
        binding.tvProgress.text = "$progress%"
    }

    private fun appendLog(msg: String) {
        Log.d(TAG, msg)
        val current = binding.tvLog.text.toString()
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(System.currentTimeMillis())
        binding.tvLog.text = "$current\n[$timestamp] $msg"
        binding.scrollLog.post { binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN) }
    }
}
