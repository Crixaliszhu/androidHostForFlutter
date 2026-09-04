package com.example.camera.adapter

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.databinding.BindingAdapter

@BindingAdapter("bitmap")
fun ImageView.bindBitmap(bitmap: Bitmap?) {
    setImageBitmap(bitmap)
}