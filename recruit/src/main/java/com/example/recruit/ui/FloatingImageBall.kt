package com.example.recruit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.recruit.R
import kotlin.math.roundToInt

@Composable
fun FloatingImageBall(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val ballSize = 72.dp
    val closeSize = 16.dp
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxOffsetX = with(density) { (maxWidth - ballSize).toPx().coerceAtLeast(0f) }
        val maxOffsetY = with(density) { (maxHeight - ballSize).toPx().coerceAtLeast(0f) }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(ballSize)
                .pointerInput(maxOffsetX, maxOffsetY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(-maxOffsetX, 0f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxOffsetY)
                        onClick()
                    }
                }
                .semantics { contentDescription = "招聘头像浮标" },
        ) {
            Image(
                painter = painterResource(R.drawable.floating_avatar),
                contentDescription = "打开招聘页面",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.White, CircleShape)
                    .clip(CircleShape),
            )

            Image(
                painter = painterResource(R.drawable.floating_close),
                contentDescription = "关闭浮标",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(closeSize)
                    .clickable(onClick = onClose),
            )
        }
    }
}
