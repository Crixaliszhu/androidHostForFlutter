# Project-specific R8/ProGuard rules.
#
# 这个文件只放“本项目确实需要”的额外混淆规则。Android Gradle Plugin 会先加载
# proguard-android-optimize.txt，Sentry、AndroidX、Room、WorkManager 等依赖也会通过
# consumer-proguard-rules 自动携带自己的 keep 规则，所以这里不需要提前复制一大堆模板。
#
# 为什么当前规则看起来很少？
# 1. Sentry Android SDK 自带 consumer rules，通常不需要手动 keep Sentry 类。
# 2. Kotlin/AndroidX/Room 等常见库也会随 AAR 提供自身规则。
# 3. 过宽的 -keep 会削弱 R8 优化，导致正式包体积变大、无用代码无法裁剪。
# 4. 最稳妥的策略是：先开启 R8 构建 release 包，再根据 release-only 崩溃或反射失败补精确规则。
#
# 什么时候需要在这里加规则？
# - 代码通过 Class.forName("完整类名")、反射调用方法/字段，且类名无法被 R8 静态识别。
# - JSON/XML/路由/插件框架依赖运行时类名或字段名。
# - WebView JavaScriptInterface、ServiceLoader、JNI 入口等需要固定签名。
# - release 包出现 debug 包没有的问题，并且堆栈指向混淆/裁剪导致的类或方法缺失。
#
# 推荐写法：尽量 keep 到具体类、具体成员，避免全包 keep。
# 示例：如果某个类名被外部配置反射引用，可以只保留这个类名。
# -keepnames class com.example.hybriddemo.SomeReflectEntry
#
# 示例：如果 JSBridge 方法通过 @JavascriptInterface 暴露给 WebView，需要保留被注解的方法。
# -keepclassmembers class * {
#     @android.webkit.JavascriptInterface <methods>;
# }
#
# 示例：如果某个模型字段被没有 R8 适配的老 JSON 框架按字段名反射读取，可保留字段名。
# -keepclassmembers class com.example.hybriddemo.api.SomeLegacyDto {
#     <fields>;
# }

# ARouter 运行期通过生成的路由表和模板接口装载页面，R8 场景下需要保留这些入口。
-keep class com.alibaba.android.arouter.** { *; }
-keep public class com.alibaba.android.arouter.routes.** { *; }
-keep public class com.alibaba.android.arouter.facade.** { *; }
-keep class * implements com.alibaba.android.arouter.facade.template.IProvider { *; }
-keep class * implements com.alibaba.android.arouter.facade.template.ISyringe { *; }
-dontwarn javax.lang.model.element.Element
