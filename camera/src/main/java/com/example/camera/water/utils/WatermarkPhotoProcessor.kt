package com.example.camera.water.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 把 Camera2 JPEG 转成正向位图、绘制水印，并按系统版本保存到公共相册。 */
object WatermarkPhotoProcessor {

    /**
     * JPEG_ORIENTATION 通常由相机写入 EXIF，而 BitmapFactory 不会自动按 EXIF 旋转。
     * 这里使用拍照请求中的同一个角度旋转像素，随后直接在正向像素上绘制水印。
     */
    fun createWatermarkedBitmap(
        jpegBytes: ByteArray,
        jpegOrientation: Int,
        timeText: String,
        locationText: String,
    ): Bitmap {
        val source = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            ?: error("无法解码相机 JPEG")
        val matrix = Matrix().apply { postRotate(jpegOrientation.toFloat()) }
        val oriented = if (jpegOrientation == 0) {
            source
        } else {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
                source.recycle()
            }
        }
        val output = oriented.copy(Bitmap.Config.ARGB_8888, true)
            ?: error("无法创建水印位图")
        if (output !== oriented) oriented.recycle()

        drawWatermark(output, timeText, locationText)
        return output
    }

    /**
     * 水印尺寸按照片宽度计算，保证不同像素尺寸下视觉比例一致。
     * 地点过长时按实际像素宽度截断，避免文字超出照片边界。
     */
    private fun drawWatermark(bitmap: Bitmap, timeText: String, locationText: String) {
        val canvas = Canvas(bitmap)
        val scale = bitmap.width / REFERENCE_WIDTH
        val horizontalPadding = 24f * scale
        val verticalPadding = 18f * scale
        val outerMargin = 36f * scale
        val lineGap = 10f * scale
        val maxTextWidth = bitmap.width * MAX_TEXT_WIDTH_RATIO

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(5f * scale, 1f * scale, 2f * scale, Color.BLACK)
        }
        val locationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 31f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            setShadowLayer(4f * scale, 1f * scale, 2f * scale, Color.BLACK)
        }
        val displayedLocation = ellipsize("地点  $locationText", locationPaint, maxTextWidth)
        val timeHeight = timePaint.fontMetrics.run { bottom - top }
        val locationHeight = locationPaint.fontMetrics.run { bottom - top }
        val contentWidth = maxOf(
            timePaint.measureText(timeText),
            locationPaint.measureText(displayedLocation),
        )
        val boxWidth = contentWidth + horizontalPadding * 2
        val boxHeight = timeHeight + locationHeight + lineGap + verticalPadding * 2
        val left = outerMargin
        val bottom = bitmap.height - outerMargin
        val top = bottom - boxHeight

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(125, 0, 0, 0)
        }
        canvas.drawRoundRect(
            RectF(left, top, left + boxWidth, bottom),
            12f * scale,
            12f * scale,
            backgroundPaint,
        )

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(183, 232, 107) }
        canvas.drawRect(left, top, left + 7f * scale, bottom, accentPaint)

        val textLeft = left + horizontalPadding
        val timeBaseline = top + verticalPadding - timePaint.fontMetrics.top
        val locationBaseline = timeBaseline + timePaint.fontMetrics.bottom + lineGap - locationPaint.fontMetrics.top
        canvas.drawText(timeText, textLeft, timeBaseline, timePaint)
        canvas.drawText(displayedLocation, textLeft, locationBaseline, locationPaint)
    }

    /** 保存的是已经绘制水印的位图，因此相册、分享和上传读取到的都是真实水印。 */
    fun saveToGallery(context: Context, bitmap: Bitmap, capturedAtMillis: Long): Uri {
        val fileName = "watermark-${FILE_TIME_FORMAT.format(Date(capturedAtMillis))}.jpg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, bitmap, fileName)
        } else {
            saveToLegacyGallery(context, bitmap, fileName)
        }
    }

    private fun saveWithMediaStore(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法在系统相册创建图片")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "JPEG 编码失败"
                }
            } ?: error("无法打开相册输出流")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (throwable: Throwable) {
            // 写入失败时删除 pending 项，避免相册数据库留下不可见的半成品。
            resolver.delete(uri, null, null)
            throw throwable
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacyGallery(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM_NAME,
        )
        check(directory.exists() || directory.mkdirs()) { "无法创建相册目录" }
        val file = File(directory, fileName)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "JPEG 编码失败"
            }
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/jpeg"),
            null,
        )
        return Uri.fromFile(file)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val suffix = "..."
        val suffixWidth = paint.measureText(suffix)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + suffixWidth > maxWidth) {
            end--
        }
        return text.take(end) + suffix
    }

    private const val REFERENCE_WIDTH = 1080f
    private const val MAX_TEXT_WIDTH_RATIO = 0.72f
    private const val JPEG_QUALITY = 94
    private const val ALBUM_NAME = "WatermarkCamera"
    private val FILE_TIME_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
}
