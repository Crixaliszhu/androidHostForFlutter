package com.example.hybriddemo.customview

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView

class JobSearchCollapseDemoActivity : AppCompatActivity() {

    private lateinit var headerView: CollapsibleSearchHeaderView
    private lateinit var scrollView: NestedScrollView

    private val collapseDistance by lazy { dp(128) }
    private val headerExpandedHeight by lazy { dp(232) }
    private val headerCollapsedOffset by lazy { dp(86) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createContentView())
        headerView.onProgressChanged = { progress ->
            updateHeaderHeight(progress)
        }
        bindScroll()
    }

    private fun bindScroll() {
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val progress = (scrollY.toFloat() / collapseDistance).coerceIn(0f, 1f)
            headerView.setProgress(progress)
        }
        scrollView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP ||
                event.action == MotionEvent.ACTION_CANCEL
            ) {
                settleHeader()
            }
            false
        }
    }

    private fun settleHeader() {
        val shouldCollapse = headerView.progress >= 0.5f
        headerView.settleTo(if (shouldCollapse) 1f else 0f)
        scrollView.post {
            scrollView.smoothScrollTo(0, if (shouldCollapse) collapseDistance else 0)
        }
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF5F6FA.toInt())
        }

        headerView = CollapsibleSearchHeaderView(this)
        root.addView(
            headerView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                headerExpandedHeight,
            ),
        )

        scrollView = NestedScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scrollView.addView(createListContent())
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        return root
    }

    private fun createListContent(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(24))
            addView(createFilterBar())
            addView(createGroupCard())
            repeat(18) { index ->
                addView(createJobCard(index))
            }
        }
    }

    private fun updateHeaderHeight(progress: Float) {
        val newHeight = headerExpandedHeight - (headerCollapsedOffset * progress).toInt()
        val params = headerView.layoutParams
        if (params.height == newHeight) return
        params.height = newHeight
        headerView.layoutParams = params
    }

    private fun createFilterBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(dp(6), 0, dp(6), 0)
        }
        listOf("综合", "最新", "附近", "职位⌃", "成都⌃", "筛选⌃").forEachIndexed { index, text ->
            row.addView(TextView(this).apply {
                this.text = text
                textSize = 18f
                setTextColor(if (index == 4) 0xFF0092FF.toInt() else 0xFF333333.toInt())
                gravity = android.view.Gravity.CENTER
            }, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        return row
    }

    private fun createGroupCard(): View {
        return TextView(this).apply {
            text = "本地老板用人急招群\n要4个师傅干点包，有空的联系我          加入群聊"
            textSize = 17f
            setTextColor(0xFF333333.toInt())
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = roundedBg(0xFFFFFFFF.toInt(), dp(16))
        }.withMargins(top = dp(12))
    }

    private fun createJobCard(index: Int): View {
        val titles = listOf(
            "成都锦江区有人做小区不要人。",
            "四川成都招聘瓦工2名，负责砌筑管网检查井。",
            "本地急招弱电工，包活稳定，今天能到优先。",
        )
        return TextView(this).apply {
            text = "${titles[index % titles.size]}\n\n弱电工   小工\n\n昨日活跃                                      成都  ×"
            textSize = 22f
            setLineSpacing(dp(4).toFloat(), 1f)
            setTextColor(0xFF252525.toInt())
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = roundedBg(0xFFFFFFFF.toInt(), dp(16))
        }.withMargins(top = dp(12))
    }

    private fun View.withMargins(top: Int = 0): View {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = top
        }
        return this
    }

    private fun roundedBg(color: Int, radius: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
