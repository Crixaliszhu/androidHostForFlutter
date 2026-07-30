package com.example.hybriddemo.customview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.atan2

/**
 * PathMeasure 路径动画示例。
 *
 * 这个 View 演示了自定义 View 里最常用的一类路径动画：
 * 1. 用 [PathMeasure.getSegment] 从原始路径中截取 0..distance 的片段，
 *    形成“路径被逐渐画出来”的效果。
 * 2. 用 [PathMeasure.getPosTan] 获取 distance 对应位置的坐标和切线，
 *    让小圆点停在路径当前进度点，并根据切线方向旋转箭头。
 *
 * 注意：Paint / Path / RectF / FloatArray 都作为成员变量复用，
 * 避免在 onDraw() 每帧创建临时对象导致 GC 和掉帧。
 */
class PathAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // 灰色底轨迹：用于展示完整路径轮廓。
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(214, 224, 236)
    }

    // 蓝色进度轨迹：每一帧只绘制 sourcePath 的一部分。
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(0, 146, 255)
    }

    // 路径当前位置的小圆点。
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(255, 116, 63)
    }

    // 小圆点右侧箭头，配合切线角度旋转，能看出运动方向。
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(255, 116, 63)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(36, 44, 54)
        textSize = dp(15f)
    }

    // 原始完整路径。尺寸变化时在 rebuildPath() 中重新计算。
    private val sourcePath = Path()

    // 当前进度对应的路径片段。onDraw() 中 reset 后由 getSegment() 填充。
    private val segmentPath = Path()

    // 小箭头路径，绘制时复用，避免每帧 new Path。
    private val arrowPath = Path()

    // 保留路径边界，便于调试或后续扩展点击区域、居中计算等。
    private val pathBounds = RectF()

    // getPosTan() 的输出数组：pos[0]/pos[1] 是当前点坐标。
    private val pos = FloatArray(2)

    // getPosTan() 的输出数组：tan[0]/tan[1] 是当前点切线向量。
    private val tan = FloatArray(2)

    // PathMeasure 负责测量 Path 长度、截取片段、按距离取点。
    private var pathMeasure = PathMeasure()

    // sourcePath 的总长度。progress * pathLength 就是当前动画距离。
    private var pathLength = 0f

    // 动画进度，范围 0f..1f。
    private var progress = 0f

    // 持有动画引用，便于暂停、重置和 View detach 时取消，避免泄漏。
    private var animator: ValueAnimator? = null

    init {
        // 示例中没有使用硬件加速特性；关闭后部分复杂 Path 效果在旧设备更稳定。
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        start()
    }

    fun start() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(progress, 1f).apply {
            // 从当前 progress 继续播放，而不是每次都从 0 开始。
            duration = ((1f - progress).coerceAtLeast(0.01f) * 3600).toLong()
            interpolator = LinearInterpolator()

            // 到达 1f 后自动回到 0f 重新播放。
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                progress = it.animatedValue as Float

                // progress 变了，触发下一帧重绘。不要直接调用 onDraw()。
                invalidate()
            }
            start()
        }
    }

    fun pause() {
        animator?.cancel()
        animator = null
    }

    fun reset() {
        pause()
        progress = 0f

        // 重置后主动刷新一次，让界面立即回到起点。
        invalidate()
    }

    override fun onDetachedFromWindow() {
        // 无限动画如果不取消，会继续持有 View/Context，页面退出后容易泄漏。
        pause()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Path 的坐标依赖 View 尺寸，首次布局和尺寸变化时都要重新构建。
        rebuildPath(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pathLength <= 0f) return

        canvas.drawColor(Color.rgb(248, 250, 252))
        drawTitle(canvas)

        canvas.drawPath(sourcePath, trackPaint)

        segmentPath.reset()

        // distance 是当前动画在路径上的“实际路程”。
        val distance = pathLength * progress

        // 截取 sourcePath 从 0 到 distance 的片段到 segmentPath。
        // startWithMoveTo=true 表示片段开头自动 moveTo，避免路径连接到旧位置。
        pathMeasure.getSegment(0f, distance, segmentPath, true)
        canvas.drawPath(segmentPath, progressPaint)

        // 根据 distance 获取当前点坐标和切线，用于绘制沿路径运动的小圆点。
        pathMeasure.getPosTan(distance, pos, tan)
        drawMovingPoint(canvas)
        drawProgressLabel(canvas, distance)
    }

    private fun rebuildPath(width: Int, height: Int) {
        if (width == 0 || height == 0) return

        val left = paddingLeft + dp(28f)
        val right = width - paddingRight - dp(28f)
        val top = paddingTop + dp(96f)
        val bottom = height - paddingBottom - dp(84f)
        val centerY = (top + bottom) / 2f

        // 构建一条两段三阶贝塞尔曲线。实际业务里这里可以换成任意 Path：
        // 直线、圆弧、贝塞尔曲线、SVG 转 Path 等都能被 PathMeasure 测量。
        sourcePath.reset()
        sourcePath.moveTo(left, centerY)
        sourcePath.cubicTo(
            left + (right - left) * 0.24f,
            top,
            left + (right - left) * 0.38f,
            bottom,
            left + (right - left) * 0.52f,
            centerY,
        )
        sourcePath.cubicTo(
            left + (right - left) * 0.66f,
            top - dp(24f),
            left + (right - left) * 0.82f,
            bottom + dp(18f),
            right,
            centerY - dp(18f),
        )

        // 计算边界不是路径动画必需步骤，这里保留给调试和扩展使用。
        sourcePath.computeBounds(pathBounds, true)

        // false 表示不强制闭合路径。若传 true，PathMeasure 会把终点连回起点。
        pathMeasure.setPath(sourcePath, false)
        pathLength = pathMeasure.length
    }

    private fun drawMovingPoint(canvas: Canvas) {
        // save/restore 隔离坐标系变换，避免 translate/rotate 影响后续文字绘制。
        val saveCount = canvas.save()

        // 把画布原点移动到路径当前点，后续图形都围绕这个点绘制。
        canvas.translate(pos[0], pos[1])

        // tan 是切线向量，atan2(y, x) 可得到当前路径方向角。
        val degrees = Math.toDegrees(atan2(tan[1], tan[0]).toDouble()).toFloat()
        canvas.rotate(degrees)
        canvas.drawCircle(0f, 0f, dp(9f), ballPaint)

        // 在局部坐标系下画一个朝右的小三角。画布旋转后，它会沿路径方向指向前方。
        arrowPath.reset()
        arrowPath.moveTo(dp(15f), 0f)
        arrowPath.lineTo(dp(4f), -dp(6f))
        arrowPath.lineTo(dp(4f), dp(6f))
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restoreToCount(saveCount)
    }

    private fun drawTitle(canvas: Canvas) {
        textPaint.textSize = dp(18f)
        textPaint.isFakeBoldText = true
        canvas.drawText("PathMeasure 路径动画", dp(24f), dp(42f), textPaint)
        textPaint.textSize = dp(13f)
        textPaint.isFakeBoldText = false
        canvas.drawText("蓝色路径由 getSegment() 截取，小圆点由 getPosTan() 定位", dp(24f), dp(66f), textPaint)
    }

    private fun drawProgressLabel(canvas: Canvas, distance: Float) {
        textPaint.textSize = dp(14f)
        val percent = (progress * 100).toInt()
        canvas.drawText("distance=${distance.toInt()} / ${pathLength.toInt()}   progress=$percent%", dp(24f), height - dp(32f), textPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
