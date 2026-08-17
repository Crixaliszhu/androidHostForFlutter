package com.example.recruit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.recruit.R
import kotlin.math.hypot
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class FloatingBallLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val avatarView = ImageView(context)
    private val closeView = ImageView(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downRawX = 0f
    private var downRawY = 0f
    private var startTranslationX = 0f
    private var startTranslationY = 0f
    private var dragging = false

    var onBallClick: (() -> Unit)? = null
    var onBallClose: (() -> Unit)? = null

    init {
        clipChildren = false
        clipToPadding = false

        avatarView.setImageResource(R.drawable.floating_avatar)
        avatarView.scaleType = ImageView.ScaleType.CENTER_CROP
        avatarView.setBackgroundResource(R.drawable.bg_floating_ball)
        avatarView.clipToOutline = true

        closeView.setImageResource(R.drawable.floating_close)
        closeView.contentDescription = "关闭浮标"
        closeView.setOnClickListener { onBallClose?.invoke() }

        addView(avatarView, LayoutParams(dp(72), dp(72)))
        addView(
            closeView,
            LayoutParams(dp(26), dp(26)).apply {
                gravity = Gravity.TOP or Gravity.START
            },
        )

        avatarView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startTranslationX = translationX
                    startTranslationY = translationY
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        translationX = clampTranslationX(startTranslationX + dx)
                        translationY = clampTranslationY(startTranslationY + dy)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    if (!dragging) {
                        performClick()
                        onBallClick?.invoke()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                else -> false
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun clampTranslationX(value: Float): Float {
        val parentWidth = (parent as? View)?.width ?: return value
        return value.coerceIn(-(parentWidth - width).toFloat(), 0f)
    }

    private fun clampTranslationY(value: Float): Float {
        val parentHeight = (parent as? View)?.height ?: return value
        return value.coerceIn(0f, (parentHeight - height).toFloat().coerceAtLeast(0f))
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }
}
