package com.example.hybriddemo.anrdemo

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.SystemClock

class AnrDemoProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        // 该 Provider 运行在独立进程，宿主主线程同步 query 时会阻塞在 Binder 调用上。
        SystemClock.sleep(BLOCK_DURATION_MS)
        return MatrixCursor(arrayOf("result")).apply {
            addRow(arrayOf("slow provider finished"))
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val BLOCK_DURATION_MS = 15_000L
    }
}
