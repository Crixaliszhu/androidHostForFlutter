package com.example.hybriddemo.storage.room.db

import androidx.room.TypeConverter

/**
 * 类型转换器
 */
class CConverters {
    @TypeConverter
    fun fromListString(value: List<String>?): String? {
        if (value.isNullOrEmpty()) return null
        return appendStringByList(value, ",")
    }

    @TypeConverter
    fun string2List(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        return value.split(",").toList()
    }

    /**
     * 列表是否为空值
     *
     * @param list 列表
     * @return true为空值，false不为空值
     */
    fun isEmpty(list: List<*>?): Boolean {
        return list == null || list.isEmpty()
    }

    /**
     * 将字符列表，按照指定间隔符，拼接为字符串
     *
     * @param list   字符列表
     * @param symbol 间隔字符
     * @return 列表字符
     */
    @JvmOverloads
    fun appendStringByList(
        list: List<String?>,
        symbol: String? = ","
    ): String {
        if (isEmpty(list)) {
            return ""
        }
        val sb = StringBuffer()
        var i = 0
        val len = list.size
        while (i < len) {
            sb.append(list[i])
            if (i != list.size - 1) {
                sb.append(symbol)
            }
            i++
        }
        return sb.toString()
    }
}