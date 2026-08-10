cmake_minimum_required(VERSION 3.22.1)

project(quality_native_crash)

# 自研 Native 崩溃采集 so，只负责崩溃现场 tombstone 落盘，不依赖第三方平台。
add_library(
        quality_native_crash
        SHARED
        nativecrash/quality_native_crash.cpp
)

target_link_libraries(
        quality_native_crash
        # android/log 仅用于初始化阶段日志；真正信号处理路径避免调用复杂日志 API。
        android
        log
)
