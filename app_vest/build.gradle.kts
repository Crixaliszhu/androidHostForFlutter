import com.android.build.gradle.internal.api.BaseVariantOutputImpl
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

fun localOrEnvProperty(name: String): String {
    return localProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
        ?: ""
}

val vestVersionName = "1.0.0"
val vestVersionCode = 10000
val vestApplicationId = "com.example.hybriddemo.vest"
val vestArchiveBaseName = "AndroidHostVest-v$vestVersionName-$vestVersionCode"

android {
    // namespace 决定 R、BuildConfig 等生成代码所在包。这里保持主工程源码包不变，
    // 否则复用 app/src/main/java 时，显式 import com.example.hybriddemo.BuildConfig 的代码会编译失败。
    namespace = "com.example.hybriddemo"
    compileSdk = 35

    defaultConfig {
        // applicationId 才是安装到手机上的真实包名。马甲包通过它和主包共存。
        applicationId = vestApplicationId
        manifestPlaceholders["appLabel"] = "马甲Flutter"
        minSdk = 24
        targetSdk = 35
        versionCode = vestVersionCode
        versionName = vestVersionName

        buildConfigField("String", "SENTRY_DSN", "\"${localOrEnvProperty("VEST_SENTRY_DSN")}\"")
        buildConfigField("String", "QUALITY_MONITOR_UPLOAD_URL", "\"${localOrEnvProperty("VEST_QUALITY_MONITOR_UPLOAD_URL")}\"")
        buildConfigField("boolean", "SELF_ANR_ENABLED", "true")

        vectorDrawables {
            useSupportLibrary = true
        }

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += "room.schemaLocation" to "$projectDir/schemas"
            }
        }
    }

    sourceSets {
        getByName("main") {
            // 马甲包复用主 app 的代码、Manifest、资源和 assets，再用 app_vest/src/main/res 覆盖外壳资源。
            manifest.srcFile("../app/src/main/AndroidManifest.xml")
            java.srcDirs("../app/src/main/java")
            res.srcDirs("../app/src/main/res", "src/main/res")
            assets.srcDirs("../app/src/main/assets")
        }
        getByName("debug") {
            manifest.srcFile("../app/src/debug/AndroidManifest.xml")
            java.srcDirs("../app/src/debug/java")
        }
        getByName("release") {
            java.srcDirs("../app/src/release/java")
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            buildConfigField("String", "SENTRY_ENVIRONMENT", "\"vest-debug\"")
            buildConfigField("boolean", "SENTRY_DEBUG", "true")
            buildConfigField("double", "SENTRY_TRACES_SAMPLE_RATE", "1.0")
            buildConfigField("double", "SENTRY_PROFILES_SAMPLE_RATE", "1.0")
            buildConfigField("boolean", "SENTRY_ATTACH_SCREENSHOT", "true")
            buildConfigField("boolean", "SENTRY_ATTACH_VIEW_HIERARCHY", "true")
            buildConfigField("boolean", "SENTRY_REPORT_ANR_IN_DEBUG", "true")
            buildConfigField("boolean", "QUALITY_MONITOR_ENABLED", "true")
            resValue("bool", "android_god_eye_manual_install", "false")
            resValue("bool", "leak_canary_watcher_auto_install", "false")
            resValue("bool", "android_god_eye_need_notification", "false")
            resValue("integer", "android_god_eye_monitor_port", "5391")
            resValue("string", "android_god_eye_install_assets_path", "android-godeye-config/install.config")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "../app/proguard-rules.pro"
            )
            buildConfigField("String", "SENTRY_ENVIRONMENT", "\"vest-production\"")
            buildConfigField("boolean", "SENTRY_DEBUG", "false")
            buildConfigField("double", "SENTRY_TRACES_SAMPLE_RATE", "0.05")
            buildConfigField("double", "SENTRY_PROFILES_SAMPLE_RATE", "0.01")
            buildConfigField("boolean", "SENTRY_ATTACH_SCREENSHOT", "false")
            buildConfigField("boolean", "SENTRY_ATTACH_VIEW_HIERARCHY", "false")
            buildConfigField("boolean", "SENTRY_REPORT_ANR_IN_DEBUG", "false")
            buildConfigField("boolean", "QUALITY_MONITOR_ENABLED", "true")
            resValue("bool", "android_god_eye_manual_install", "true")
            resValue("bool", "android_god_eye_need_notification", "false")
            resValue("integer", "android_god_eye_monitor_port", "5391")
            resValue("string", "android_god_eye_install_assets_path", "android-godeye-config/install.config")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

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

android.applicationVariants.all {
    outputs.all {
        val variantName = name
        (this as BaseVariantOutputImpl).outputFileName = "$vestArchiveBaseName-$variantName.apk"
    }
}

configurations.configureEach {
    resolutionStrategy {
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
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.tencent:mmkv-static:1.3.16")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.sentry:sentry-android:8.51.0")

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
    implementation(project(":quality_monitor"))
}
