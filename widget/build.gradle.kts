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
}

dependencies {
    implementation("androidx.compose.material:material-icons-core")
}
