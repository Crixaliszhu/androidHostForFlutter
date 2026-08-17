pluginManager.apply("org.jetbrains.kotlin.kapt")

val androidExtension = extensions.findByName("android")
    ?: error("androidRouter 插件只能应用于 Android application/library 模块")

fun Any.invokeNoArg(methodName: String): Any {
    return javaClass.getMethod(methodName).invoke(this)
}

val defaultConfig = androidExtension.invokeNoArg("getDefaultConfig")
val javaCompileOptions = defaultConfig.invokeNoArg("getJavaCompileOptions")
val annotationProcessorOptions = javaCompileOptions.invokeNoArg("getAnnotationProcessorOptions")
@Suppress("UNCHECKED_CAST")
val arguments = annotationProcessorOptions.invokeNoArg("getArguments") as MutableMap<String, String>
arguments["AROUTER_MODULE_NAME"] = project.name

// 自动添加以下依赖
dependencies {
    add("implementation", "com.alibaba:arouter-api:1.5.2")
    add("kapt", "com.alibaba:arouter-compiler:1.5.2")
}
