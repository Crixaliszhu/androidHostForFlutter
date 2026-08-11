package com.example.qualitymonitor.memory.leak

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

/**
 * 被观察对象的弱引用记录。
 *
 * 这里只保存对象类名、观察原因和时间，不保存被观察对象强引用，否则监控器自身会制造泄露。
 */
internal class WatchedReference(
    watchedObject: Any,
    referenceQueue: ReferenceQueue<Any>,
    val key: String,
    val className: String,
    val description: String,
    val watchTimestampMillis: Long,
) {
    val weakReference: WeakReference<Any> = WeakReference(watchedObject, referenceQueue)
    var suspectReported: Boolean = false
    var confirmedReported: Boolean = false
}
