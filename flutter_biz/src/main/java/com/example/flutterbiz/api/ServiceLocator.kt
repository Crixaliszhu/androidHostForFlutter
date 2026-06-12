package com.example.flutterbiz.api

import kotlin.reflect.KClass

/**
 * 极简服务注册表。
 *
 * 生产代码用 ARouter `@Route` 注册 Provider，业务方通过
 * `RouterApi.getByClass(IFlutterRouterService::class.java)` 拿。
 *
 * 这里用 `ServiceLocator.register(IFlutterRouterService::class) { FlutterRouterServiceImpl() }`
 * 替代，避免 demo 引入 ARouter。
 */
object ServiceLocator {
    private val registry = mutableMapOf<KClass<*>, () -> Any>()

    fun <T : Any> register(clazz: KClass<T>, factory: () -> T) {
        registry[clazz] = factory
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: KClass<T>): T? = registry[clazz]?.invoke() as? T
}
