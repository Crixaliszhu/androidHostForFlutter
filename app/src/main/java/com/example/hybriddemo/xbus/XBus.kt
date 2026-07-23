package com.example.hybriddemo.xbus

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 轻量级事件总线角色定义：
 * 1. XBus 入口类
 * 2. 事件对象
 * 3. 订阅者对象
 * 4. 总线访问器
 *
 */

/**
 * 轻量级事件总线
 * XBus作为入口类：获取总线访问器，维护全局的订阅者列表，注册订阅，注销订阅
 */
object XBus {
    // 订阅者们
    private val subscriptions = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<Subscription<*>>>()

    // 获取总线访问器
    fun get(owner: LifecycleOwner?): BusAccessor = BusAccessor(owner)

    // 注册订阅
    internal fun <T : Any> register(eventType: Class<T>, subscription: Subscription<T>) {
        val list = subscriptions.getOrPut(eventType) { CopyOnWriteArrayList() }
        list.add(subscription)
    }

    // 注销订阅
    internal fun <T> unRegister(eventType: Class<T>) {
        subscriptions.remove(eventType)
    }

    // 分发事件
    internal fun <T> dispatcher(eventType: Class<T>, event: T) {
        val list = subscriptions[eventType] ?: return
        for (sub in list) {
            (sub as Subscription<T>).callback(event)
        }
    }

}

/**
 * 总线访问器：获取事件流
 */
class BusAccessor(val owner: LifecycleOwner?) {
    //获取事件流对象
    fun <T : Any> of(eventType: Class<T>): EventStream<T> = EventStream(eventType, this)
}

/**
 * 事件流对象：负责定义对事件的操作
 * 事件的类型
 * 操作：订阅，注销，发送
 */
class EventStream<T : Any>(private val eventType: Class<T>, private val accessor: BusAccessor) {

    // 监听流程：1.创建订阅者，2.注册订阅，3.注册生命周期回调
    fun listen(callback: (T) -> Unit) {
        val subscription = Subscription(accessor.owner, callback)
        XBus.register(eventType, subscription)
        accessor.owner?.lifecycle?.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                XBus.unRegister(eventType)
                owner.lifecycle.removeObserver(this)
            }
        })
    }

    fun post(event: T) {
        XBus.dispatcher(eventType, event)
    }
}

/**
 * 订阅者：订阅者必须能感知生命周期状态，订阅者要能回到事件
 */
data class Subscription<T>(val owner: LifecycleOwner?, val callback: (T) -> Unit)