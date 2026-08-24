package com.example.hybriddemo.life

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.hybriddemo.databinding.LifecycleMainActivityBinding

class LifecycleMainActivity : AppCompatActivity() {
    private lateinit var _binding: LifecycleMainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = LifecycleMainActivityBinding.inflate(layoutInflater)
        setContentView(_binding.root)
    }

    private fun initView(){
        _binding.fcvTab
    }
}