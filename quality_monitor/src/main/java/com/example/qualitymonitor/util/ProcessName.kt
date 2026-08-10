package com.example.qualitymonitor.util

import android.app.Application
import android.os.Build

/**
 * 当前进程名工具。
 *
 * 质量事件需要带上进程名，服务端才能区分主进程、Flutter 相关进程或后续扩展的独立服务进程。
 */
object ProcessName {
    fun current(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            // 第一阶段只支持 Android P+ 的官方 API；低版本后续可补充 ActivityManager 兜底实现。
            "unknown"
        }
    }
}
