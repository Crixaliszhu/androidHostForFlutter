import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun sentryProperty(name: String): String {
    return localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
        ?: ""
}

android {
    namespace = "com.example.hybriddemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hybriddemo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "SENTRY_DSN", "\"${sentryProperty("SENTRY_DSN")}\"")
        buildConfigField("String", "SENTRY_ENVIRONMENT", "\"debug-local\"")
        buildConfigField("boolean", "SENTRY_DEBUG", "true")
        vectorDrawables {
            useSupportLibrary = true
        }

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += "room.schemaLocation" to "$projectDir/schemas"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            resValue("bool", "android_god_eye_manual_install", "false")
            // GodEye 的 leakcanary 插件会手动安装 AppWatcher；关闭 LeakCanary 自带
            // ContentProvider 自动安装，避免启动时出现 "AppWatcher already installed"。
            resValue("bool", "leak_canary_watcher_auto_install", "false")
            // AndroidGodEye 3.x 的通知服务没有声明 foregroundServiceType，
            // targetSdk 34+ 设备上启动前台服务会崩溃；开发阶段直接关闭通知入口，
            // 仍可通过 adb forward + http://localhost:5390/index.html 查看 Web Monitor。
            resValue("bool", "android_god_eye_need_notification", "false")
            resValue("integer", "android_god_eye_monitor_port", "5390")
            resValue(
                "string",
                "android_god_eye_install_assets_path",
                "android-godeye-config/install.config"
            )
        }
        getByName("release") {
            isMinifyEnabled = false
            resValue("bool", "android_god_eye_manual_install", "true")
            resValue("bool", "android_god_eye_need_notification", "false")
            resValue("integer", "android_god_eye_monitor_port", "5390")
            resValue(
                "string",
                "android_god_eye_install_assets_path",
                "android-godeye-config/install.config"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

configurations.configureEach {
    resolutionStrategy {
        // AndroidGodEye 3.4.3 传递的是 LeakCanary 2.2：
        // - Android 12+ 创建 PendingIntent 需要 FLAG_IMMUTABLE / FLAG_MUTABLE
        // - Android 14+ 启动 ForegroundService 需要 foregroundServiceType
        // 统一提升 LeakCanary/Shark 到新版 2.x，保留 GodEye 对分析结果的监听。
        force(
            "com.squareup.leakcanary:leakcanary-android:2.14",
            "com.squareup.leakcanary:leakcanary-android-core:2.14",
            "com.squareup.leakcanary:leakcanary-object-watcher:2.14",
            "com.squareup.leakcanary:leakcanary-object-watcher-android:2.14",
            "com.squareup.leakcanary:leakcanary-object-watcher-android-androidx:2.14",
            "com.squareup.leakcanary:shark:2.14",
            "com.squareup.leakcanary:shark-android:2.14",
            "com.squareup.leakcanary:shark-graph:2.14",
            "com.squareup.leakcanary:shark-hprof:2.14",
            "com.squareup.leakcanary:shark-log:2.14",
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.tencent:mmkv-static:1.3.16")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.sentry:sentry-android:8.51.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    val androidGodEyeVersion = "3.4.3"
    debugImplementation("cn.hikyson.godeye:godeye-core:$androidGodEyeVersion")
    debugImplementation("cn.hikyson.godeye:godeye-monitor:$androidGodEyeVersion")
    debugImplementation("cn.hikyson.godeye:godeye-xcrash:$androidGodEyeVersion")
    debugImplementation("cn.hikyson.godeye:godeye-leakcanary:$androidGodEyeVersion")

    kapt("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    implementation(project(":flutter_engine"))
    implementation(project(":flutter_biz"))
}
