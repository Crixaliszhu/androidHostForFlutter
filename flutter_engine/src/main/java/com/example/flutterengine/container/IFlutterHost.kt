package com.example.flutterengine.container

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner

/**
 * Flutter 容器宿主接口。
 *
 * 不论容器是 Activity 还是 Fragment，桥接层只关心这套 API：能拿到 activity、生命周期、参数。
 * 对应生产代码 `IFlutterHost`。
 */
interface IFlutterHost {
    fun providerActivity(): FragmentActivity?
    fun providerLifecycleOwner(): LifecycleOwner?
    fun providerArgs(): Bundle?

    /** 关闭当前容器，可选携带返回数据给上一级原生页面（setResult）。 */
    fun finish(resultCode: Int? = null, data: Bundle? = null)
}
