package com.example.hybriddemo.mediastore

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.hybriddemo.databinding.ActivityPhotoPickerDemoBinding

/**
 * 相册图片选择 & 展示 Demo。
 *
 * 演示两种选图方式：
 * 1. Photo Picker（Android 13+ 原生，无需权限）。
 * 2. MediaStore 查询（手动 ContentResolver.query，需要 READ_MEDIA_IMAGES 权限）。
 */
class PhotoPickerDemoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PhotoPickerDemo"
    }

    private lateinit var binding: ActivityPhotoPickerDemoBinding

    // ==================== 方式一：Photo Picker ====================

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) {
                Log.d(TAG, "Photo Picker 选中: $uri")
                showImage(uri, "Photo Picker")
            } else {
                Log.d(TAG, "用户未选择图片")
                binding.tvStatus.text = "未选择图片"
            }
        }

    // ==================== 方式二：MediaStore 权限请求 ====================

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                queryLatestImageFromMediaStore()
            } else {
                binding.tvStatus.text = "权限被拒绝，无法读取相册"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoPickerDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 方式一：Photo Picker（无需权限）
        binding.btnPickPhoto.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // 方式二：MediaStore 查询最新一张图片
        binding.btnMediaStore.setOnClickListener {
            pickFromMediaStore()
        }
    }

    // ==================== MediaStore 方式实现 ====================

    private fun pickFromMediaStore() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES  // Android 13+
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE  // Android 12 及以下
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            queryLatestImageFromMediaStore()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    /**
     * 通过 MediaStore 查询设备上最新的一张图片并展示。
     *
     * 核心步骤：
     * 1. 指定查询的 URI：MediaStore.Images.Media.EXTERNAL_CONTENT_URI
     * 2. 指定要返回的字段（projection）
     * 3. 指定排序方式（最新优先）
     * 4. 用 _ID 拼接出完整的 content:// URI
     */
    private fun queryLatestImageFromMediaStore() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,   // selection（无过滤条件）
            null,   // selectionArgs
            sortOrder,
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val size = it.getLong(sizeColumn)

                // 通过 ID 构建 content:// URI
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                Log.d(TAG, "MediaStore 查到最新图片: name=$name, size=${size / 1024}KB, uri=$uri")
                showImage(uri, "MediaStore 查询\n$name (${size / 1024}KB)")
            } else {
                binding.tvStatus.text = "MediaStore: 未找到任何图片"
            }
        }
    }

    // ==================== 展示图片 ====================

    private fun showImage(uri: Uri, source: String) {
        binding.ivPhoto.setImageURI(uri)
        binding.tvStatus.text = "来源: $source\nUri: $uri"
    }
}
