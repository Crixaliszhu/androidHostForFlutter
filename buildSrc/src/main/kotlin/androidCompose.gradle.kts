val composeCompileSdk = 35
val composeMinSdk = 24
val composeCompilerVersion = "1.5.8"
val composeBomVersion = "2024.09.00"

fun Any.invokeNoArg(methodName: String): Any {
    return javaClass.getMethod(methodName).invoke(this)
}

fun Any.setProperty(propertyName: String, value: Any) {
    val setterName = "set" + propertyName.replaceFirstChar { it.uppercaseChar() }
    val setter = javaClass.methods.firstOrNull {
        it.name == setterName && it.parameterTypes.size == 1
    } ?: error("未找到属性 $propertyName 的 setter: ${javaClass.name}")
    setter.invoke(this, value)
}

val androidExtension = extensions.findByName("android")
    ?: error("androidCompose 插件只能应用于 Android application/library 模块")

androidExtension.setProperty("compileSdk", composeCompileSdk)

val defaultConfig = androidExtension.invokeNoArg("getDefaultConfig")
defaultConfig.setProperty("minSdk", composeMinSdk)

val compileOptions = androidExtension.invokeNoArg("getCompileOptions")
compileOptions.setProperty("sourceCompatibility", JavaVersion.VERSION_17)
compileOptions.setProperty("targetCompatibility", JavaVersion.VERSION_17)

val buildFeatures = androidExtension.invokeNoArg("getBuildFeatures")
buildFeatures.setProperty("compose", true)

val composeOptions = androidExtension.invokeNoArg("getComposeOptions")
composeOptions.setProperty("kotlinCompilerExtensionVersion", composeCompilerVersion)

dependencies {
    add("implementation", "androidx.core:core-ktx:1.13.1")
    add("implementation", "androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    add("implementation", "androidx.activity:activity-compose:1.10.1")
    add("implementation", platform("androidx.compose:compose-bom:$composeBomVersion"))
    add("implementation", "androidx.compose.ui:ui")
    add("implementation", "androidx.compose.ui:ui-graphics")
    add("implementation", "androidx.compose.ui:ui-tooling-preview")
    add("implementation", "androidx.compose.material3:material3")

    add("testImplementation", "junit:junit:4.13.2")
    add("androidTestImplementation", "androidx.test.ext:junit:1.2.1")
    add("androidTestImplementation", "androidx.test.espresso:espresso-core:3.6.1")
    add("androidTestImplementation", platform("androidx.compose:compose-bom:$composeBomVersion"))
    add("androidTestImplementation", "androidx.compose.ui:ui-test-junit4")
    add("debugImplementation", "androidx.compose.ui:ui-tooling")
    add("debugImplementation", "androidx.compose.ui:ui-test-manifest")
}
