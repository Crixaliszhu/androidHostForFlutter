package com.example.qualitymonitor.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * 自研质量监控的统一事件模型。
 *
 * Java 崩溃、Native 崩溃、ANR、启动耗时和页面耗时都转成这个结构，
 * 这样本地队列和上报服务只需要处理一种事件信封。
 */
data class QualityEvent(
    val eventId: String,
    val eventType: String,
    val timestampMillis: Long,
    val payload: JSONObject,
) {
    /** 合并公共字段和面包屑，生成最终写入磁盘或上传的 JSON。 */
    fun toJson(common: JSONObject, breadcrumbs: List<String>): JSONObject {
        return JSONObject()
            .put("eventId", eventId)
            .put("eventType", eventType)
            .put("timestampMillis", timestampMillis)
            .put("common", common)
            .put("breadcrumbs", JSONArray(breadcrumbs))
            .put("payload", payload)
    }
}
