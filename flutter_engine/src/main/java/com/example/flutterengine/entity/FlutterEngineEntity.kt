package com.example.flutterengine.entity

import com.example.flutterengine.registry.ApiRegistrar
import io.flutter.embedding.engine.FlutterEngine
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 引擎缓存条目。
 *
 * @property engine FlutterEngine 实例。
 * @property handlerApis 当前引擎注册的所有 API（基础 + 业务），方便后续按类查找。
 * @property pages 这个引擎承载的活跃页面，用于决定是否销毁引擎。
 * @property isAlive 引擎是否仍可用。一旦销毁置 false。
 */
class FlutterEngineEntity(
    val engine: FlutterEngine,
    val handlerApis: List<ApiRegistrar>,
    val pages: MutableList<FlutterPageEntity> = CopyOnWriteArrayList(),
    @Volatile var isAlive: Boolean = true,
)
