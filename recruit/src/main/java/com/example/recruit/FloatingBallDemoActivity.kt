package com.example.recruit

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.alibaba.android.arouter.facade.annotation.Route
import com.example.recruit.api.RecruitRouterApiPaths
import com.example.recruit.ui.FloatingBallLayout

@Route(path = RecruitRouterApiPaths.RECRUIT_FLOATING_BALL)
class FloatingBallDemoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e(RecruitmentMainActivity.TAG, "B - onCreate")
        setContentView(R.layout.activity_floating_ball_demo)

        val floatingBall = findViewById<FloatingBallLayout>(R.id.floatingBall)
        val showFloatingBall = findViewById<Button>(R.id.showFloatingBall)

        floatingBall.onBallClick = {
            Toast.makeText(this, "点击了 View 浮标", Toast.LENGTH_SHORT).show()
        }
        floatingBall.onBallClose = {
            floatingBall.visibility = View.GONE
            showFloatingBall.visibility = View.VISIBLE
        }
        showFloatingBall.setOnClickListener {
            floatingBall.visibility = View.VISIBLE
            showFloatingBall.visibility = View.GONE
        }
        showFloatingBall.visibility = View.GONE
    }

    override fun onStart() {
        super.onStart()
        Log.e(RecruitmentMainActivity.TAG, "B - onStart")
    }

    override fun onPause() {
        super.onPause()
        Log.e(RecruitmentMainActivity.TAG, "B - onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.e(RecruitmentMainActivity.TAG, "B - onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(RecruitmentMainActivity.TAG, "B - onDestroy")
    }
}
