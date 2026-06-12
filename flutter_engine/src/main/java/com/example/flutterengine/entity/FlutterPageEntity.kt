package com.example.flutterengine.entity

import com.example.flutterengine.container.IFlutterPage

/**
 * 引擎中的一个 Flutter 页面记录。
 *
 * 多容器复用同一个引擎时（比如主引擎承载多个 Tab），需要根据 instanceId 区分。
 */
data class FlutterPageEntity(
    val instanceId: String,
    val route: String,
    val page: IFlutterPage,
)
