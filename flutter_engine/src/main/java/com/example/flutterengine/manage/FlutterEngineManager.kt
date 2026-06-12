package com.example.flutterengine.manage

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import com.example.flutterengine.config.FlutterConstants
import com.example.flutterengine.entity.FlutterApiContext
import com.example.flutterengine.entity.FlutterEngineEntity
import com.example.flutterengine.entity.FlutterPageEntity
import com.example.flutterengine.registry.ApiRegistrar
import com.example.flutterengine.registry.FlutterApiRegistry
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import kotlin.reflect.KClass

/**
 * 引擎注册中心 + 生命周期管理器。
 *
 * 关键能力：
 * 1. 引擎缓存：同一个 engineId 复用同一个 FlutterEngine。
 * 2. "空引擎保留" 名单：主引擎承载首页类页面，所有页面退出后也不释放。
 * 3. 跨引擎广播：通过保存所有引擎的 BinaryMessenger，可以一次把消息推给所有 Flutter 端。
 *
 * 对应生产代码 `FlutterEngineManager`。
 */
object FlutterEngineManager {

    private const val TAG = "FlutterEngineManager"

    private lateinit var provider: FlutterEngineProvider
    private val lock = Any()

    private val engineCache = mutableMapOf<String, FlutterEngineEntity>()
    private val keepEmptyEngineIds = mutableSetOf<String>()

    /** engineId → 当前承载的容器 Activity（弱引用），HostApi 实现需要拿 Activity 时通过 [getHostActivity] 取。 */
    private val hostActivities = mutableMapOf<String, java.lang.ref.WeakReference<android.app.Activity>>()

    /** 业务方注入的 ApiRegistrar 工厂；引擎创建时会调一次拿到这个引擎要注册的 API 列表。 */
    private var bizHandlerApi: ((FlutterApiContext) -> List<ApiRegistrar>)? = null

    fun init(context: Context) {
        provider = FlutterEngineProvider(context)
    }

    fun setHandlerApiRegister(api: (FlutterApiContext) -> List<ApiRegistrar>) {
        this.bizHandlerApi = api
    }

    fun addKeepEmptyEngineIds(ids: Set<String>) = keepEmptyEngineIds.addAll(ids)

    // ---------------- 容器活跃 Activity 跟踪 ----------------

    /** 容器 Activity 在 onCreate / onDestroy 中调用，告诉桥接层"现在我是活跃宿主"。 */
    fun bindHostActivity(engineId: String, activity: android.app.Activity?) {
        synchronized(lock) {
            if (activity == null) {
                hostActivities.remove(engineId)
            } else {
                hostActivities[engineId] = java.lang.ref.WeakReference(activity)
            }
        }
    }

    /** HostApi 实现里通过这个拿当前承载页面的 Activity。 */
    fun getHostActivity(engineId: String): android.app.Activity? = synchronized(lock) {
        hostActivities[engineId]?.get()
    }

    // ---------------- 引擎获取 ----------------

    /**
     * 拿引擎（不存在时创建）。
     *
     * 注意：调用方应在主线程使用，引擎创建本身需要在主线程。
     */
    fun fetchFlutterEngine(engineId: String, initRoute: String?): FlutterEngine? {
        synchronized(lock) {
            getAliveEntity(engineId)?.let { return it.engine }

            val engine = provider.create(initRoute) ?: return null
            val apis = onEngineCreated(engine, engineId)

            // 监听引擎销毁事件，主动清掉缓存。
            engine.addEngineLifecycleListener(object : FlutterEngine.EngineLifecycleListener {
                override fun onPreEngineRestart() {}
                override fun onEngineWillDestroy() {
                    onEngineWillDestroy(engineId)
                }
            })

            engineCache[engineId] = FlutterEngineEntity(engine = engine, handlerApis = apis)
            return engine
        }
    }

    fun isAliveEngine(engineId: String): Boolean = getAliveEntity(engineId) != null

    fun getFlutterEngine(engineId: String): FlutterEngine? = getAliveEntity(engineId)?.engine

