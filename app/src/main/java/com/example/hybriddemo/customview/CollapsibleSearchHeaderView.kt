package com.example.hybriddemo.customview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding

/**
 * 推荐职位顶部搜索区。
 *
 * [progress] 取值 0..1：
 * - 0：搜索框完全展开
 * - 1：搜索框向自身中心缩小并透明，右侧独立搜索图标显示
 */
class CollapsibleSearchHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val topSearchGroup = FrameLayout(context)
    private val searchContainer = LinearLayout(context)
    private val titleRow = FrameLayout(context)
    private val compactSearchIcon = TextView(context)
    private var animator: ValueAnimator? = null

    var onProgressChanged: ((Float) -> Unit)? = null

    var progress: Float = 0f
        private set

    init {
        setWillNotDraw(false)
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(184, 240, 255), Color.WHITE),
        )

        addTopSearchGroup()
        addTitleRow()
    }

    fun setProgress(value: Float) {
        animator?.cancel()
        applyProgress(value.coerceIn(0f, 1f))
    }

    fun settleTo(target: Float) {
        val end = target.coerceIn(0f, 1f)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, end).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        topSearchGroup.pivotX = topSearchGroup.width / 2f
        topSearchGroup.pivotY = topSearchGroup.height / 2f
    }

    private fun applyProgress(value: Float) {
        progress = value
        val scale = 1f - 0.72f * value
        topSearchGroup.scaleX = scale
        topSearchGroup.scaleY = scale
        topSearchGroup.alpha = 1f - value
        topSearchGroup.translationY = -dp(18) * value

        titleRow.translationY = -dp(84) * value

        compactSearchIcon.alpha = value
        compactSearchIcon.scaleX = 0.8f + 0.2f * value
        compactSearchIcon.scaleY = 0.8f + 0.2f * value
        onProgressChanged?.invoke(value)
    }

    private fun addTopSearchGroup() {
        addView(
            topSearchGroup,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(78)).apply {
                leftMargin = dp(18)
                rightMargin = dp(12)
                topMargin = dp(68)
            },
        )

        searchContainer.orientation = LinearLayout.HORIZONTAL
        searchContainer.gravity = Gravity.CENTER_VERTICAL
        searchContainer.setPadding(dp(14), 0, dp(16), 0)
        searchContainer.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(28).toFloat()
        }

        val icon = TextView(context).apply {
            text = "⌕"
            textSize = 28f
            setTextColor(Color.rgb(135, 135, 135))
            gravity = Gravity.CENTER
        }
        searchContainer.addView(icon, LinearLayout.LayoutParams(dp(32), dp(56)))

        val hint = TextView(context).apply {
            text = "搜索职位"
            textSize = 26f
            setTextColor(Color.rgb(145, 145, 145))
        }
        searchContainer.addView(
            hint,
            LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
        )

        val action = TextView(context).apply {
            text = "搜索"
            textSize = 20f
            setTextColor(Color.rgb(0, 146, 255))
        }
        searchContainer.addView(action)

        topSearchGroup.addView(
            searchContainer,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(64)).apply {
                rightMargin = dp(84)
                gravity = Gravity.CENTER_VERTICAL
            },
        )

        val recruit = TextView(context).apply {
            text = "♙\n我要招人"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.rgb(35, 42, 48))
            includeFontPadding = false
        }
        topSearchGroup.addView(
            recruit,
            LayoutParams(dp(76), dp(68), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(2)
            },
        )
    }

    private fun addTitleRow() {
        addView(
            titleRow,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(72)).apply {
                leftMargin = dp(18)
                rightMargin = dp(18)
                topMargin = dp(146)
            },
        )

        val title = TextView(context).apply {
            text = "推荐职位"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20, 24, 28))
            includeFontPadding = false
        }
        titleRow.addView(
            title,
            LayoutParams(LayoutParams.WRAP_CONTENT, dp(60), Gravity.CENTER_VERTICAL),
        )

        val plus = TextView(context).apply {
            text = "+"
            textSize = 46f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(18, 25, 32))
            includeFontPadding = false
        }
        titleRow.addView(
            plus,
            LayoutParams(dp(58), dp(58), Gravity.CENTER_VERTICAL or Gravity.END).apply {
                rightMargin = dp(76)
            },
        )

        compactSearchIcon.text = "⌕"
        compactSearchIcon.textSize = 42f
        compactSearchIcon.gravity = Gravity.CENTER
        compactSearchIcon.setTextColor(Color.rgb(18, 25, 32))
        compactSearchIcon.alpha = 0f
        titleRow.addView(
            compactSearchIcon,
            LayoutParams(dp(58), dp(58), Gravity.CENTER_VERTICAL or Gravity.END),
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()
}
