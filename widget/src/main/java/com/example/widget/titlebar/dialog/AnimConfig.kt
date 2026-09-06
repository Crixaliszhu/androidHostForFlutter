package com.example.widget.titlebar.dialog

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.Window
import android.view.animation.LinearInterpolator
import androidx.fragment.app.DialogFragment
import com.example.widget.R

internal class AnimConfig {

    companion object {
        //动画弹出形式  1中心 2底部 else 默认
        const val ANIM_TYPE_DEFAULT = 0
        const val ANIM_TYPE_CENTER = 1
        const val ANIM_TYPE_BOTTOM = 2

        // 阴影透明度
        private const val shadeAlpha = 0.55f

        fun initShowAnim(animType: Int, window: Window) {
            if (animType == ANIM_TYPE_CENTER) {
                window.setDimAmount(0f)
                window.decorView.apply {
                    alpha = 0f
                    scaleX = 0f
                    scaleY = 0f
                }
                AnimConfig().showCenterAnim(window)
            } else if (animType == ANIM_TYPE_BOTTOM) {
                window.setWindowAnimations(R.style.AnimBottomDialog)
            }
        }

        fun initHideAnim(animType: Int, window: Window, dialogFragment: DialogFragment) {
            if (animType == ANIM_TYPE_CENTER) {
                window.setDimAmount(shadeAlpha)
                window.decorView.apply {
                    alpha = 1.0f
                    scaleY = 1.0f
                    scaleX = 1.0f
                }
                AnimConfig().hideCenterAnim(window, dialogFragment)
            } else {
                dialogFragment.dismissAllowingStateLoss()
            }
        }
    }

    /**
     * 动画持续时间
     */
    private val durationTime = 300L

    /**
     * 贝塞尔曲线值
     *  @sample 1f,0.23f,1f,0.39f  这个就是很抖的 先快后慢
     */
    private val interpolatorValue = listOf(0.25f, 0.1f, 0.25f, 1.0f)


    /**
     * 动画是否正在播放
     */
    private var isPlaying = false

    fun showCenterAnim(window: Window) {
        window.decorView.post {
            val alpha = ObjectAnimator.ofFloat(window.decorView, "alpha", 0.0f, 1.0f)
            alpha.interpolator = LinearInterpolator()
            alpha.duration = durationTime
            alpha.startDelay = durationTime / 3
            alpha.addUpdateListener { animation ->
                val av = animation.animatedValue as Float
                window.decorView.scaleX = av
                window.decorView.scaleY = av
            }
            val dima = ObjectAnimator.ofFloat(window, "dimAmount", 0.0f, shadeAlpha)
            dima.interpolator = LinearInterpolator()
            dima.duration = durationTime
            val animSet = AnimatorSet()
            animSet.play(alpha).with(dima)
            animSet.start()
        }
    }

    private fun hideCenterAnim(window: Window, dialogFragment: DialogFragment) {
        if (isPlaying) {
            return
        }
        window.decorView.post {
            isPlaying = true
            val alpha = ObjectAnimator.ofFloat(window.decorView, "alpha", 1.0f, 0.0f)
            alpha.addUpdateListener { animation ->
                val s = animation.animatedValue as Float
                window.decorView.scaleX = s
                window.decorView.scaleY = s
            }
            alpha.duration = durationTime
            alpha.startDelay = durationTime / 3

            val dim = ObjectAnimator.ofFloat(window, "dimAmount", shadeAlpha, 0.0f)
            dim.duration = durationTime
            alpha.addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    //
                }

                override fun onAnimationEnd(animation: Animator) {
                    isPlaying = false
                    try {
                        dialogFragment.dismissAllowingStateLoss()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    //
                }

                override fun onAnimationRepeat(animation: Animator) {
                    //
                }

            })

            val animSet = AnimatorSet()
            animSet.interpolator = LinearInterpolator()
            animSet.play(alpha).with(dim)
            animSet.start()
        }
    }
}