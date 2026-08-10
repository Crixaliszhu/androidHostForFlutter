package com.example.qualitymonitor.performance.page

import android.os.SystemClock
import com.example.qualitymonitor.core.QualityEventStore
import java.util.UUID
import org.json.JSONObject

/**
 * 页面性能手动埋点入口。
 *
 * 自动 Activity 首帧只能覆盖原生容器；业务页面、Flutter 页面或数据 ready/可交互节点
 * 可以用这个对象手动标记，形成更贴近用户体验的页面耗时。
 */
object PagePerf {
    private var store: QualityEventStore? = null
    private val sessions = mutableMapOf<String, PageSession>()

    fun bindStore(eventStore: QualityEventStore) {
        store = eventStore
    }

    fun start(pageName: String, route: String = pageName): String {
        val id = UUID.randomUUID().toString()
        sessions[id] = PageSession(pageName, route, SystemClock.uptimeMillis())
        return id
    }

    fun mark(sessionId: String, name: String) {
        sessions[sessionId]?.marks?.put(name, SystemClock.uptimeMillis())
    }

    fun end(sessionId: String, endName: String = "interactive") {
        val session = sessions.remove(sessionId) ?: return
        val endUptime = SystemClock.uptimeMillis()
        val payload = JSONObject()
            .put("pageName", session.pageName)
            .put("route", session.route)
            .put("durationMs", endUptime - session.startUptime)
            .put("endName", endName)
        session.marks.forEach { (name, uptime) ->
            payload.put("${name}DurationMs", uptime - session.startUptime)
        }
        store?.enqueue("page_perf", payload)
    }

    /**
     * 单次页面性能会话。
     *
     * marks 按插入顺序保留，便于服务端或本地日志按业务节点顺序展示耗时。
     */
    private data class PageSession(
        val pageName: String,
        val route: String,
        val startUptime: Long,
        val marks: MutableMap<String, Long> = linkedMapOf(),
    )
}
