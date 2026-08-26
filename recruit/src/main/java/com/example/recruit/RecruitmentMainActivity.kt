package com.example.recruit

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.example.recruit.api.RecruitRouterApiPaths
import com.example.recruit.ui.FloatingImageBall
import com.example.recruit.ui.theme.FlutterHybridDemoTheme

@Route(path = RecruitRouterApiPaths.RECRUIT_MAIN)
class RecruitmentMainActivity : ComponentActivity() {
    companion object{
        const val TAG = "Activity-SM"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlutterHybridDemoTheme {
                var showFloatingBall by rememberSaveable { mutableStateOf(true) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Greeting(name = "招聘主页面")
                            Button(
                                onClick = {
                                    ARouter.getInstance()
                                        .build(RecruitRouterApiPaths.RECRUIT_FLOATING_BALL)
                                        .navigation(this@RecruitmentMainActivity)
                                },
                            ) {
                                Text("打开 View 浮标演示")
                            }
                        }
                        if (showFloatingBall) {
                            FloatingImageBall(
                                onClick = {
                                    Toast.makeText(
                                        this@RecruitmentMainActivity,
                                        "点击了招聘浮标",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                onClose = { showFloatingBall = false },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.e(TAG, "A - onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.e(TAG, "A - onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e(TAG, "A - onDestroy")
    }

    override fun onResume() {
        super.onResume()
        Log.e(TAG, "A - onResume")
    }

    override fun onRestart() {
        super.onRestart()
        Log.e(TAG, "A - onRestart")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FlutterHybridDemoTheme {
        Greeting("Android")
    }
}
