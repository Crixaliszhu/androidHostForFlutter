package com.example.flutterengine.config

/**
 * Flutter 容器与引擎相关常量。
 *
 * 对应生产代码 `FlutterConstance` + `FlutterConstant` 两个文件，这里合并到一处方便查阅。
 */
object FlutterConstants {

    /** 主引擎 ID。常驻不销毁，承载首页类的 Flutter 页面。 */
    const val ENGINE_MAIN = "demo_engine_main"

    /** Flutter 默认根路由，预热引擎时指定。 */
    const val DEFAULT_ROOT_ROUTE = "flutter/root"

    object ArgsKey {
        /** Intent 中 Flutter 路由 key。值不要随意改，与 Flutter 端解析逻辑配套。 */
        const val ROUTE = "route"

        /** 引擎 ID。指定后宿主会复用同一个引擎实例。 */
        const val CACHED_ENGINE_ID = "cached_engine_id"

        /** 页面实例 ID。多容器复用同一个引擎时区分页面用。 */
        const val INSTANCE_ID = "instance_id"
    }
}
