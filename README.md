# 原生 + Flutter 混合教学 Demo 设计

## 背景

当前仓库包含真实生产工程 `recruitment_android`、Flutter Module `easy_job_module`，以及一个独立演示工程 `FlutterHybridDemo`。本次目标是在 `FlutterHybridDemo` 基础上沉淀一个“教学型 demo + 保持可运行”的原生 + Flutter 混合项目，用最小代码演示生产工程里用到的核心混合开发知识点。

这个 demo 服务两个场景：

1. 新人可以按 README 跑通 demo，理解已有原生项目如何接入 Flutter Module。
2. 开发者可以对照 demo 与 `recruitment_android + easy_job_module`，看懂生产工程里的引擎、容器、路由、桥接分层。

## 目标

- 保持 `FlutterHybridDemo` 独立可运行，不依赖公司私有插件、ARouter、Hilt、Pigeon 生成链路。
- 用接近生产工程的分层名称组织代码：宿主 App、Flutter 引擎基础层、Flutter 业务桥接层、Flutter Module。
- README 写清楚“Flutter 接入已有原生项目”的完整搭建步骤。
- 每个混合开发关键点都有可运行按钮、代码位置和文档解释。
- 保留迁移到生产方案的说明：Pigeon、ARouter、Hilt、Fat AAR、本地源码模式。

## 非目标

- 不复刻 `recruitment_android` 的完整业务依赖。
- 不接入真实网络、登录、埋点、图片选择、IM 等复杂业务。
- 不强制引入 Pigeon 代码生成；demo 用 `MethodChannel` 手写通道模拟 HostApi / FlutterApi 语义。
- 不做 RN 或 iOS 演示。

## Demo 目录结构

```text
FlutterHybridDemo/
├── README.md
├── docs/
│   ├── native-flutter-setup.md
│   └── knowledge-map.md
├── android_host/
│   ├── settings.gradle.kts
│   ├── local.properties.template
│   ├── app/
│   ├── flutter_engine/
│   └── flutter_biz/
└── flutter_module/
    ├── pubspec.yaml
    └── lib/
```

其中：

- `android_host/app`：原生宿主 App，负责 Application 初始化和 MainActivity 演示入口。
- `android_host/flutter_engine`：通用混合基础设施，包含 `FlutterEngineGroup`、引擎缓存、容器基类、API 注册中心、路由参数协议。
- `android_host/flutter_biz`：业务侧 Flutter 接入层，包含业务 API 注册、Flutter 容器 Activity、Flutter 路由服务。
- `flutter_module`：Flutter Module，包含页面、路由、通道封装和入口参数解析。

## Flutter 接入已有原生项目搭建步骤

### 1. 在原生项目旁创建 Flutter Module

在已有 Android 原生项目同级或指定目录创建 Flutter Module：

```sh
flutter create -t module flutter_module
cd flutter_module
flutter pub get
```

关键产物：

- `pubspec.yaml`：声明 Flutter Module 信息。
- `.android/include_flutter.groovy`：Flutter SDK 生成的 Gradle 胶水脚本。
- `.flutter-plugins-dependencies`：Flutter 插件依赖描述，原生侧可用它定位插件路径。

`pubspec.yaml` 中需要确认：

```yaml
flutter:
  module:
    androidX: true
    androidPackage: com.example.flutter_module
```

### 2. 原生宿主配置 Flutter Module 路径

在原生宿主 `local.properties` 增加：

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
flutter.project.dir=/absolute/path/to/flutter_module
```

`flutter.project.dir` 必须是绝对路径，方便 Gradle 在任意工作目录下稳定定位 Flutter Module。

### 3. 原生 settings.gradle.kts 引入 Flutter 仓库

宿主 `settings.gradle.kts` 需要包含 Flutter 引擎产物仓库：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://storage.googleapis.com/download.flutter.io") }
    }
}
```

使用 `PREFER_SETTINGS` 是为了兼容 Flutter 生成脚本内部添加仓库的行为。

### 4. 原生 settings.gradle.kts 应用 include_flutter.groovy

读取 `local.properties` 中的 `flutter.project.dir`，然后应用 Flutter 生成脚本：

```kotlin
val includeFlutterScript = File(settings.flutterProjectDir(), ".android/include_flutter.groovy")
if (includeFlutterScript.exists()) {
    apply(from = includeFlutterScript)
} else {
    logger.warn("请先在 Flutter Module 目录执行 flutter pub get")
}
```

这一步会把 Flutter Module 注册成 Gradle 子工程，通常名为 `:flutter`。

### 5. 原生模块依赖 :flutter

在负责 Flutter 基础设施的 Android Library 中依赖 Flutter 工程：

```kotlin
dependencies {
    api(project(":flutter"))
}
```

生产项目可以扩展为双模式：

- 本地源码模式：`api(project(":flutter"))`，适合开发调试。
- Fat AAR 模式：`api("group:name:version")` + Flutter embedding/ABI 依赖，适合产物集成。

### 6. 建立 Flutter 基础层

基础层负责与业务无关的混合能力：

- `FlutterEngineGroup` 创建引擎。
- 使用 `engineId` 缓存引擎。
- 主引擎 idle 预热。
- 页面销毁后按策略销毁或保留引擎。
- 统一注册 HostApi / FlutterApi。
- 封装 `BaseFlutterActivity` / `BaseFlutterFragment`。
- 统一处理 `initialRoute`、`nativeParams`、`instanceId`。

### 7. 建立 Flutter 业务接入层

业务层负责把具体能力注册给 Flutter：

- Toast HostApi。
- DeviceInfo HostApi。
- Router HostApi。
- Event FlutterApi。
- Flutter 页面入口 Activity。
- 面向原生业务方的 RouterService。

