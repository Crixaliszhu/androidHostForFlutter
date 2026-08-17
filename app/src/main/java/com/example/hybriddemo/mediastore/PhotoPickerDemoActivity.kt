package com.example.hybriddemo.mediastore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
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
 * 演示三种选图方式：
 * 1. Photo Picker（Android 13+ 原生，无需权限）。
 * 2. MediaStore 查询（手动 ContentResolver.query，需要权限）。
 * 3. Intent ACTION_OPEN_DOCUMENT 打开系统文件选择器（类似 openAlbum 的底层原理）。
 */
@com.alibaba.android.arouter.facade.annotation.Route(path = com.example.hybriddemo.router.DemoRouterPaths.PHOTO_PICKER)
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

    // ==================== 方式三：Intent 打开相册 ====================

    /**
     * 通过 ACTION_OPEN_DOCUMENT 打开系统文件选择器。
     * 这是 openAlbum 底层的原理之一（PictureSelector 库内部也是通过 Intent 启动选择界面）。
     *
     * 优点：无需运行时权限（返回的 Uri 自带临时读取授权）。
     * 适用：用户主动选择文件的场景。
     */
    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    Log.d(TAG, "Intent 选中: $uri")
                    showImage(uri, "Intent ACTION_OPEN_DOCUMENT")
                }
            } else {
                binding.tvStatus.text = "用户取消选择"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoPickerDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 方式一：Photo Picker
        binding.btnPickPhoto.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // 方式二：MediaStore 查询最新图片
        binding.btnMediaStore.setOnClickListener {
            pickFromMediaStore()
        }

        // 方式三：Intent 打开相册让用户选择
        binding.btnOpenDocument.setOnClickListener {
            openAlbumViaIntent()
            Looper.getMainLooper().setMessageLogging {

            }
        }
    }

    // ==================== 方式二实现 ====================

    private fun pickFromMediaStore() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            queryLatestImageFromMediaStore()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun queryLatestImageFromMediaStore() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder,
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                val size = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))

                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                showImage(uri, "MediaStore 查询\n$name (${size / 1024}KB)")
            } else {
                binding.tvStatus.text = "MediaStore: 未找到任何图片"
            }
        }
    }

    // ==================== 方式三实现 ====================

    /**
     * 通过 Intent 打开系统相册/文件选择器。
     * 这模拟了 recruitment_android 中 openAlbum 的底层行为：
     * - PictureSelector 库最终也是通过 Intent 启动一个选择 Activity
     * - 用户选择后通过 onActivityResult 返回选中的 Uri
     */
    private fun openAlbumViaIntent() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"  // 限定只选择图片
            // 如果需要同时选图片和视频，可以用：
            // type = "*/*"
            // putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        openDocumentLauncher.launch(intent)
    }

    // ==================== 展示图片 ====================

    private fun showImage(uri: Uri, source: String) {
        binding.ivPhoto.setImageURI(uri)
        binding.tvStatus.text = "来源: $source\nUri: $uri"
    }
}
