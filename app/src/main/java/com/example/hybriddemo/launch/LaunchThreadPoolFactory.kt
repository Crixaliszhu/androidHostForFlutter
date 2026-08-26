package com.example.hybriddemo.launch

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object LaunchThreadPoolFactory {

    private val launchPool by lazy {
        ThreadPoolExecutor(
            1,
            1,
            100,
            TimeUnit.MILLISECONDS,
            LinkedBlockingDeque(4),
            object : ThreadFactory {
                private val atomicNumber = AtomicInteger(1)
                override fun newThread(r: Runnable?): Thread {
                    return Thread(r, "LaunchThreadPool ${atomicNumber.getAndIncrement()}").apply {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                        priority = Thread.MAX_PRIORITY
                    }
                }
            }
        )
    }

    fun submit(runnable: Runnable): Future<*> {
        return launchPool.submit(runnable)
    }

    fun shutDown(){
        launchPool.shutdown()
    }
}