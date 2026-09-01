package com.example.camera

/**
 * camera 模块对外暴露的路由路径。
 *
 * 本 Demo 采用“模块自己声明路由，宿主只知道路径”的方式：
 * - [CameraDemoActivity] 用 @Route 注册这个 path。
 * - app 模块的 DemoRouterServiceImpl 通过同一个 path 打开页面。
 * - business_bundle 依赖 camera 后，ARouter 编译产物会随 APK 一起打包。
 */
object CameraRouterPaths {
    const val CAMERA_DEMO = "/camera/main"
}
