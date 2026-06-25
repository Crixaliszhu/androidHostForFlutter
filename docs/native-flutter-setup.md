# Flutter 接入已有原生 Android 项目步骤

这份文档说明如何把 Flutter Module 接入一个已有 Android 原生项目。示例代码来自 `FlutterHybridDemo/android_host`，生产项目可对照 `recruitment_android + easy_job_module`。

## 目标架构

```text
your_project/
├── android_host/              # 已有原生 Android 工程
│   ├── settings.gradle.kts
│   ├── local.properties
│   ├── app/
│   ├── flutter_engine/        # 建议新增：Flutter 基础设施层
│   └── flutter_biz/           # 建议新增：Flutter 业务接入层
└── flutter_module/            # 新增：Flutter Module
```

推荐把 Flutter 能力拆成两层：

- `flutter_engine`：通用能力，不关心具体业务。负责引擎、容器、路由参数、通道注册。
- `flutter_biz`：业务能力。负责 Toast、设备信息、业务路由、登录、IM 等具体 HostApi。

这样原生主 App 不需要直接理解 Flutter 细节，只依赖一个面向业务的 RouterService。

## 1. 创建 Flutter Module

在原生工程同级目录执行：

```sh
flutter create -t module flutter_module
cd flutter_module
flutter pub get
```

如果项目使用 FVM：

```sh
fvm flutter create -t module flutter_module
cd flutter_module
fvm flutter pub get
```

创建后重点检查：

```text
flutter_module/
├── pubspec.yaml
├── .android/include_flutter.groovy
└── .flutter-plugins-dependencies
```

`pubspec.yaml` 里需要有 Module 配置：

```yaml
flutter:
  module:
    androidX: true
    androidPackage: com.example.flutter_module
```

`androidPackage` 需要换成你的包名空间。

## 2. 在原生工程配置 Flutter Module 路径

编辑原生工程的 `local.properties`：

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
flutter.project.dir=/absolute/path/to/flutter_module
```

注意：

- `flutter.project.dir` 使用绝对路径。
- 不建议把每个人的 `local.properties` 提交到 Git。
- 可以提交 `local.properties.template`，让每个开发者复制后改成本机路径。

Demo 对应文件：

- `FlutterHybridDemo/android_host/local.properties.template`

## 3. 在 settings.gradle.kts 读取 Flutter 路径

在原生 `settings.gradle.kts` 里添加读取方法：

```kotlin
import java.util.Properties

