import java.util.Properties
import java.util.Locale


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
    plugins {
        id("com.android.application") version "8.6.0"
        id("com.android.library") version "8.6.0"
        id("org.jetbrains.kotlin.android") version "1.9.22"
        id("org.jetbrains.kotlin.jvm") version "1.9.22"
        id("io.gitlab.arturbosch.detekt") version "1.23.7"
        id("io.sentry.android.gradle") version "6.16.0"
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
        // 自研 quality_monitor 已迁移到独立 ServiceModule 项目维护；默认读取其本地 Maven 发布目录。
        val qualityMonitorRepoUrl = settings.gradleLocalProperty("QUALITY_MONITOR_REPO_URL")
            ?: System.getenv("QUALITY_MONITOR_REPO_URL")
            ?: "${rootDir}/../servicemodule/quality_monitor/build/repo"
        maven { setUrl(uri(qualityMonitorRepoUrl)) }
        // AndroidGodEye 3.x 发布在 JCenter；Bintray/JCenter 关闭后，国内镜像更稳定。
        maven { setUrl("https://maven.aliyun.com/repository/jcenter") }
        // AndroidGodEye 的部分历史依赖仍托管在 JCenter，仅在开发期 debug 包使用。
        @Suppress("DEPRECATION")
        jcenter()
        maven { setUrl("https://storage.googleapis.com/download.flutter.io") }

        // 1) Flutter Module 在 flutter_module/build/host/outputs/repo 下产出 Pigeon plugin AAR。
        //    本地源码模式不一定需要，但加上没坏处。
        val flutterDir = settings.flutterProjectDir()
        maven { setUrl("$flutterDir/build/host/outputs/repo") }
    }
}

rootProject.name = "FlutterHybridDemo"

include(":app")
// 马甲包演示模块：复用主 app 的源码和资源，只替换 applicationId、应用名等外壳配置。
include(":app_vest")
include(":app_api")
include(":customRules")
include(":router")
include(":flutter_engine")
include(":flutter_biz")
include(":recruit")
include(":recruit_api")
include(":resume")
include(":resume_api")
include(":business_bundle")
include(":camera")
include(":widget")
include(":water_camera")
include(":permission")
include(":local_mmkv")

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

fun Settings.gradleLocalProperty(name: String): String? {
    val file = File(rootDir, "local.properties")
    if (!file.exists()) return null
    return Properties().apply { file.inputStream().use { load(it) } }
        .getProperty(name)
        ?.takeIf { it.isNotBlank() }
}

// 约定：开发者只需要在 settings.gradle.kts 中 include(":module_api")。
// Sync 前自动补齐缺失的 API 模块目录和基础源码，已有目录不会被覆盖。
val settingsIncludes = File(rootDir, "settings.gradle.kts")
    .readLines()
    .filterNot { it.trimStart().startsWith("//") }
    .joinToString("\n")
Regex("include\\(([^)]*)\\)")
    .findAll(settingsIncludes)
    .flatMap { match ->
        Regex("[\\\"'](:[^\\\"']+)[\\\"']")
            .findAll(match.groupValues[1])
            .map { it.groupValues[1] }
    }
    .filter { it.substringAfterLast(":").endsWith("_api") }
    .forEach { ensureApiModule(it) }

fun ensureApiModule(apiProjectPath: String) {
    val apiName = apiProjectPath.substringAfterLast(":")
    val moduleName = apiName.removeSuffix("_api")
    require(moduleName.isNotBlank()) { "API 模块名不能为空: $apiProjectPath" }

    val apiDir = rootDir.resolve(apiProjectPath.toRelativeDir())
    if (apiDir.exists()) return

    val pascalName = moduleName.toPascalCase()
    val packageName = "com.example.${moduleName.toPackageSegment()}.api"
    val serviceName = "I${pascalName}RouterService"
    val pathsName = "${pascalName}RouterApiPaths"
    val servicePath = "/${moduleName.toPathSegment()}_api/service/${moduleName.toPathSegment()}-router"
    val packageDir = apiDir.resolve("src/main/java/${packageName.replace('.', '/')}")

    packageDir.mkdirs()
    apiDir.resolve("src/main").mkdirs()
    apiDir.resolve("build.gradle.kts").writeText(
        """
        plugins {
            id("com.android.library")
            id("org.jetbrains.kotlin.android")
        }

        android {
            namespace = "$packageName"
            compileSdk = 35

            defaultConfig {
                minSdk = 24
                consumerProguardFiles("consumer-rules.pro")
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            kotlinOptions {
                jvmTarget = "17"
            }
        }

        dependencies {
            api("com.alibaba:arouter-api:1.5.2")
        }
        """.trimIndent() + "\n"
    )
    apiDir.resolve("consumer-rules.pro").writeText("")
    apiDir.resolve("src/main/AndroidManifest.xml").writeText(
        """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest />
        """.trimIndent() + "\n"
    )
    packageDir.resolve("$pathsName.kt").writeText(
        """
        package $packageName

        object $pathsName {
            const val ${moduleName.toConstantPrefix()}_ROUTER_SERVICE = "$servicePath"
        }
        """.trimIndent() + "\n"
    )
    packageDir.resolve("$serviceName.kt").writeText(
        """
        package $packageName

        import android.content.Context
        import com.alibaba.android.arouter.facade.template.IProvider

        interface $serviceName : IProvider {
            fun open(context: Context)
        }
        """.trimIndent() + "\n"
    )

    logger.lifecycle("[FlutterHybridDemo] 已自动创建 API 模块: $apiProjectPath (${apiDir.absolutePath})")
}

fun String.toRelativeDir(): String = trim(':').replace(':', File.separatorChar)

fun String.toPascalCase(): String {
    return split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString("") { part ->
            part.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
            }
        }
}

fun String.toPackageSegment(): String {
    return lowercase(Locale.US).replace(Regex("[^a-z0-9_]"), "")
        .takeIf { it.isNotBlank() } ?: "module"
}

fun String.toPathSegment(): String {
    return lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "").trim('-')
        .takeIf { it.isNotBlank() } ?: "module"
}

fun String.toConstantPrefix(): String {
    return replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .replace(Regex("[^A-Za-z0-9]+"), "_")
        .trim('_')
        .uppercase(Locale.US)
        .takeIf { it.isNotBlank() } ?: "MODULE"
}
