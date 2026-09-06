package com.example.widget.titlebar

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.FragmentActivity
import com.example.widget.R

/**
 * 为传统 View 页面自动安装标题栏，并统一处理系统栏样式和高度。
 *
 * Activity 先调用 setContentView，再调用 [attach]。管理器会保留原页面根 View，自动在其上方
 * 加入状态栏占位和标题栏、下方加入导航栏占位，业务布局不需要 include 标题栏。
 */
class ToolBarManager private constructor(
    private val activity: FragmentActivity,
    config: Config,
    onBackClick: (() -> Unit)?,
) {
    /** 页面只需要声明标题和返回按钮等可变配置。 */
    class Config(
        val title: CharSequence? = null,
        val showTitle: Boolean = true,
        val showBack: Boolean = true,
        @DrawableRes val backIconRes: Int = R.drawable.ic_toolbar_back,
        @ColorInt val toolbarColor: Int = Color.WHITE,
        @ColorInt val titleColor: Int = Color.rgb(31, 35, 41),
        @ColorInt val backIconColor: Int = Color.rgb(31, 35, 41),
        @ColorInt val statusBarColor: Int = Color.WHITE,
        val darkStatusBarIcons: Boolean = true,
        @ColorInt val navigationBarColor: Int = Color.WHITE,
        val darkNavigationBarIcons: Boolean = true,
    )

    /** 当前系统栏占用的顶部和底部高度，单位为 px。 */
    class SystemBarHeights(
        val statusBarHeight: Int,
        val navigationBarHeight: Int,
    )

    companion object {
        /** 在 Activity 当前内容之上安装标题栏，必须在 setContentView 之后调用。 */
        fun attach(
            activity: FragmentActivity,
            config: Config = Config(),
            onBackClick: (() -> Unit)? = null,
        ): ToolBarManager {
            return ToolBarManager(activity, config, onBackClick)
        }
    }

    private val activityContent: ViewGroup = activity.findViewById(android.R.id.content)
    private val hostView = LinearLayout(activity).apply {
        id = R.id.widget_toolbar_host
        orientation = LinearLayout.VERTICAL
    }
    private val titleBarView = LayoutInflater.from(activity)
        .inflate(R.layout.widget_toolbar, hostView, false)
    private val statusBarHeightView: View = titleBarView.findViewById(R.id.widget_status_bar_space)
    private val toolbarContainer: View = titleBarView.findViewById(R.id.widget_toolbar_container)
    private val backView: ImageView = titleBarView.findViewById(R.id.widget_toolbar_back)
    private val titleView: TextView = titleBarView.findViewById(R.id.widget_toolbar_title)
    private val contentContainer = FrameLayout(activity).apply {
        id = R.id.widget_toolbar_content
    }
    private val navigationBarHeightView = View(activity)

    var systemBarHeights: SystemBarHeights = SystemBarHeights(0, 0)
        private set

    init {
        installIntoActivity()
        setTitle(config.title)
        setTitleVisible(config.showTitle)
        setBackIcon(config.backIconRes)
        setBackIconColor(config.backIconColor)
        setBackVisible(config.showBack)
        setBackContentDescription(R.string.widget_toolbar_back)
        setOnBackClickListener(onBackClick)
        setToolbarColor(config.toolbarColor)
        setTitleColor(config.titleColor)
        setStatusBarAppearance(config.statusBarColor, config.darkStatusBarIcons)
        setNavigationBarAppearance(config.navigationBarColor, config.darkNavigationBarIcons)
        installSystemBarInsets()
    }

    fun setTitle(title: CharSequence?): ToolBarManager = apply {
        titleView.text = title ?: ""
    }

    fun setTitle(@StringRes titleRes: Int): ToolBarManager = apply {
        titleView.setText(titleRes)
    }

    fun setTitleVisible(visible: Boolean): ToolBarManager = apply {
        titleView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setTitleColor(@ColorInt color: Int): ToolBarManager = apply {
        titleView.setTextColor(color)
    }

    fun setToolbarColor(@ColorInt color: Int): ToolBarManager = apply {
        toolbarContainer.setBackgroundColor(color)
    }

    fun setBackIcon(@DrawableRes iconRes: Int): ToolBarManager = apply {
        backView.setImageResource(iconRes)
    }

    fun setBackIconColor(@ColorInt color: Int): ToolBarManager = apply {
        ImageViewCompat.setImageTintList(backView, ColorStateList.valueOf(color))
    }

    fun setBackContentDescription(@StringRes descriptionRes: Int): ToolBarManager = apply {
        backView.contentDescription = activity.getText(descriptionRes)
    }

    fun setBackVisible(visible: Boolean): ToolBarManager = apply {
        backView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** 传入 null 时使用 Activity 的 OnBackPressedDispatcher 处理返回。 */
    fun setOnBackClickListener(listener: (() -> Unit)? = null): ToolBarManager = apply {
        backView.setOnClickListener {
            listener?.invoke() ?: activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    /** [darkIcons] 为 true 表示使用深色状态栏图标，适用于浅色背景。 */
    fun setStatusBarAppearance(
        @ColorInt backgroundColor: Int,
        darkIcons: Boolean,
    ): ToolBarManager = apply {
        statusBarHeightView.setBackgroundColor(backgroundColor)
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = backgroundColor
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = darkIcons
    }

    /** [darkIcons] 为 true 表示使用深色导航栏图标，适用于浅色背景。 */
    fun setNavigationBarAppearance(
        @ColorInt backgroundColor: Int,
        darkIcons: Boolean,
    ): ToolBarManager = apply {
        navigationBarHeightView.setBackgroundColor(backgroundColor)
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = backgroundColor
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightNavigationBars = darkIcons
    }

    private fun installIntoActivity() {
        require(activityContent.findViewById<View>(R.id.widget_toolbar_host) == null) {
            "ToolBarManager 已经安装到当前 Activity"
        }
        require(activityContent.childCount > 0) {
            "请在 Activity.setContentView() 之后调用 ToolBarManager.attach()"
        }

        val originalContent = List(activityContent.childCount) { activityContent.getChildAt(it) }
        activityContent.removeAllViews()

        hostView.addView(
            titleBarView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        hostView.addView(
            contentContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        hostView.addView(
            navigationBarHeightView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0),
        )
        activityContent.addView(
            hostView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        originalContent.forEach { contentView ->
            contentContainer.addView(
                contentView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun installSystemBarInsets() {
        // 透明系统栏配合占位 View，Android 15 强制 edge-to-edge 后仍能显示配置的背景色。
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        ViewCompat.setOnApplyWindowInsetsListener(hostView) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationBarHeight =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            systemBarHeights = SystemBarHeights(statusBarHeight, navigationBarHeight)
            statusBarHeightView.updateHeight(statusBarHeight)
            navigationBarHeightView.updateHeight(navigationBarHeight)

            // 标题栏已经消费系统栏空间，业务内容只继续接收 IME 等其他 Insets。
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
                .build()
        }
        ViewCompat.requestApplyInsets(hostView)
    }

    private fun View.updateHeight(height: Int) {
        if (layoutParams.height == height) return
        layoutParams = layoutParams.apply {
            this.height = height.coerceAtLeast(0)
        }
    }
}
