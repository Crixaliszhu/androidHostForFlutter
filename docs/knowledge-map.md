# 原生 + Flutter 混合开发知识点地图

这份文档把 demo 中演示的混合开发知识点，映射到生产项目 `recruitment_android + easy_job_module`。

## 总览

| 知识点 | Demo 位置 | 生产项目位置 | 要理解什么 |
| --- | --- | --- | --- |
| 本地源码模式 | `android_host/settings.gradle.kts` | `recruitment_android/settings.gradle.kts` | 通过 `.android/include_flutter.groovy` 把 Flutter Module 注册成 Gradle 子工程 `:flutter`。 |
| Fat AAR 模式 | `README.md` / `native-flutter-setup.md` | `recruitment_android/settings.gradle.kts`、`flutter_base/build.gradle.kts` | Flutter 产物可发布成 Maven AAR，原生通过依赖坐标接入，适合 CI 和产物集成。 |
| Flutter Module | `flutter_module/pubspec.yaml` | `easy_job_module/pubspec.yaml` | Flutter Module 是被原生宿主加载的 Flutter 工程，包含 Dart 页面、资源、插件和生成文件。 |
| FlutterEngineGroup | `flutter_engine/manage/FlutterEngineProvider.kt` | `recruitment_android/flutter/flutter_base/src/main/java/com/yupao/flutter/manage/FlutterEngineProvider.kt` | 多个 FlutterEngine 共享底层资源，适合混合多页面场景。 |
| 引擎缓存 | `flutter_engine/manage/FlutterEngineManager.kt` | `recruitment_android/flutter/flutter_base/src/main/java/com/yupao/flutter/manage/FlutterEngineManager.kt` | 用 `engineId` 管理引擎生命周期，避免重复创建和错误销毁。 |
| 主引擎预热 | `flutter_biz/DemoFlutterInitManager.kt` | `recruitment_android/flutter/flutter_biz/src/main/java/com/yupao/flutter/biz/FlutterInitManager.kt` | App 空闲时提前创建主引擎，降低首次打开 Flutter 页面的耗时。 |
| Activity 容器 | `flutter_engine/container/BaseFlutterActivity.kt` | `recruitment_android/flutter/flutter_base/src/main/java/com/yupao/flutter/container/activity/BaseFlutterActivity.kt` | 原生容器负责提供 FlutterEngine、initialRoute、入口参数和生命周期回收。 |
| 页面代理 | `flutter_engine/container/FlutterPageProxy.kt` | `recruitment_android/flutter/flutter_base/src/main/java/com/yupao/flutter/container/host/FlutterPageProxy.kt` | 把路由、参数、引擎获取、页面登记从 Activity 中抽离。 |
| `nativeParams` | `FlutterPageProxy.kt`、`flutter_module/lib/main.dart` | `FlutterPageProxy.kt`、`easy_job_module/lib/routing` | 用 JSON 传复杂参数，保留 List/Map/Boolean/Number 类型。 |
| HostApi | `flutter_biz/bridge/*HostApiImpl.kt`、`flutter_module/lib/channel` | `easy_job_module/lib/utils/channel`、`recruitment_android/flutter/flutter_biz/src/main/java/com/yupao/flutter/biz/bridge` | Flutter 调 Native 的接口，例如 Toast、设备信息、路由。 |
| FlutterApi | `flutter_biz/bridge/EventApiCaller.kt`、`event_channel.dart` | Pigeon 生成的 `*FlutterAPI`、`FlutterEngineManager.sendEventToAllEngines` | Native 调 Flutter 的接口，例如事件推送、页面结果通知。 |
| 路由服务 | `flutter_biz/api/FlutterRouterServiceImpl.kt` | `recruitment_android/flutter/flutter_biz/src/main/java/com/yupao/flutter/biz/router/FlutterRouterServiceImpl.kt` | 原生业务方不直接启动 Flutter 容器，而是走统一 RouterService。 |
| 生命周期清理 | `FlutterEngineManager.removePage` | `FlutterEngineManager.removePage` | 页面销毁时移除 page，页面清空且不保活时销毁引擎。 |
| Dart entrypoint args | `FlutterArgsUtils.kt`、`entry_args.dart` | `FlutterArgsUtils.kt`、`easy_job_module/lib/main/entry_args.dart` | 原生通过 `setDartEntrypointArgs` 把包名、版本、环境等基础信息传给 Dart `main(List<String> args)`。 |

## 1. Gradle 接入

混合项目第一步是让原生 Gradle 认识 Flutter Module。

Demo 中：

- `android_host/settings.gradle.kts` 读取 `flutter.project.dir`。
- 应用 `${flutter.project.dir}/.android/include_flutter.groovy`。
- `flutter_engine/build.gradle.kts` 依赖 `api(project(":flutter"))`。

生产项目中：

- `recruitment_android/settings.gradle.kts` 同样读取 `flutter.project.dir`。
- 非 Fat AAR 模式下应用 `.android/include_flutter.groovy`。
- `flutter_base/build.gradle.kts` 根据 `IS_FLUTTER_FAT_AAR` 在源码和 AAR 之间切换。

要点：

- `flutter pub get` 必须先执行，否则 `.android/include_flutter.groovy` 可能不存在。
- Flutter Maven 仓库必须配置，否则找不到 Flutter embedding。
- `local.properties` 放本机绝对路径，不应提交个人路径。

