package com.example.hybriddemo.service.workmanager

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.delay

/**
 * 模拟媒体文件（图片/视频）上传的 Worker。
 *
 * 演示要点：
 * 1. 继承 CoroutineWorker，在 doWork 中使用挂起函数。
 * 2. 通过 inputData 接收上传参数（文件路径、类型等）。
 * 3. 通过 setProgress 实时汇报进度。
 * 4. 返回 Result.success / failure / retry 控制结果。
 *
 * 对应生产代码：recruitment_android 的 UploadWorker。
 */
class MediaUploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "MediaUploadWorker"

        // Input keys
        const val INPUT_FILE_PATH = "input_file_path"
        const val INPUT_FILE_NAME = "input_file_name"
        const val INPUT_MEDIA_TYPE = "input_media_type" // "image" or "video"

        // Progress keys
        const val PROGRESS = "progress"
        const val PROGRESS_MSG = "progress_msg"

        // Output keys
        const val OUTPUT_URL = "output_url"
        const val OUTPUT_RESOURCE_ID = "output_resource_id"
    }

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(INPUT_FILE_PATH)
        val fileName = inputData.getString(INPUT_FILE_NAME) ?: "unknown"
        val mediaType = inputData.getString(INPUT_MEDIA_TYPE) ?: "image"

        Log.d(TAG, "开始上传: name=$fileName, type=$mediaType, path=$filePath")

        if (filePath.isNullOrBlank()) {
            Log.e(TAG, "文件路径为空，上传失败")
            return Result.failure(workDataOf(PROGRESS_MSG to "文件路径为空"))
        }

        return try {
            // 模拟上传过程（分10步，每步模拟网络耗时）
            for (i in 1..10) {
                // 检查是否被取消
                if (isStopped) {
                    Log.d(TAG, "任务被取消: $fileName")
                    return Result.failure(workDataOf(PROGRESS_MSG to "已取消"))
                }

                delay(800) // 模拟网络传输耗时
                val progress = i * 10
                setProgress(workDataOf(PROGRESS to progress, PROGRESS_MSG to "上传中 $progress%"))
                Log.d(TAG, "上传进度: $fileName -> $progress%")
            }

            // 模拟服务端返回
            val fakeUrl = "https://cdn.example.com/media/${System.currentTimeMillis()}/$fileName"
            val fakeResourceId = "res_${System.currentTimeMillis()}"

            Log.d(TAG, "上传成功: $fileName -> $fakeUrl")

            Result.success(
                workDataOf(
                    OUTPUT_URL to fakeUrl,
                    OUTPUT_RESOURCE_ID to fakeResourceId,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "上传异常: ${e.message}")
            if (runAttemptCount < 3) {
                Log.d(TAG, "将进行重试，当前第 $runAttemptCount 次尝试")
                Result.retry()
            } else {
                Result.failure(workDataOf(PROGRESS_MSG to "上传失败: ${e.message}"))
            }
        }
    }
}
