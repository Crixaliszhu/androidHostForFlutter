plugins {
    id("com.android.library")
}

android {
    namespace = "com.example.businessbundle"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    // 应用组装层：把业务实现及其 ARouter 路由表带入最终 APK。
    implementation(project(":recruit"))
    implementation(project(":resume"))
    implementation(project(":camera"))
}
