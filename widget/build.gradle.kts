plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("androidCompose")
}

android {
    namespace = "com.example.widget"

    defaultConfig {
        targetSdk = 35
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        // widget 提供 XML BindingAdapter，必须生成 Data Binding 适配代码。
        dataBinding = true
    }
}

dependencies {
    implementation("androidx.compose.material:material-icons-core")
}
