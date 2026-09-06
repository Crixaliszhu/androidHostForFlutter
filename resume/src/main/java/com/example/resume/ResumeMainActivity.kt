package com.example.resume

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import com.alibaba.android.arouter.facade.annotation.Route
import com.example.resume.route.ResumeRouterImpl
import com.example.widget.titlebar.ToolBarManager
import com.example.widget.titlebar.dialog.CommonDialog2


@Route(path = ResumeRouterImpl.RESUME_MAIN)
class ResumeMainActivity : FragmentActivity() {

    companion object {

        fun startActivity(context: Context?) {
            context ?: return
            val intent = Intent(context, ResumeMainActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resume_main)
        ToolBarManager.attach(
            this, config = ToolBarManager.Config(
                title = "找活主页",
                showTitle = true,
                showBack = true,
                backIconRes = com.example.widget.R.drawable.ic_toolbar_back,
            )
        )
//        enableEdgeToEdge()
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
        findViewById<Button>(R.id.btn_resume_back).setOnClickListener {
            finish()
        }
        findViewById<Button>(R.id.btn_resume_toast).setOnClickListener {
//            Toast.makeText(this, "这是Resume页toast", Toast.LENGTH_SHORT).show()
            CommonDialog2.show(
                supportFragmentManager,
                tag = "commonDialog",
                title = "自定义弹窗标题",
                content = "这是我的弹窗内容",
                negativeClick = {},
                positiveClick = {},
            )
        }
    }
}
