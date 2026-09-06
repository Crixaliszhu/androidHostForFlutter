package com.example.widget.titlebar

import android.content.Context

object DensityUtils {

    fun dp2px(context: Context, dipValue: Float): Int {
        val scale: Float = context.resources.displayMetrics.density
        return (dipValue * scale + 0.5f).toInt()

    }
}