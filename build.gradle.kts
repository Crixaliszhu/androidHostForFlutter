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
}
