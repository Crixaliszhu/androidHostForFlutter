package com.example.camera

import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField

/**
 * DataBinding 直接观察的页面状态。
 *
 * 这里使用 ObservableField，而不是把 View 引用塞进状态对象：字段变化时 DataBinding 只刷新
 * 依赖它的 XML 属性，例如水印文字、按钮区域可见性和加载状态。
 */
class WatermarkCameraUiModel {
    val timeText = ObservableField("")
    val locationText = ObservableField("我在这里")
    val statusText = ObservableField("")
    val statusVisible = ObservableBoolean(false)
    val reviewing = ObservableBoolean(false)
    val busy = ObservableBoolean(false)

    fun showStatus(message: String) {
        statusText.set(message)
        statusVisible.set(message.isNotBlank())
    }
}

/** XML 中的点击事件统一回调到 Activity，避免布局直接持有 Camera2、定位等系统对象。 */
interface WatermarkCameraActionHandler {
    fun onBack()
    fun onLocate()
    fun onCapture()
    fun onDiscard()
    fun onSave()
}