## 2. 引擎管理

Flutter 页面不是每次都简单 new 一个 `FlutterActivity`。生产混合项目通常要自己管理引擎。

Demo 中：

- `FlutterEngineProvider` 只负责创建引擎。
- `FlutterEngineManager` 负责缓存、页面登记、销毁、广播。
- 主引擎 ID 是 `FlutterConstants.ENGINE_MAIN`。

要点：

- 首页、Tab、常驻场景适合复用主引擎。
- 二级页、弹窗、隔离业务适合独立引擎。
- 复用引擎打开新页面时，不能再次依赖 initialRoute，需要 `navigationChannel.pushRoute(route)`。
- 新建引擎时只设置 initialRoute，避免重复 push 首屏路由。

## 3. 容器模式

Demo 的 `BaseFlutterActivity` 对应生产项目的 `BaseFlutterActivity`。

容器职责：

- 从 Intent 读取 `route`、`engineId`、`instanceId`。
- 通过 `provideFlutterEngine` 提供引擎。
- 通过 `getInitialRoute` 提供 Flutter 首路由。
- 在 `onDestroy` 时通知 `FlutterEngineManager.removePage`。

`FlutterPageProxy` 职责：

- 生成缺省 `engineId` 和 `instanceId`。
- 拼装 `nativeParams`。
- 判断是新建引擎还是复用引擎。
- 负责页面登记和移除。

这样 Activity 保持模板化，复杂逻辑集中在代理类里。

## 4. 路由和参数

Demo 使用的路由格式：

```text
flutter/detail?nativeParams={"id":123,"title":"from native","instanceId":"demo-instance-001"}
```

为什么不用普通 query 参数传复杂对象：

- query 参数天然是字符串。
- Boolean、Number、List、Map 容易丢类型。
- JSON 能保留结构，Flutter 端统一解析。

Flutter 端在 `main.dart` 的 `onGenerateRoute` 中：

- 解析 route path。
- 解析 `nativeParams` JSON。
- 合并 query 和 `RouteSettings.arguments`。
- 根据 `routeMap` 分发页面。

生产项目中路由更复杂，包含生成路由、参数 model、路由拦截器和原生 fallback，但核心思想一致。

## 5. Native 与 Flutter 通信

Demo 已切到 Pigeon 强类型通道。

Flutter 调 Native：

- `ToastHostApi.show`
- `DeviceInfoHostApi.getInfo`
- `RouteHostApi.pushFlutterRoute`

Native 调 Flutter：

- `EventApiCaller.sendTick`
- `FlutterEngineManager.sendEventToAllEngines`
- Flutter 端 `DemoEventBridge.events`

- `@HostApi` 生成 Flutter 调 Native 的接口。
- `@FlutterApi` 生成 Native 调 Flutter 的接口。
- 自动生成类型、通道名和参数编解码，减少手写错误。

## 6. 预热和性能

Demo 在 `DemoFlutterInitManager` 中使用：

```kotlin
Looper.myQueue().addIdleHandler {
    FlutterEngineManager.initMainEngine()
    false
}
```

含义：

- App 主线程空闲时创建主引擎。
- 用户首次打开 Flutter 首页时，主引擎可能已经准备好。
- 生产项目还需要判断前后台、冷启动来源、耗时埋点、失败上报。

## 7. 多引擎事件

Demo 的 `sendEventToAllEngines` 会遍历所有活跃引擎的 `BinaryMessenger`：

```kotlin
getAllBinaryMessenger().forEach { messenger ->
    EventFlutterApi(messenger).onReceiveEvent(payload) { ... }
}
```

适用场景：

- 登录态变化。
- 账号切换。
- 全局配置变化。
- 业务事件广播。

注意：

- 只向活跃引擎发。
- Flutter 端要尽早注册监听。
- 事件数据仍然按可空处理。

## 8. 教学 Demo 到生产项目的迁移

从 demo 迁移到生产项目时，一般做这些替换：

| Demo 写法 | 生产写法 |
| --- | --- |
| Pigeon 生成代码 | Pigeon 生成代码 |
| `ServiceLocator` | ARouter Provider / Hilt |
| 少量 API | 按业务拆分多个 `*ApiImpl` |
| 简单 Log | 埋点、Crash、Sentry |
| 单 Activity 容器 | Activity、DialogActivity、Fragment 多容器 |
| 本地源码模式 | 本地源码 + Fat AAR 双模式 |

## 9. 阅读建议

如果只想快速理解混合架构，按这个顺序读：

1. `README.md`
2. `android_host/settings.gradle.kts`
3. `android_host/app/src/main/java/com/example/hybriddemo/DemoApplication.kt`
4. `android_host/flutter_biz/src/main/java/com/example/flutterbiz/DemoFlutterInitManager.kt`
5. `android_host/flutter_engine/src/main/java/com/example/flutterengine/manage/FlutterEngineProvider.kt`
6. `android_host/flutter_engine/src/main/java/com/example/flutterengine/manage/FlutterEngineManager.kt`
7. `android_host/flutter_engine/src/main/java/com/example/flutterengine/container/FlutterPageProxy.kt`
8. `android_host/flutter_biz/src/main/java/com/example/flutterbiz/api/FlutterRouterServiceImpl.kt`
9. `flutter_module/lib/main.dart`
10. `flutter_module/lib/channel`
