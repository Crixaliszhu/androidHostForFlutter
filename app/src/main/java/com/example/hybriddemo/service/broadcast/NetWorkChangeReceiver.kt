package com.example.hybriddemo.service.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager

sealed interface NetWorkType {
    data object MOBILE : NetWorkType

    data object WIFI : NetWorkType

    data object NO : NetWorkType
}

object NetWorkUtil {

    fun getNetWorkState(context: Context): NetWorkType {
        val cm: ConnectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetworkInfo
        if (activeNet != null) {
            if (activeNet.type == ConnectivityManager.TYPE_WIFI) return NetWorkType.WIFI
            if (activeNet.type == ConnectivityManager.TYPE_MOBILE) return NetWorkType.MOBILE
        }
        return NetWorkType.NO
    }

    fun isNetWorkEnable(context: Context): Boolean {
        return getNetWorkState(context) != NetWorkType.NO
    }

}

/**
 * 网络状态监听广播
 * 网络状态枚举；
 * 接受网络状态改变的回调；
 *
 */
class NetWorkChangeReceiver : BroadcastReceiver() {
    private var netWorkChangeListener: ((NetWorkType) -> Unit?)? = null
    private var _state: NetWorkType? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        intent ?: return
        if (ConnectivityManager.CONNECTIVITY_ACTION == intent.action) {
            val state = NetWorkUtil.getNetWorkState(context)
            if (_state != state) {
                netWorkChangeListener?.invoke(state)
            }
        }
    }

    fun setNetWorkChangeListener(listener: (NetWorkType) -> Unit) {
        this.netWorkChangeListener = listener
    }
}