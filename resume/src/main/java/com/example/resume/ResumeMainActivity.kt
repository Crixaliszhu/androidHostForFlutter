package com.example.resume

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.alibaba.android.arouter.facade.annotation.Route
import com.example.resume.route.ResumeRouterImpl


@Route(path = ResumeRouterImpl.RESUME_MAIN)
class ResumeMainActivity : ComponentActivity() {

    companion object {

        fun startActivity(context: Context?) {
            context ?: return
            val intent = Intent(context, ResumeMainActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_resume_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<Button>(R.id.btn_resume_toast).setOnClickListener {
            Toast.makeText(this, "这是Resume页toast", Toast.LENGTH_SHORT).show()
        }
    }
}
