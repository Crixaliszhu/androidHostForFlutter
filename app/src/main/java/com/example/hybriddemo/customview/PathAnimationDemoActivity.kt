package com.example.hybriddemo.customview

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

@com.alibaba.android.arouter.facade.annotation.Route(path = com.example.hybriddemo.router.DemoRouterPaths.PATH_ANIMATION)
class PathAnimationDemoActivity : AppCompatActivity() {

    // 保存自定义 View 引用，底部按钮直接调用它暴露的控制方法。
    private lateinit var pathAnimationView: PathAnimationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
    }

    private fun createContentView(): LinearLayout {
        pathAnimationView = PathAnimationView(this)

        // 这里用代码创建布局，便于 Demo 保持在一个 Activity 内。
        // 真实业务中也可以把 PathAnimationView 写到 XML 布局里。
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF8FAFC.toInt())
            addView(
                pathAnimationView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,

                    // weight=1 让动画区域占满按钮栏之外的剩余空间。
                    1f,
                ),
            )
            addView(createButtonBar())
        }
    }

    private fun createButtonBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(20))

            // 三个按钮分别演示动画生命周期控制：
            // start() 继续播放，pause() 取消动画但保留进度，reset() 回到起点。
            addView(actionButton("开始") { pathAnimationView.start() })
            addView(actionButton("暂停") { pathAnimationView.pause() })
            addView(actionButton("重置") { pathAnimationView.reset() })
        }
    }

    private fun actionButton(text: String, action: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            this.text = text
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
