package com.example.widget.titlebar.adapter

import android.graphics.Typeface
import android.widget.TextView
import androidx.databinding.BindingAdapter

/** 根据绑定值切换粗体，同时保留字体原有的斜体样式。 */
@BindingAdapter("textBold")
fun TextView.bindTextBold(isBold: Boolean) {
    val targetStyle = if (isBold) {
        typeface.style or Typeface.BOLD
    } else {
        typeface.style and Typeface.BOLD.inv()
    }
    if (typeface.style != targetStyle) {
        setTypeface(typeface, targetStyle)
    }
}
