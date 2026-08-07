package com.example.hybriddemo.anrdemo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class AnrDemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // BroadcastReceiver 的 onReceive 默认运行在主线程，长时间阻塞会触发广播超时类 ANR。
        SystemClock.sleep(BLOCK_DURATION_MS)
    }

    companion object {
        private const val BLOCK_DURATION_MS = 15_000L
    }
}
