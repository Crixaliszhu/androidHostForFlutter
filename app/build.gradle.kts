import java.util.Properties
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("io.sentry.android.gradle")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

// Sentry 相关私密值优先从 local.properties 读取，CI 构建时也可以用环境变量覆盖。
// 这样 DSN、Auth Token、采样率都不会被写死到仓库里，debug/release 也能复用同一套配置入口。
fun sentryProperty(name: String): String {
    return localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
        ?: ""
}

fun selfMonitorProperty(name: String): String {
    // 自研监控与 Sentry 使用不同前缀，方便后续在 CI 中单独灰度或切换内部上报域名。
    return localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
        ?: ""
}

fun qualityMonitorDependencyNotation(): String {
    // quality_monitor 已迁移到独立 ServiceModule 项目，宿主 Demo 始终通过 Maven AAR 接入。
    val groupId = selfMonitorProperty("QUALITY_MONITOR_GROUP_ID").ifBlank { "com.example.qualitymonitor" }
    val artifactId = selfMonitorProperty("QUALITY_MONITOR_ARTIFACT_ID").ifBlank { "quality-monitor" }
    val version = selfMonitorProperty("QUALITY_MONITOR_VERSION").ifBlank { "1.0.0-SNAPSHOT" }
    return "$groupId:$artifactId:$version"
}

// SENTRY_AUTH_TOKEN 只用于构建期上传 R8/ProGuard mapping.txt，不会被打进 APK。
// 如果没有配置 token，正式包仍可构建，只是 Sentry 后台无法自动还原混淆后的崩溃栈。
val sentryAuthToken = sentryProperty("SENTRY_AUTH_TOKEN")
val sentryOrg = sentryProperty("SENTRY_ORG").ifBlank { "crixalis" }
val sentryProject = sentryProperty("SENTRY_PROJECT").ifBlank { "android" }

// App 版本号集中在这里维护，避免 defaultConfig、APK 文件名、Sentry release 各写一份导致不一致。
// SentryInitializer 会使用 BuildConfig.VERSION_NAME / VERSION_CODE 生成 release：
// com.example.hybriddemo@1.0.2+261020。
val appVersionName = "1.0.3"
val appVersionCode = 261030
val appArchiveBaseName = "AndroidHostForFlutter-v$appVersionName-$appVersionCode"

// Release 签名同样优先从 local.properties 读取，CI 中可改用环境变量。
// 密钥文件和密码都属于高敏感信息，只能保存在本机安全目录或 CI Secret，不能提交到仓库。
fun releaseSigningProperty(name: String): String {
    return localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
        ?: ""
}

val releaseStoreFilePath = releaseSigningProperty("RELEASE_STORE_FILE")
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseSigningProperty("RELEASE_STORE_PASSWORD"),
    releaseSigningProperty("RELEASE_KEY_ALIAS"),
    releaseSigningProperty("RELEASE_KEY_PASSWORD"),
).all { it.isNotBlank() }