fun Settings.flutterProjectDir(): String {
    val localProps = Properties().apply {
        val f = File(rootDir, "local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    return localProps.getProperty("flutter.project.dir")
        ?: error("请在 local.properties 中配置 flutter.project.dir")
}
```

这让 Gradle 可以稳定找到 Flutter Module。

Demo 对应文件：

- `FlutterHybridDemo/android_host/settings.gradle.kts`

## 4. 添加 Flutter Maven 仓库

Flutter Android embedding 和 ABI 产物来自 Flutter Maven 仓库：

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { setUrl("https://storage.googleapis.com/download.flutter.io") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://storage.googleapis.com/download.flutter.io") }
    }
}
```

为什么用 `PREFER_SETTINGS`：

- Flutter 生成的 `.android/Flutter/build.gradle` 可能自己添加仓库。
- 如果原生工程使用 `FAIL_ON_PROJECT_REPOS`，Gradle Sync 可能失败。

国内网络环境也可以额外加入：

```kotlin
maven { setUrl("https://storage.flutter-io.cn/download.flutter.io") }
```

## 5. 应用 include_flutter.groovy

仍然在 `settings.gradle.kts` 中添加：

```kotlin
val includeFlutterScript = File(settings.flutterProjectDir(), ".android/include_flutter.groovy")
if (includeFlutterScript.exists()) {
    apply(from = includeFlutterScript)
} else {
    logger.warn("未找到 ${includeFlutterScript.absolutePath}，请先在 Flutter Module 目录执行 flutter pub get")
}
```

这一步会把 Flutter Module 注册为 Gradle 子工程，通常是：

```text
:flutter
```

常见问题：

- 报找不到 `include_flutter.groovy`：先到 Flutter Module 执行 `flutter pub get`。
- 报找不到 Flutter SDK：确认本机 Flutter 环境和 `PATH` 配置。
- Gradle Sync 卡在下载 Flutter embedding：检查 Flutter Maven 仓库是否可访问。

## 6. 新增 flutter_engine 模块并依赖 :flutter

在原生工程新增 Android Library，例如：

```text
android_host/flutter_engine
```

在 `settings.gradle.kts` 中 include：

```kotlin
include(":flutter_engine")
```

在 `flutter_engine/build.gradle.kts` 中依赖 Flutter：

```kotlin
dependencies {
    api(project(":flutter"))
}
```

为什么用 `api`：

- `flutter_engine` 对外暴露 `FlutterActivity`、`FlutterEngine` 等类型时，下游模块也需要看到 Flutter embedding 类型。
- 如果只在内部使用 Flutter 类型，也可以用 `implementation`。

Demo 对应文件：

- `FlutterHybridDemo/android_host/flutter_engine/build.gradle.kts`

## 7. 用 FlutterEngineGroup 创建引擎

核心代码：

```kotlin
private val group: FlutterEngineGroup by lazy {
    FlutterEngineGroup(appContext)
}

fun create(initRoute: String?): FlutterEngine {
    val options = FlutterEngineGroup.Options(appContext)
        .setInitialRoute(initRoute ?: "flutter/root")
        .setDartEntrypointArgs(FlutterArgsUtils.createEntryArgs(appContext))
    return group.createAndRunEngine(options)
}
```

为什么用 `FlutterEngineGroup`：

- 多个 FlutterEngine 可以共享底层资源。
- 比每次 `FlutterEngine(context)` 更适合混合多页面场景。

Demo 对应文件：

- `FlutterHybridDemo/android_host/flutter_engine/src/main/java/com/example/flutterengine/manage/FlutterEngineProvider.kt`

## 8. 建立引擎缓存和销毁策略

需要一个统一的 `FlutterEngineManager`：

- `engineId -> FlutterEngineEntity`
- `engineId -> pages`
- `keepEmptyEngineIds`
- `sendEventToAllEngines`

典型策略：

- 首页或 Tab 页面使用主引擎：`ENGINE_MAIN`。
- 主引擎加入保活集合，页面退出后不销毁。
- 普通二级页使用独立 `engineId`，页面退出后销毁。
- 需要复用同一引擎时，先判断引擎是否已存在，再 `navigationChannel.pushRoute(route)`。

Demo 对应文件：

- `FlutterHybridDemo/android_host/flutter_engine/src/main/java/com/example/flutterengine/manage/FlutterEngineManager.kt`

## 9. 封装 Flutter 容器

不要让每个业务 Activity 都直接写 Flutter 初始化逻辑。建议封装：

- `BaseFlutterActivity`
- `FlutterPageProxy`

容器负责：

- 从 `Intent` 取 `route`、`engineId`、`instanceId`。
- 提供 `provideFlutterEngine(context)`。
- 提供 `getInitialRoute()`。
- 在 `onDestroy()` 移除页面并触发引擎销毁策略。

Demo 对应文件：

- `FlutterHybridDemo/android_host/flutter_engine/src/main/java/com/example/flutterengine/container/BaseFlutterActivity.kt`
- `FlutterHybridDemo/android_host/flutter_engine/src/main/java/com/example/flutterengine/container/FlutterPageProxy.kt`

## 10. 设计路由和 nativeParams 参数协议

建议 Native 打开 Flutter 页面时只传一个统一路由字符串：

```text
flutter/detail?nativeParams={"id":123,"title":"from native","instanceId":"demo-instance-001"}
```

其中：

- path：`flutter/detail`
- `nativeParams`：JSON 字符串，保留复杂类型。
- `instanceId`：原生容器实例 ID，用于关闭容器、结果回传、事件关联。

不要把复杂对象拆成普通 query 参数，因为 List/Map 会被转成字符串，类型容易丢失。

Demo 对应文件：

- Native 拼参数：`FlutterPageProxy.kt`
- Flutter 解参数：`flutter_module/lib/main.dart`

## 11. 新增 flutter_biz 业务接入层

业务层提供具体 API：

```text
flutter_biz/
├── DemoFlutterInitManager.kt
├── api/
│   ├── IFlutterRouterService.kt
│   └── FlutterRouterServiceImpl.kt
├── bridge/
│   ├── ToastHostApiImpl.kt
│   ├── DeviceInfoHostApiImpl.kt
│   ├── RouterHostApiImpl.kt
│   └── EventApiCaller.kt
└── container/
    └── DemoFlutterActivity.kt
```

`Application.onCreate()` 中调用：

```kotlin
DemoFlutterInitManager.init(this, isKeepMainEngine = true)
```

它负责：

- 初始化 `FlutterEngineManager`。
- 注册每个引擎创建时需要安装的 HostApi。
- 注册 `IFlutterRouterService`。
- 添加主引擎保活。
- 主线程 idle 时预热主引擎。

Demo 对应文件：

- `FlutterHybridDemo/android_host/flutter_biz/src/main/java/com/example/flutterbiz/DemoFlutterInitManager.kt`

## 12. 注册 Native 与 Flutter 通道

Demo 现在直接使用 Pigeon：

- 定义 `@HostApi()` 给 Flutter 调 Native。
- 定义 `@FlutterApi()` 给 Native 调 Flutter。
- 生成 Kotlin / Dart / Swift，减少通道名和字段类型错误。

Demo 对应文件：

- 协议：`flutter_module/pigeons/demo_bridge.dart`
- Native：`android_host/flutter_biz/src/main/java/com/example/flutterbiz/bridge`
- Flutter：`flutter_module/lib/pigeon`

## 13. 原生业务方打开 Flutter 页面

业务方不直接 new Flutter Activity，而是走服务：

```kotlin
ServiceLocator.get(IFlutterRouterService::class)?.goFlutter(
    context = this,
    route = "flutter/detail",
    args = mapOf("id" to 123, "title" to "from_native"),
    useMainEngine = false,
)
```

生产项目可替换为 ARouter Provider：

```kotlin
YPRouterApi.getService(IFlutterRouterService::class.java)
    ?.goFlutter(context, route, args)
```

Demo 对应文件：

- `FlutterHybridDemo/android_host/app/src/main/java/com/example/hybriddemo/MainActivity.kt`
- `FlutterHybridDemo/android_host/flutter_biz/src/main/java/com/example/flutterbiz/api/FlutterRouterServiceImpl.kt`

## 14. 验证

执行：

```sh
cd FlutterHybridDemo/flutter_module
flutter pub get

cd ../android_host
./gradlew :app:assembleDebug
```

运行 App 后验证：

1. 点“打开 Flutter 首页（主引擎）”。
2. 点 Flutter 页面的 Toast 和设备信息按钮。
3. 返回原生页，点“向所有 Flutter 引擎推 tick 事件”。
4. 点“打开 Flutter 详情页（独立引擎 + 传参）”，确认参数展示。

## 生产化扩展

### Pigeon

当前 demo 已经使用 Pigeon：

- 定义文件放在 Flutter Module 的 `pigeons/demo_bridge.dart`。
- 生成 Dart/Kotlin 代码。
- 原生实现生成的 HostApi 接口，Flutter 注册 FlutterApi handler。

### Fat AAR

如果不希望原生工程每次都依赖 Flutter 源码，可做 Fat AAR：

- Flutter Module 打包成 Maven 产物。
- 原生项目通过 Gradle 属性切换。
- Debug 走源码模式，Release 或 CI 走 AAR 模式。

生产项目 `recruitment_android/settings.gradle.kts` 已有 `IS_FLUTTER_FAT_AAR` 类似开关。

### 多引擎治理

真实项目需要补充：

- 引擎创建耗时埋点。
- 引擎创建失败上报。
- 页面栈与原生 Activity 栈对账。
- 登录态、环境参数、AB 参数注入。
- 低端机内存压力下的引擎回收策略。
