import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import java.util.Locale

// 顶层 build.gradle.kts。
// 仅声明 plugin 版本，子模块用 `id("...")` 显式启用。
//
// AGP 版本说明：
//  - 8.2.2 只测到 compileSdk 34，配 compileSdk = 35 会报警。
//  - 8.6.0 起官方支持 compileSdk 35，需要 Gradle 8.7+（gradle-wrapper.properties 已就位）。
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("com.android.library") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    id("io.sentry.android.gradle") version "6.16.0" apply false
}

val detektConfigFile = rootProject.layout.projectDirectory.file("config/detekt/detekt.yml")

detekt {
    config.setFrom(detektConfigFile)
    buildUponDefaultConfig = false
    ignoreFailures = false
}

dependencies {
    detektPlugins(project(":customRules"))
}

fun Project.isLocalAndroidHostProject(): Boolean {
    val rootPath = rootProject.projectDir.toPath().normalize()
    val projectPath = projectDir.toPath().normalize()
    return projectPath.startsWith(rootPath)
}

subprojects {
    pluginManager.withPlugin("com.android.application") {
        if (!isLocalAndroidHostProject()) return@withPlugin

        extensions.configure<ApplicationExtension>("android") {
            lint {
                abortOnError = true
                checkDependencies = true
                checkReleaseBuilds = true
                htmlReport = true
                xmlReport = true
            }
        }
        configureProjectDetekt()
    }

    pluginManager.withPlugin("com.android.library") {
        if (!isLocalAndroidHostProject()) return@withPlugin

        extensions.configure<LibraryExtension>("android") {
            lint {
                abortOnError = true
                checkDependencies = true
                checkReleaseBuilds = true
                htmlReport = true
                xmlReport = true
            }
        }
        configureProjectDetekt()
    }
}

fun Project.configureProjectDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    dependencies.add("detektPlugins", rootProject.project(":customRules"))

    extensions.configure<DetektExtension>("detekt") {
        config.setFrom(rootProject.files(detektConfigFile))
        buildUponDefaultConfig = false
        ignoreFailures = false
        source.setFrom(
            files(
                "src/main/java",
                "src/main/kotlin",
                "src/test/java",
                "src/test/kotlin",
                "src/androidTest/java",
                "src/androidTest/kotlin",
            )
        )
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(true)
            sarif.required.set(false)
        }
    }
}

val detektChangedFilePaths = providers.gradleProperty("detektChangedFiles").orNull
    ?.split(',')
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.map { rootProject.file(it) }
    ?.filter { it.isFile }
    .orEmpty()

tasks.register<Detekt>("detektChanged") {
    group = "verification"
    description = "Runs detekt project rules only on staged Kotlin files passed by -PdetektChangedFiles."

    setSource(detektChangedFilePaths)
    include("**/*.kt", "**/*.kts")
    exclude("**/build/**")
    config.setFrom(detektConfigFile)
    buildUponDefaultConfig = false
    ignoreFailures = false
    jvmTarget = "17"

    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(true)
        sarif.required.set(false)
    }

    onlyIf {
        detektChangedFilePaths.isNotEmpty()
    }
}

tasks.register("createApiModule") {
    group = "scaffold"
    description = "Create a sibling _api module for a business module. Usage: ./gradlew createApiModule -Pmodule=app"

    doLast {
        val modulePath = providers.gradleProperty("module").orNull
            ?.toProjectPath()
            ?: error("请传入模块名，例如：./gradlew createApiModule -Pmodule=app 或 -Pmodule=:feature:recruitment")
        val apiProjectPath = modulePath.toApiProjectPath()
        val apiDir = rootDir.resolve(apiProjectPath.toRelativeDir())

        if (apiDir.exists()) {
            error("API 模块已存在: $apiProjectPath (${apiDir.absolutePath})")
        }

        val moduleName = modulePath.substringAfterLast(":")
        val pascalName = providers.gradleProperty("servicePrefix").orNull
            ?.toPascalCase()
            ?.takeIf { it.isNotBlank() }
            ?: moduleName.toPascalCase()
        val packageName = providers.gradleProperty("packageName").orNull
            ?.takeIf { it.isNotBlank() }
            ?: "com.example.${moduleName.toPackageSegment()}.api"
        val serviceName = providers.gradleProperty("serviceName").orNull
            ?.takeIf { it.isNotBlank() }
            ?: "I${pascalName}RouterService"
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

        val settingsFile = rootDir.resolve("settings.gradle.kts")
        val includeLine = """include("$apiProjectPath")"""
        val settingsText = settingsFile.readText()
        if (!settingsText.contains(includeLine)) {
            settingsFile.writeText(settingsText.trimEnd() + "\n$includeLine\n")
        }

        println(
            """
            已创建 API 模块: $apiProjectPath
            目录: ${apiDir.absolutePath}
            接口: $packageName.$serviceName
            服务路径: $servicePath
            """.trimIndent()
        )
    }
}

fun String.toProjectPath(): String {
    val normalized = trim().replace('\\', ':').replace('/', ':').trim(':')
    require(normalized.isNotBlank()) { "module 不能为空" }
    return ":$normalized"
}

fun String.toApiProjectPath(): String {
    val parts = trim(':').split(':').toMutableList()
    require(parts.isNotEmpty()) { "module 不能为空" }
    parts[parts.lastIndex] = "${parts.last()}_api"
    return ":${parts.joinToString(":")}"
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
        .takeIf { it.isNotBlank() }
        ?: "module"
}

fun String.toPathSegment(): String {
    return lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
        .takeIf { it.isNotBlank() }
        ?: "module"
}

fun String.toConstantPrefix(): String {
    return replace(Regex("([a-z])([A-Z])"), "$1_$2")
        .replace(Regex("[^A-Za-z0-9]+"), "_")
        .trim('_')
        .uppercase(Locale.US)
        .takeIf { it.isNotBlank() }
        ?: "MODULE"
}
