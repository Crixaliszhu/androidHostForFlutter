package com.example.flutterengine.entity

import io.flutter.plugin.common.BinaryMessenger

/**
 * 注册桥接 API 时下发给每个 ApiRegistrar 的上下文。
 *
 * 对应生产代码 `FlutterApiContext`。
 *
 * @property engineId 当前引擎 ID。多引擎场景下可以根据 ID 做不同处理。
 * @property messenger Flutter 引擎的 BinaryMessenger，channel 注册必备。
 */
data class FlutterApiContext(
    val engineId: String,
    val messenger: BinaryMessenger,
)
