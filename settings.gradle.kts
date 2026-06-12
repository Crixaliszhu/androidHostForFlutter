import java.util.Properties

// =====================================================================
// FlutterHybridDemo / android_host
// 演示原生侧如何把同级 flutter_module 当 Gradle 子工程引入（本地源码模式）。
// 对应 recruitment_android/settings.gradle.kts 的 Flutter 引入逻辑。
// =====================================================================

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Flutter 引擎产物所在的官方镜像
        maven { setUrl("https://storage.googleapis.com/download.flutter.io") }
    }
}

dependencyResolutionManagement {
    // 与生产代码 recruitment_android/settings.gradle.kts 对齐：
    // 用 PREFER_SETTINGS 而不是 FAIL_ON_PROJECT_REPOS。
    // 因为 Flutter Module 内部的 .android/Flutter/build.gradle 会自己 maven { ... }，
    // FAIL_ON_PROJECT_REPOS 会直接报错：
    //   Build was configured to prefer settings repositories over project repositories
    //   but repository 'maven' was added by plugin 'dev.flutter.flutter-gradle-plugin'
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://storage.googleapis.com/download.flutter.io") }

        // 1) Flutter Module 在 flutter_module/build/host/outputs/repo 下产出 Pigeon plugin AAR。
        //    本地源码模式不一定需要，但加上没坏处。
        val flutterDir = settings.flutterProjectDir()
        maven { setUrl("$flutterDir/build/host/outputs/repo") }
    }
}

rootProject.name = "FlutterHybridDemo"

include(":app")
include(":flutter_engine")
include(":flutter_biz")

// 2) 把 flutter_module 当成 Gradle 子工程引入。
//    Flutter SDK 在 `<module>/.android/include_flutter.groovy` 里提供了这个胶水脚本，
//    它会注册 `:flutter` 项目和插件项目。
//    第一次需要在 flutter_module 跑 `flutter pub get`，让 .android/ 目录生成。
val includeFlutterScript = File(settings.flutterProjectDir(), ".android/include_flutter.groovy")
if (includeFlutterScript.exists()) {
    apply(from = includeFlutterScript)
} else {
    logger.warn(
        "[FlutterHybridDemo] 未找到 ${includeFlutterScript.absolutePath}，" +
        "请先在 flutter_module/ 目录执行 `flutter pub get`。"
    )
}

fun Settings.flutterProjectDir(): String {
    val localProps = Properties().apply {
        val f = File(rootDir, "local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    return localProps.getProperty("flutter.project.dir")
        ?: error("[FlutterHybridDemo] 请在 local.properties 中配置 flutter.project.dir")
}
