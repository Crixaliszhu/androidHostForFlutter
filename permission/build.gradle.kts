plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("androidCompose")
    id("androidRouter")
}

android {
    namespace = "com.example.permission"
    compileSdk = 34

    defaultConfig {

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}