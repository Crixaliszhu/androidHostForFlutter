package com.example.qualitymonitor.anr.entity

import org.json.JSONArray
import org.json.JSONObject

/**
 * 看门狗和系统退出记录共用的可序列化 ANR 事件。
 */
data class AnrEvent(
    val id: String,
    val type: String,
    val timestampMillis: Long,
    val processName: String,
    val foreground: Boolean,
    val currentActivity: String?,
    val lastBreadcrumbs: List<String>,
    val mainThreadStack: String,
    val allThreadStacks: String?,
    val systemTrace: String?,
    val extra: Map<String, String>,
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("type", type)
            .put("timestampMillis", timestampMillis)
            .put("processName", processName)
            .put("foreground", foreground)
            .put("currentActivity", currentActivity)
            .put("lastBreadcrumbs", JSONArray(lastBreadcrumbs))
            .put("mainThreadStack", mainThreadStack)
            .put("allThreadStacks", allThreadStacks)
            .put("systemTrace", systemTrace)
            .put("extra", JSONObject(extra))
    }
}
