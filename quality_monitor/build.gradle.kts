plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.qualitymonitor"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        externalNativeBuild {
            cmake {
                // Native crash dumper 使用 C++17，后续扩展寄存器解析和符号格式化时保持语法一致。
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            // 第一阶段 Native 崩溃采集依赖本模块内置 so，CMake 配置必须随模块一起维护。
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