android {
    namespace = "com.example.hybriddemo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hybriddemo"
        manifestPlaceholders["appLabel"] = "@string/app_name"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        // DSN 是 Sentry 项目的公开写入地址。这里通过 BuildConfig 暴露给运行时代码，
        // AndroidManifest 中已关闭 Sentry 自动初始化，避免 Provider 在 BuildConfig 可用前读取不到 DSN。
        buildConfigField("String", "SENTRY_DSN", "\"${sentryProperty("SENTRY_DSN")}\"")
        // 第一阶段自研质量监控统一上报地址，覆盖崩溃、ANR、启动和页面性能事件。
        buildConfigField("String", "QUALITY_MONITOR_UPLOAD_URL", "\"${selfMonitorProperty("QUALITY_MONITOR_UPLOAD_URL")}\"")
        vectorDrawables {
            useSupportLibrary = true
        }
        resourceConfigurations.addAll(listOf("zh","zh-rCN"))
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += "room.schemaLocation" to "$projectDir/schemas"
                arguments += "AROUTER_MODULE_NAME" to project.name
            }
        }

        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }

        splits {
            abi {
                isEnable = true
                reset()
                include("armeabi-v7a","arm64-v8a")
                isUniversalApk = true
            }
        }
        packaging {
            resources {
                excludes.add("META-INF/*.kotlin_module")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 开发阶段使用独立环境，避免调试崩溃、手动卡顿、ANR Demo 污染线上指标。
            buildConfigField("String", "SENTRY_ENVIRONMENT", "\"debug-local\"")
            buildConfigField("boolean", "SENTRY_DEBUG", "true")
            // Debug 全量采样，方便在 Sentry 的 Traces / Profiles 中立即看到每次点击和页面加载。
            buildConfigField("double", "SENTRY_TRACES_SAMPLE_RATE", "1.0")
            buildConfigField("double", "SENTRY_PROFILES_SAMPLE_RATE", "1.0")
            // Debug 开启截图和 ViewHierarchy，便于学习事件现场；线上默认关闭，避免隐私和体积风险。
            buildConfigField("boolean", "SENTRY_ATTACH_SCREENSHOT", "true")
            buildConfigField("boolean", "SENTRY_ATTACH_VIEW_HIERARCHY", "true")
            // Sentry 默认不在 debug 构建中上报 ANR，这里为了 Demo 明确打开。
            buildConfigField("boolean", "SENTRY_REPORT_ANR_IN_DEBUG", "true")
            buildConfigField("boolean", "SELF_ANR_ENABLED", "true")
            // Debug 默认打开自研质量监控，便于本地验证崩溃、ANR、启动和页面事件是否落盘。
            buildConfigField("boolean", "QUALITY_MONITOR_ENABLED", "true")
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
            // 正式包开启 R8 混淆和资源压缩，用于接近线上真实体积、堆栈和性能表现。
            // mapping.txt 会由 Sentry Gradle Plugin 在构建后上传，用于后台还原混淆栈。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 线上统一使用 production，Sentry 后台的 Issues、Releases、Alerts 都按这个环境筛选。
            buildConfigField("String", "SENTRY_ENVIRONMENT", "\"production\"")
            buildConfigField("boolean", "SENTRY_DEBUG", "false")
            // Trace 采样率控制 Performance/Traces 数据量；默认 5%，可在 local.properties 或 CI 中调整。
            buildConfigField("double", "SENTRY_TRACES_SAMPLE_RATE", sentryProperty("SENTRY_TRACES_SAMPLE_RATE").ifBlank { "0.05" })
            // Profile 采样率是在已采样 Trace 的基础上继续抽样；默认 1%，避免线上 CPU profiling 成本过高。
            buildConfigField("double", "SENTRY_PROFILES_SAMPLE_RATE", sentryProperty("SENTRY_PROFILES_SAMPLE_RATE").ifBlank { "0.01" })
            // 线上默认不附带截图和 ViewHierarchy。若业务明确允许，再通过构建参数小流量开启。
            buildConfigField("boolean", "SENTRY_ATTACH_SCREENSHOT", sentryProperty("SENTRY_ATTACH_SCREENSHOT").ifBlank { "false" })
            buildConfigField("boolean", "SENTRY_ATTACH_VIEW_HIERARCHY", sentryProperty("SENTRY_ATTACH_VIEW_HIERARCHY").ifBlank { "false" })
            buildConfigField("boolean", "SENTRY_REPORT_ANR_IN_DEBUG", "false")
            buildConfigField("boolean", "SELF_ANR_ENABLED", "true")
            // Release 默认打开采集，真实上报地址仍由 local.properties 或 CI Secret 控制。
            buildConfigField("boolean", "QUALITY_MONITOR_ENABLED", "true")
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

    signingConfigs {
        create("release") {
            // storeFile 支持绝对路径，也支持相对项目根目录的路径。
            // 推荐使用绝对路径或放在项目外部目录，降低误提交 keystore 的风险。
            if (releaseStoreFilePath.isNotBlank()) {
                storeFile = file(releaseStoreFilePath)
            }
            storePassword = releaseSigningProperty("RELEASE_STORE_PASSWORD")
            keyAlias = releaseSigningProperty("RELEASE_KEY_ALIAS")
            keyPassword = releaseSigningProperty("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            // 只有四个签名参数都存在时才绑定 release 签名。
            // 这样没有 keystore 的新环境仍可执行 assembleRelease 验证 R8/mapping 产物，
            // 但真正安装或分发线上包前，必须补齐 local.properties/CI Secret。
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// 让 assembleDebug / assembleRelease 生成的 APK 文件名携带版本号，方便测试包、正式包归档和回溯。
// 示例：AndroidHostForFlutter-v1.0.2-261020-release.apk。
// 注意：这里改的是 APK 输出名；如果后续使用 bundleRelease 生成 AAB，需要再单独配置 AAB 归档命名。
android.applicationVariants.all {
    outputs.all {
        val variantName = name
        (this as BaseVariantOutputImpl).outputFileName = "$appArchiveBaseName-$variantName.apk"
    }
}

sentry {
    // 这三个值决定 mapping.txt 上传到哪个 Sentry 组织和项目。
    // 本 Demo 对应 https://crixalis.sentry.io/projects/android/。
    org.set(sentryOrg)
    projectName.set(sentryProject)
    authToken.set(sentryAuthToken)

    // includeProguardMapping 负责把 R8 mapping 作为构建产物交给 Sentry 插件；
    // autoUploadProguardMapping 只有在配置 token 时才上传，避免本地无 token 构建失败。
    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(sentryAuthToken.isNotBlank())
    // Source Context 会上传源码片段，线上通常关闭，避免源码或敏感业务逻辑暴露到第三方平台。
    includeSourceContext.set(false)
    // 关闭构建插件遥测，不影响 Sentry 崩溃、ANR、性能数据上报。
    telemetry.set(false)

    // 当前 Demo 没有接入 native so 符号表上传；后续如引入 NDK/Flutter native symbols 再单独开启。
    uploadNativeSymbols.set(false)
    includeNativeSources.set(false)
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.activity:activity-ktx:1.8.1")
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
    implementation("com.alibaba:arouter-api:1.5.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    val androidGodEyeVersion = "3.4.3"
    debugImplementation("cn.hikyson.godeye:godeye-core:$androidGodEyeVersion")
    debugImplementation("cn.hikyson.godeye:godeye-monitor:$androidGodEyeVersion")
    debugImplementation("cn.hikyson.godeye:godeye-xcrash:$androidGodEyeVersion")
    debugImplementation("cn.hikyson.godeye:godeye-leakcanary:$androidGodEyeVersion")

    kapt("androidx.room:room-compiler:2.6.1")
    kapt("com.alibaba:arouter-compiler:1.5.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    implementation(project(":app_api"))
    implementation(project(":router"))
    implementation(project(":flutter_engine"))
    implementation(project(":flutter_biz"))
    implementation(project(":recruit_api"))
    implementation(project(":recruit"))
    implementation(project(":resume_api"))
    // 第一阶段自研质量监控总入口，当前通过 ServiceModule 发布的 Maven AAR 接入。
    implementation(qualityMonitorDependencyNotation())
}