生产项目可把这里替换为 ARouter Provider、Hilt 注入和 Pigeon 生成接口。

### 8. Flutter 端解析入口参数与路由

Flutter `main(List<String> args)` 负责解析原生通过 `DartEntrypointArgs` 传入的 App 信息。

Flutter 初始路由通过原生容器的 `getInitialRoute()` 传入，例如：

```text
flutter/detail?nativeParams={"id":123,"title":"from native","instanceId":"demo-instance-001"}
```

Flutter 端需要：

- 解析 route path。
- 解析 `nativeParams` JSON。
- 根据 path 分发页面。
- 保留 `instanceId` 用于关闭页面、结果回传和事件关联。

### 9. 建立 Native 与 Flutter 通信

demo 使用 `MethodChannel` 手写，语义上对应生产项目 Pigeon：

- Flutter -> Native：HostApi，例如 Toast、DeviceInfo、Router。
- Native -> Flutter：FlutterApi，例如推送事件、通知页面结果。
- 通道注册时机：每个 FlutterEngine 创建完成后，用该 engine 的 `binaryMessenger` 注册。

生产迁移时，把手写 channel 替换为 Pigeon：

```dart
@HostApi()
abstract class ToastHostApi {
  void show(String message);
}

@FlutterApi()
abstract class EventFlutterApi {
  void onReceiveEvent(String name, Map<String, Object?> arguments);
}
```

### 10. 提供可运行演示入口

原生 MainActivity 至少提供：

- 打开 Flutter 首页。
- 打开 Flutter 详情页并传复杂参数。
- 向 Flutter 推事件。
- 展示当前引擎缓存状态。

Flutter 首页至少提供：

- 调原生 Toast。
- 调原生 DeviceInfo。
- 让原生打开 Flutter 详情页。
- 监听原生推送事件并显示计数。

## 知识点映射

| 知识点 | Demo 文件 | 生产项目对照 |
| --- | --- | --- |
| 本地源码模式接入 Flutter Module | `android_host/settings.gradle.kts` | `recruitment_android/settings.gradle.kts` |
| Flutter Module 配置 | `flutter_module/pubspec.yaml` | `easy_job_module/pubspec.yaml` |
| 引擎管理与缓存 | `flutter_engine/manage/FlutterEngineManager.kt` | `recruitment_android/flutter/flutter_base/src/main/java/com/yupao/flutter/manage/FlutterEngineManager.kt` |
| FlutterEngineGroup | `flutter_engine/manage/FlutterEngineProvider.kt` | `recruitment_android/flutter/flutter_base/src/main/java/com/yupao/flutter/manage/FlutterEngineProvider.kt` |
| 主引擎预热 | `flutter_biz/DemoFlutterInitManager.kt` | `recruitment_android/flutter/flutter_biz/src/main/java/com/yupao/flutter/biz/FlutterInitManager.kt` |
| 容器基类 | `flutter_engine/container/BaseFlutterActivity.kt` | `recruitment_android/flutter/flutter_base/src/main/java/com/yupao/flutter/container/activity/BaseFlutterActivity.kt` |
| 页面代理与参数协议 | `flutter_engine/container/FlutterPageProxy.kt` | `recruitment_android/flutter/flutter_base/src/main/java/com/yupao/flutter/container/host/FlutterPageProxy.kt` |
| API 注册中心 | `flutter_engine/registry/FlutterApiRegistry.kt` | `recruitment_android/flutter/flutter_base/src/main/java/com/yupao/flutter/container/registry/FlutterApiRegistry.kt` |
| Flutter -> Native | `flutter_biz/bridge/*HostApiImpl.kt` | `recruitment_android/flutter/flutter_biz/src/main/java/com/yupao/flutter/biz/bridge/*ApiImpl.kt` |
| Native -> Flutter | `flutter_biz/bridge/EventApiCaller.kt` | `EventFlutterAPI` / 跨引擎事件 |
| Flutter 路由解析 | `flutter_module/lib/routing/router.dart` | `easy_job_module/lib/routing/router.dart` |
| Dart 入口参数 | `flutter_module/lib/app/entry_args.dart` | `easy_job_module/lib/main/entry_args.dart` |

## 错误处理

- `local.properties` 未配置 `flutter.project.dir`：Gradle 给出明确错误。
- 未执行 `flutter pub get`：提示缺少 `.android/include_flutter.groovy`。
- Flutter 引擎创建失败：原生日志输出错误，页面不崩溃。
- Channel 未注册：Flutter 端捕获 `PlatformException` 并展示错误文本。
- 路由参数为空或非法 JSON：Flutter 端降级为空 map。

## 验证方式

最小验证：

```sh
cd FlutterHybridDemo/flutter_module
flutter pub get

cd ../android_host
./gradlew :app:assembleDebug
```

手动验证：

1. Android Studio 打开 `FlutterHybridDemo/android_host`。
2. Run `app`。
3. 点击“打开 Flutter 首页”，确认 Flutter 页面展示。
4. 点击“打开 Flutter 详情页 + 传参”，确认参数展示。
5. 在 Flutter 首页点击 Toast、设备信息、打开详情页按钮。
6. 回到原生页点击“向 Flutter 推事件”，确认 Flutter 计数变化。

## 文档交付

README 应作为入口文档，包含：

- 一句话说明 demo 目的。
- 目录结构。
- 快速运行步骤。
- 从零接入已有原生项目步骤。
- 演示功能说明。
- 文件阅读顺序。
- 与生产项目映射。

`docs/native-flutter-setup.md` 应更详细解释搭建步骤。

`docs/knowledge-map.md` 应按知识点解释概念、demo 代码位置、生产项目代码位置。
