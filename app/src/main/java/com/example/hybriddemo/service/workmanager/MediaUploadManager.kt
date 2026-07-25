package com.example.hybriddemo.service.workmanager

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 媒体上传管理器，封装 WorkManager 调度逻辑。
 *
 * 演示要点：
 * 1. 网络约束（仅在网络可用时执行）。
 * 2. UniqueWork 防止同一文件重复上传。
 * 3. 通过 LiveData 观察任务状态和进度。
 * 4. 支持取消任务。
 * 5. 退避策略（指数退避重试）。
 *
 * 对应生产代码：recruitment_android 的 TaskImpl + FlutterEngineManager 中的上传调度。
 */
object MediaUploadManager {

    private const val UNIQUE_WORK_PREFIX = "media_upload_"

    /**
     * 发起一个媒体上传任务。
     *
     * @param context 上下文
     * @param filePath 本地文件路径
     * @param fileName 文件名
     * @param mediaType 媒体类型：image / video
     * @return 任务 ID，可用于观察进度和取消
     */
    fun enqueueUpload(
        context: Context,
        filePath: String,
        fileName: String,
        mediaType: String = "image",
    ): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // 仅在有网络时上传
            .setRequiresBatteryNotLow(true)               // 低电量时暂停
            .build()

        val inputData = workDataOf(
            MediaUploadWorker.INPUT_FILE_PATH to filePath,
            MediaUploadWorker.INPUT_FILE_NAME to fileName,
            MediaUploadWorker.INPUT_MEDIA_TYPE to mediaType,
        )

        val request = OneTimeWorkRequestBuilder<MediaUploadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(mediaType) // 按类型打 tag，方便批量取消
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .build()

        // UniqueWork：同一文件不会重复排队
        val uniqueName = "$UNIQUE_WORK_PREFIX$filePath"
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName,
            ExistingWorkPolicy.KEEP, // 已有同名任务则保留，不重复提交
            request,
        )

        return request.id
    }

    /**
     * 观察某个上传任务的状态。
     */
    fun observeWork(context: Context, workId: UUID): LiveData<WorkInfo> {
        return WorkManager.getInstance(context).getWorkInfoByIdLiveData(workId)
    }

    /**
     * 取消某个上传任务。
     */
    fun cancelUpload(context: Context, workId: UUID) {
        WorkManager.getInstance(context).cancelWorkById(workId)
    }

    /**
     * 取消所有指定类型的上传任务。
     */
    fun cancelAllByType(context: Context, mediaType: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(mediaType)
    }

    /**
     * 取消所有上传任务。
     */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }
}