    fun getBinaryMessenger(engineId: String): BinaryMessenger? =
        getFlutterEngine(engineId)?.dartExecutor?.binaryMessenger

    /** 给跨引擎广播用：拿到所有当前活跃引擎的 BinaryMessenger。 */
    fun getAllBinaryMessenger(): List<BinaryMessenger> = synchronized(lock) {
        engineCache.values.filter { it.isAlive }.map { it.engine.dartExecutor.binaryMessenger }
    }

    /**
     * 找指定引擎里某一类 ApiRegistrar 的实例。
     *
     * 业务实现需要回调时（比如 `RouterApi` 想关闭页面），在这里拿到自己持有的 context.activity。
     */
    @Suppress("UNCHECKED_CAST")
    fun <Api : Any> getHandlerApi(engineId: String, clazz: KClass<Api>): Api? {
        return getAliveEntity(engineId)?.handlerApis?.firstOrNull {
            clazz.java.isAssignableFrom(it::class.java)
        } as? Api
    }

    // ---------------- 主引擎预热 ----------------

    /** 与生产代码对齐的入口：在 idle 时机拉起主引擎。 */
    fun initMainEngine() {
        runCatching {
            fetchFlutterEngine(FlutterConstants.ENGINE_MAIN, null)
        }.onFailure { Log.e(TAG, "initMainEngine failed: ${it.message}") }
    }

    // ---------------- 跨引擎事件 ----------------

    /**
     * 向所有 Flutter 引擎广播事件（Native → Flutter 单向）。
     *
     * Flutter 端在 `event_channel.dart` 里监听 `onReceiveEvent`。
     * 真实项目用 Pigeon 的 `EventFlutterAPI.onReceiveEvent`，原理一致。
     */
    fun sendEventToAllEngines(eventName: String, arguments: Map<String, Any?>? = null) {
        getAllBinaryMessenger().forEach { msg ->
            val ch = MethodChannel(msg, "com.example.hybriddemo/event")
            ch.invokeMethod("onReceiveEvent", mapOf(
                "name" to eventName,
                "arguments" to (arguments ?: emptyMap<String, Any?>()),
            ))
        }
    }

    // ---------------- 页面登记 ----------------

    @MainThread
    fun addPage(engineId: String, pageEntity: FlutterPageEntity) {
        synchronized(lock) {
            engineCache[engineId]?.pages?.add(pageEntity)
        }
    }

    /**
     * 移除一个页面，必要时销毁引擎。
     *
     * 销毁规则：当前引擎页面清空 && 不在 keepEmptyEngineIds 里 → destroy。
     */
    @MainThread
    fun removePage(engineId: String, instanceId: String) {
        val entity = synchronized(lock) { engineCache[engineId] } ?: return
        val shouldDestroy = synchronized(lock) {
            entity.pages.removeAll { it.instanceId == instanceId }
            entity.pages.isEmpty() && !keepEmptyEngineIds.contains(engineId)
        }
        if (shouldDestroy && entity.isAlive) {
            entity.isAlive = false
            runCatching { entity.engine.destroy() }
                .onFailure { Log.e(TAG, "destroy engine $engineId failed: ${it.message}") }
        }
    }

    // ---------------- 内部 ----------------

    private fun onEngineCreated(engine: FlutterEngine, engineId: String): List<ApiRegistrar> {
        val ctx = FlutterApiContext(engineId, engine.dartExecutor.binaryMessenger)
        val apis = bizHandlerApi?.invoke(ctx).orEmpty()
        FlutterApiRegistry.performRegister(apis)
        return apis
    }

    private fun onEngineWillDestroy(engineId: String) {
        synchronized(lock) {
            engineCache.remove(engineId)?.apply {
                isAlive = false
                pages.clear()
            }
        }
    }

    private fun getAliveEntity(engineId: String): FlutterEngineEntity? = synchronized(lock) {
        engineCache[engineId]?.takeIf { it.isAlive }
    }
}
