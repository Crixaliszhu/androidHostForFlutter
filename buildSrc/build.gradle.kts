plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // 约定插件运行时也需要 AGP 和 Kotlin 类型，与 Hilt 放在同一类路径，避免类加载失败。
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.51.1")
    implementation("com.android.tools.build:gradle:8.6.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
}
