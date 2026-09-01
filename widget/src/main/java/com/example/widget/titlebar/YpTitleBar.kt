package com.example.widget.titlebar

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class YpTitleBarColors internal constructor(
    val containerColor: Color,
    val titleColor: Color,
    val navigationIconColor: Color,
    val actionIconColor: Color,
)

@Immutable
object YpTitleBarDefaults {
    val Height: Dp = 48.dp
    val IconButtonSize: Dp = 48.dp
    val IconSize: Dp = 24.dp

    @Composable
    fun colors(
        containerColor: Color = Color.White,
        titleColor: Color = Color(0xFF1F2329),
        navigationIconColor: Color = Color(0xFF1F2329),
        actionIconColor: Color = Color(0xFF1F2329),
    ): YpTitleBarColors {
        return YpTitleBarColors(
            containerColor = containerColor,
            titleColor = titleColor,
            navigationIconColor = navigationIconColor,
            actionIconColor = actionIconColor,
        )
    }
}

@Composable
fun YpTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBackClick: (() -> Unit)? = null,
    colors: YpTitleBarColors = YpTitleBarDefaults.colors(),
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val resolvedBackClick: () -> Unit = onBackClick ?: {
        backDispatcher?.onBackPressed()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.containerColor)
            .statusBarsPadding()
            .height(YpTitleBarDefaults.Height),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(YpTitleBarDefaults.IconButtonSize),
            contentAlignment = Alignment.Center,
        ) {
            when {
                navigationIcon != null -> {
                    CompositionLocalProvider(LocalContentColor provides colors.navigationIconColor) {
                        navigationIcon()
                    }
                }

                showBack -> {
                    IconButton(onClick = resolvedBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(YpTitleBarDefaults.IconSize),
                            tint = colors.navigationIconColor,
                        )
                    }
                }
            }
        }

        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = YpTitleBarDefaults.IconButtonSize),
            color = colors.titleColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.actionIconColor) {
                actions()
            }
        }
    }
}

@Composable
fun YpPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBackClick: (() -> Unit)? = null,
    colors: YpTitleBarColors = YpTitleBarDefaults.colors(),
    containerColor: Color = Color.White,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = containerColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            YpTitleBar(
                title = title,
                showBack = showBack,
                onBackClick = onBackClick,
                colors = colors,
                actions = actions,
            )
        },
        content = content,
    )
}
