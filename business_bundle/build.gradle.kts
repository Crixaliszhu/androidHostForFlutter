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
    // camera 包含 DataBinding 页面。使用 api 将其 DataBinding mapper 元数据继续暴露给最终 app，
    // 否则宿主的聚合 DataBinderMapper 找不到 camera 布局，inflate() 会在运行时返回 null。
    api(project(":camera"))
}
