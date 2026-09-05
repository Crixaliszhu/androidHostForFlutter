package com.example.local_mmkv

import android.text.TextUtils
import android.util.Log
import com.example.local_mmkv.annotation.MethodDelete
import com.example.local_mmkv.annotation.MethodGet
import com.example.local_mmkv.annotation.MethodSave
import com.example.local_mmkv.annotation.PartKey
import com.example.local_mmkv.annotation.Value
import com.example.local_mmkv.annotation.ValueDefault
import com.example.local_mmkv.core.MMKVCore
import com.tencent.mmkv.MMKV
import java.lang.reflect.Method
import kotlin.jvm.Throws
import kotlin.reflect.KClass

class MethodExecutorKt(private val method: Method, private val args: Array<Any>) {

    companion object {
        private const val TAG = "MethodExecutorKt"
    }

    // 返回值类型
    private val returnType: KClass<*> = method.returnType.kotlin

    // 方法的注解
    private val methodAnnotations: Array<Annotation> = method.declaredAnnotations

    // 参数注解
    private val argsAnnotations: Array<Array<Annotation>> = method.parameterAnnotations

    // key值
    private val keyName: String

    // kv文件名
    private var storageId: String? = null

    private val mmkv: MMKV
        get() = if (TextUtils.isEmpty(storageId)) MMKV.defaultMMKV() else MMKV.mmkvWithID(storageId)

    init {
        initStorageId()
        keyName = initKeyName().toString()
    }

    /**
     * kv对象解析函数
     */
    fun execute(): Any? {
        for (annotation in methodAnnotations) {
            if (annotation is MethodSave) {
                handleSaveAnnotation()
                break
            } else if (annotation is MethodGet) {
                return handleGetAnnotation()
            } else if (annotation is MethodDelete) {
                handleDeleteAnnotation()
                break
            }
        }
        return null
    }

    private fun handleDeleteAnnotation() {
        MMKVCore.deleteKey(mmkv, keyName)
    }

    /**
     * 保存注解处理函数，仅第一个value注解有效
     */
    @Throws(Exception::class)
    private fun handleSaveAnnotation() {
        var isFoundValue = false
        for (i in argsAnnotations.indices) {
            for (annotation in argsAnnotations[i]) {
                if (annotation is Value) {
                    isFoundValue = true
                    executeSave(args[i])
                    break
                }
            }
            if (isFoundValue) {
                break
            }
        }
        if (!isFoundValue) {
            throw RuntimeException("`value` annotation not found !!!")
        }
    }

    /**
     * 获取数据注解处理函数
     */
    private fun handleGetAnnotation(): Any? {
        var defaultValue: Any? = null
        for (i in argsAnnotations.indices) {
            for (annotation in argsAnnotations[i]) {
                if (annotation is ValueDefault) {
                    defaultValue = args[i]
                    break
                }
            }
            if (defaultValue != null) {
                break
            }
        }
        return executeGet(defaultValue)
    }

    /**
     * 执行保存操作
     */
    private fun executeSave(value: Any) {
        MMKVCore.put(mmkv, keyName, value)
    }

    /**
     * 执行数据获取操作
     */
    private fun executeGet(defaultValue: Any?): Any? {
        require(returnType != Void::class) { "must have return type !!!" }
        return MMKVCore.get(mmkv, keyName, defaultValue, null, returnType)
    }


    /**
     * 以类名作为kv文件名
     */
    private fun initStorageId() {
        val packages = method.declaringClass.`package`
        if (packages != null) {
            val idBuilder = StringBuilder()
            val packageName = packages.name.split(".").toTypedArray()
            for (i in packageName.indices) {
                if (i >= 3) {
                    break
                }
                if (i > 0) {
                    idBuilder.append(".")
                }
                idBuilder.append(packageName[i])
            }
            storageId = idBuilder.toString()
        } else {
            storageId = ""
        }
        Log.e(TAG, "storageId 值计算 = $storageId")
    }

    /**
     * key值由所有PartKey拼接而成
     */
    private fun initKeyName(): StringBuilder {
        val keyNameBuilder = StringBuilder(method.declaringClass.simpleName)
        for (i in argsAnnotations.indices) {
            for (annotation in argsAnnotations[i]) {
                if (annotation is PartKey) {
                    val arg = args[i]
                    if (arg is String) {
                        keyNameBuilder.append("_").append(arg)
                    } else {
                        throw IllegalArgumentException("the type of `PartKey` must be `java.lang.String` when use mmkv ")
                    }
                }
            }
        }
        Log.e(TAG, "key 值计算 = ${keyNameBuilder.toString()}")
        return keyNameBuilder
    }
}