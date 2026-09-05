plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // 复用项目里的 Compose 约定插件：统一 compileSdk、minSdk、Compose BOM 和测试依赖。
    id("androidCompose")
    // 复用项目里的 ARouter 约定插件：自动添加 arouter-api/compiler，并写入 AROUTER_MODULE_NAME。
    id("androidRouter")
}

android {
    namespace = "com.example.water_camera"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}