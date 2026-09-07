package com.example.widget.titlebar.toolbar

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
 * 标题栏设置
 */
class ToolBarManager2 private constructor(
    private val activity: FragmentActivity,
    private val config: Config,
    private val onBackClick: (() -> Unit)?,
) {

    /**
     * 页面标题栏配置
     */
    @Keep
    data class Config(
        val title: CharSequence? = null,
        val showTitle: Boolean = true,
        val showBack: Boolean = true,
        @DrawableRes val backIconRes: Int = R.drawable.ic_toolbar_back,
        @ColorInt val toolbarColor: Int = Color.WHITE,
        @ColorInt val titleColor: Int = Color.BLACK,
        @ColorInt val backIconColor: Int = Color.BLACK,
        @ColorInt val statusBarColor: Int = Color.WHITE,
        @ColorInt val navigationBarColor: Int = Color.WHITE,
        val darkNavigationBarIcons: Boolean = true,
        val darkStatusBarIcons: Boolean = true,
    )

    /**
     * 当前系统占用的顶部和底部高度，单位为px
     */
    @Keep
    data class SystemBarHeights(
        val statusBarHeight: Int,
        val navigationBarHeight: Int,
    )

    companion object {

        /**
         * 在Activity 当前内容之上安装标题栏，必须在setContentView 之后调用。
         */
        fun attach(
            activity: FragmentActivity,
            config: Config = Config(),
            onBackClick: (() -> Unit)? = null
        ): ToolBarManager2 {
            return ToolBarManager2(activity, config, onBackClick)
        }
    }

    // 系统content布局-FrameLayout
    private val activityContent: ViewGroup = activity.findViewById(android.R.id.content)

    // 创建HostView承载：toolBar布局-LinearLayout， 页面布局，导航栏布局
    private val hostView = LinearLayout(activity).apply {
        id = R.id.widget_toolbar_host
        orientation = LinearLayout.VERTICAL
    }

    // toolBar布局-FrameLayout - 上
    private val titleBarView = LayoutInflater.from(activity)
        .inflate(R.layout.widget_toolbar, hostView, false)
    private val statusBarHeightView: View = titleBarView.findViewById(R.id.widget_status_bar_space)
    private val toolbarContainer: View = titleBarView.findViewById(R.id.widget_toolbar_container)
    private val backView: ImageView = titleBarView.findViewById(R.id.widget_toolbar_back)
    private val titleView: TextView = titleBarView.findViewById(R.id.widget_toolbar_title)

    // 创建一个FrameLayout用来装原来Activity根布局的父布局 - 中
    private val contentContainer = FrameLayout(activity).apply {
        id = R.id.widget_toolbar_content
    }

    // 创建导航栏布局 - 下
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

    fun setTitle(title: CharSequence?): ToolBarManager2 = apply {
        titleView.text = title ?: ""
    }

    fun setTitle(@StringRes titleRes: Int): ToolBarManager2 = apply {
        titleView.setText(titleRes)
    }

    fun setTitleVisible(visible: Boolean): ToolBarManager2 = apply {
        titleView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setTitleColor(@ColorInt color: Int): ToolBarManager2 = apply {
        titleView.setTextColor(color)
    }

    fun setToolbarColor(@ColorInt color: Int): ToolBarManager2 = apply {
        toolbarContainer.setBackgroundColor(color)
    }

    fun setBackIcon(@DrawableRes iconRes: Int): ToolBarManager2 = apply {
        backView.setImageResource(iconRes)
    }

    fun setBackIconColor(@ColorInt color: Int): ToolBarManager2 = apply {
        ImageViewCompat.setImageTintList(backView, ColorStateList.valueOf(color))
    }

    fun setBackContentDescription(@StringRes descriptionRes: Int): ToolBarManager2 = apply {
        backView.contentDescription = activity.getText(descriptionRes)
    }

    fun setBackVisible(visible: Boolean): ToolBarManager2 = apply {
        backView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setOnBackClickListener(listener: (() -> Unit)? = null): ToolBarManager2 = apply {
        backView.setOnClickListener {
            listener?.invoke() ?: activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * 状态栏颜色-图标设置，[darkIcons] 为 true 表示使用深色状态栏图标，适用于浅色背景。
     */
    fun setStatusBarAppearance(
        @ColorInt backgroundColor: Int,
        darkIcons: Boolean,
    ): ToolBarManager2 = apply {
        statusBarHeightView.setBackgroundColor(backgroundColor)
        activity.window.statusBarColor = backgroundColor
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = darkIcons
    }

    /**
     * 导航栏颜色，图标设置， [darkIcons] 为 true 表示使用深色导航栏图标，适用于浅色背景。
     */
    fun setNavigationBarAppearance(
        @ColorInt backgroundColor: Int,
        darkIcons: Boolean,
    ): ToolBarManager2 = apply {
        navigationBarHeightView.setBackgroundColor(backgroundColor)
        activity.window.navigationBarColor = backgroundColor
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightNavigationBars = darkIcons
    }

    /**
     * 标题栏组装，插入toolbarView到 content，并把activity布局装入toolbarView中
     */
    private fun installIntoActivity() {
        require(activityContent.findViewById<View>(R.id.widget_toolbar_host) == null) {
            "ToolBarManager2 已经安装到当前 Activity"
        }

        require(activityContent.childCount > 0) {
            "请在 Activity.setContentView() 之后调用 ToolBarManager.attach()"
        }
        // 系统原布局下的子布局
        val originalContent = List(activityContent.childCount) {
            activityContent.getChildAt(it)
        }
        activityContent.removeAllViews()
        // 添加标题栏布局
        hostView.addView(
            titleBarView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )
        // 添加页面父布局负责装子布局
        hostView.addView(
            contentContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        // 添加导航栏布局
        hostView.addView(
            navigationBarHeightView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0),
        )
        // 添加host到content布局
        activityContent.addView(
            hostView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        originalContent.forEach { contentView ->
            contentContainer.addView(
                contentView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun installSystemBarInsets() {
        // 透明系统栏配合占位 View, Android 15 强制edge-to-edge 后仍能显示配置的背景色
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        ViewCompat.setOnApplyWindowInsetsListener(hostView) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val navigationBarHeight =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            systemBarHeights = SystemBarHeights(statusBarHeight, navigationBarHeight)
            statusBarHeightView.updateHeight(statusBarHeight)
            navigationBarHeightView.updateHeight(navigationBarHeight)
            // 标题栏已经消费系统栏控件，业务内容只继续接受IME等其他Insets
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