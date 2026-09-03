plugins {
    // camera 是独立业务 library，由 business_bundle 聚合进最终宿主 APK。
    // 这样可以演示“功能模块自持页面和路由，宿主只依赖业务聚合层”的模块化接入方式。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // 复用项目里的 Compose 约定插件：统一 compileSdk、minSdk、Compose BOM 和测试依赖。
    id("androidCompose")
    // 复用项目里的 ARouter 约定插件：自动添加 arouter-api/compiler，并写入 AROUTER_MODULE_NAME。
    id("androidRouter")
}

android {
    // namespace 只决定 R、BuildConfig、Manifest 合并时的包空间，不等同于最终 applicationId。
    namespace = "com.example.camera"
    compileSdk = 35

    defaultConfig {
        // Camera2 API 从 Android 5.0 起可用；这里沿用项目约定，模块 minSdk 不主动拔高。
        targetSdk = 35

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // library 模块的混淆规则通过 consumerProguardFiles 传给最终 app。
        // 当前 Demo 没有反射保活需求，文件保留为空，便于后续扩展。
        consumerProguardFiles("consumer-rules.pro")
        javaCompileOptions {
            annotationProcessorOptions {
                // androidRouter 插件会在这里注入 AROUTER_MODULE_NAME。
            }
        }
    }

    buildFeatures {
        // 水印相机页面使用 XML DataBinding；原有 Camera2 教学页继续使用 Compose。
        dataBinding = true
    }
}
