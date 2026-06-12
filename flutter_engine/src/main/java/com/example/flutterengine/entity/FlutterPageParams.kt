package com.example.flutterengine.entity

/**
 * 启动一个 Flutter 容器需要的参数。
 *
 * 对应生产代码 `FlutterPageParamsEntity`。
 */
data class FlutterPageParams(
    /** 完整 Flutter 路由，比如 `flutter/detail?id=123`。 */
    val route: String,
    /** 引擎 ID。null 表示使用独立引擎，不复用主引擎。 */
    val engineId: String? = null,
    /** 页面实例 ID。null 时由代理自动生成。 */
    val instanceId: String? = null,
)
