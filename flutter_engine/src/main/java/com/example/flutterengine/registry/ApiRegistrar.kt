package com.example.flutterengine.registry

/**
 * 桥接 API 注册器。
 *
 * 每一个 HostApi 实现类都实现这个接口，由 [FlutterApiRegistry] 统一调 [register]
 * 把自己挂到 BinaryMessenger 上。对应生产代码 `ApiRegistrar`。
 */
interface ApiRegistrar {
    /** 注册 channel handler。实现类持有 [com.example.flutterengine.entity.FlutterApiContext] 即可拿到 messenger。 */
    fun register()

    fun getName(): String = this.javaClass.simpleName
}
