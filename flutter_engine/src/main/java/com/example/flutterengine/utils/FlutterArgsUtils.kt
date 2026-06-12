package com.example.flutterengine.utils

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.util.UUID

/**
 * Dart 入口参数工具。
 *
 * 引擎创建时通过 `FlutterEngineGroup.Options.setDartEntrypointArgs(...)` 把这里
 * 拼出来的 JSON 一并传给 Flutter，对应 Flutter 端 `EntryArgs.parse`。
 *
 * 对应生产代码 `FlutterArgsUtils`。
 */
object FlutterArgsUtils {

    fun createEntryArgs(context: Context): List<String> {
        val pkg = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        val json = JSONObject().apply {
            put("env", if (isDebuggable(context)) "DEBUG" else "RELEASE")
            put("packageName", context.packageName)
            put("appVersion", pkg?.versionName ?: "")
            put("buildVersion", pkg?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toString()
                else @Suppress("DEPRECATION") it.versionCode.toString()
            } ?: "")
        }
        return listOf(json.toString())
    }

    fun createUUID(): String = UUID.randomUUID().toString().replace("-", "")

    private fun isDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
