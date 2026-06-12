package com.example.flutterbiz.bridge

import com.example.flutterengine.manage.FlutterEngineManager

/**
 * Native → Flutter 事件发送封装。
 *
 * 对应生产代码 `BaseFlutterApiCaller`，是个轻量的语义层，
 * 调用方不直接接触 BinaryMessenger。
 */
object EventApiCaller {
    /** 推一个简单事件。Flutter 端 `EventChannelBridge` 会收到。 */
    fun sendTick() {
        FlutterEngineManager.sendEventToAllEngines(
            eventName = "tick",
            arguments = mapOf("ts" to System.currentTimeMillis()),
        )
    }
}
