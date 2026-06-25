# 鱼泡 Flutter 混合桥接最佳实践模板

## 1. 文档目标

本文档不是介绍 `FlutterHybridDemo` 怎么跑，而是沉淀一套更适合鱼泡正式项目使用的 Flutter 混合桥接模板。

重点回答三个问题：

1. 正式项目里，Flutter 与 Android 宿主的桥接层应该怎么分层。
2. 哪些桥接协议设计是推荐做法，哪些做法应该避免。
3. 如何把桥接层做成长期可维护、可验证、可治理的工程基础设施。

本文默认适用场景为：

- Android 宿主 + Flutter Add-to-App
- 多引擎混合项目
- Flutter 页面由原生宿主管理生命周期
- Flutter 与原生存在持续双向通信需求

## 2. 总体原则

正式项目里的桥接层，不应该只是“让 Flutter 能调到 Native”。

更准确地说，桥接层应该承担以下职责：

- 约束跨端协议，而不是放任字符串方法名散落。
- 隔离业务方与底层容器、引擎细节。
- 在多引擎场景下保持生命周期和事件分发可控。
- 为日志、监控、灰度、容错提供统一切入点。

所以桥接层的设计目标不是“最少代码”，而是：

- 边界清晰
- 类型稳定
- 生命周期可追踪
- 错误可观测
- 新能力接入成本低

## 3. 推荐架构分层

## 3.1 协议定义层

推荐做法：

- 用 Pigeon 作为 Flutter <-> Native 的主桥接协议定义工具。
- 按业务域拆分协议文件，而不是把所有能力堆在一个超大文件里。
- 协议文件只描述能力与数据结构，不写业务逻辑。

推荐职责：

- 定义 `@HostApi`
- 定义 `@FlutterApi`
- 定义消息对象
- 定义枚举、返回值对象、错误对象

不推荐做法：

- 继续扩散手写 `MethodChannel`
- 一个超级 `CommonHostApi` 承担几十种无边界能力
- 直接把业务临时字段塞进 `Map<String, dynamic>` 长期使用

正式项目建议结构：

```text
easy_job_module/
  pigeons/
    account_bridge.dart
    route_bridge.dart
    event_bridge.dart
    media_bridge.dart
```

## 3.2 Flutter 调用封装层

Pigeon 生成代码不应该直接散落在页面里使用。

推荐增加一层轻量封装，职责是：

- 统一调用入口
- 做参数适配
- 处理默认值和兼容逻辑
- 统一埋点、日志或异常包装

例如：

- `RouteHostApi` 是底层协议
- `FlutterRouteBridge` 是 Flutter 业务可直接使用的门面

这样做的好处是：

- 页面层不依赖生成代码细节
- 生成文件变更不会直接波及业务页面
- 后续如果桥接协议升级，改动集中在门面层

## 3.3 FlutterApi 事件桥接层

`@FlutterApi` 生成的接口本质上是 Native 调 Flutter 的入口，不适合直接被每个页面自己注册和消费。

正式项目推荐增加一层事件桥接器：

- 对 Native 回调做统一接收
- 再转成 Flutter 侧更易消费的流、观察器或事件总线

推荐职责：

- 尽早注册，避免错过预热阶段事件
- 把底层回调转成 typed event
- 做事件类型分类和路由
- 统一处理去重、节流、生命周期解绑

推荐结构：

```text
lib/utils/channel/flutter_api_bridge/
  event_flutter_api_bridge.dart
  route_flutter_api_bridge.dart
```

## 3.4 Android HostApi 实现层

Android 侧的每个 HostApi 实现类，应该只做一类能力，不要混写。

推荐原则：

- 一个类实现一个业务域接口
- `register()` 中只负责 `setUp`
- 业务逻辑委托给 Service / Router / UseCase

例如：

- `AccountHostApiImpl`
- `RouteHostApiImpl`
- `MediaHostApiImpl`

不推荐做法：

- 一个 `FlutterBridgeManager` 写满所有 `when(methodName)`
- HostApi 实现类自己持有过多业务状态
- 直接在实现类里拼装大量页面逻辑

## 3.5 引擎与容器协同层

桥接层不能脱离引擎管理单独设计。

正式项目里，桥接协议至少要明确依赖这些运行时上下文：

- 当前 `engineId`
- 当前宿主 `Activity/Fragment`
- 当前页面 `instanceId`
- 当前 `BinaryMessenger`

所以推荐保留类似下面的中间上下文对象：

- `FlutterApiContext`
- `FlutterPageEntity`
- `FlutterPageProxy`

桥接层只通过这些受控上下文访问容器，不直接全局乱拿对象。

## 4. 桥接协议设计规范

## 4.1 HostApi 与 FlutterApi 的拆分原则

推荐拆分方式：

- `HostApi`：Flutter 主动调 Native 的能力
- `FlutterApi`：Native 主动通知 Flutter 的能力

推荐判断标准：

- 由谁发起，就定义在哪一侧
- 回调方向不要混淆
- 不要为了偷懒把双向流程塞成单向接口

例如：

- “打开原生页”是 `HostApi`
- “登录态已变化，通知 Flutter 刷新”是 `FlutterApi`
- “页面关闭后把结果回传给 Flutter”是 `FlutterApi`

## 4.2 消息对象设计规范

推荐：

- 尽量定义明确对象，而不是长期用裸 `Map`
- 对可枚举状态使用 enum
- 对返回值使用对象包装，不直接返回多个散字段

例如推荐：

```dart
class RouteResult {
  String? code;
  String? message;
  Map<String?, Object?>? payload;
}
```

不推荐：

```dart
Map<String, dynamic>
```

长期承担：

- 成功态
- 失败态
- 页面回传
- 兼容字段

因为这样会带来：

- 字段名漂移
- 类型漂移
- 空值约束不清
- Android / Flutter 两侧隐式约定增加

## 4.3 可空约束规范

混合项目里，跨端字段默认应按可空处理。

这是正式项目必须坚持的约束，原因不是“懒”，而是：

- 原生版本不一致
- 灰度期间字段可能缺失
- 旧引擎 / 新页面共存
- 不同业务域升级节奏不同

推荐规则：

- 输入参数默认可空，必要时在实现层做校验
- 返回对象字段尽量可空
- 业务页面使用前必须做兜底

只有在满足这两个条件时，才建议强非空：

1. 协议字段是桥接必选项
2. 两端已经有稳定版本治理和强校验

## 4.4 哪些能力禁止直接透传 Map

以下能力不建议长期只用 `Map`：

- 登录态变化
- 页面结果回传
- 支付结果
- 上传结果
- 路由拦截结果
- 权限请求结果

因为这些能力都有清晰状态机，应该显式建模。

## 5. 路由与页面结果最佳实践

## 5.1 路由参数

正式项目推荐继续保留 `nativeParams` 思路：

- 原生路由 query 只负责路径与轻量参数
- 复杂业务参数统一进入 `nativeParams`
- `nativeParams` 内部尽量保持结构化 JSON

优点：

- 保留 `List/Map/Boolean/Number`
- 不容易因 query string 编码导致类型丢失
- 与容器层注入 `instanceId`、`engineId` 更自然

## 5.2 页面结果回传

正式项目不要长期沿用“直接透传一个 `Map` 作为 pop 结果”的粗粒度模式。

推荐统一成 `RouteResult` 模型：

- `code`
- `message`
- `payload`
- `sourcePage`
- `instanceId`

这样做的好处：

- 页面关闭原因可以被识别
- 埋点与日志更容易统一
- 原生/Flutter 双侧更容易做兼容

## 5.3 容器关闭模型

需要明确区分两种动作：

1. Flutter 业务页面 `pop`
2. Flutter 容器 `finish`

推荐做法：

- Navigator 栈变空或只剩根页时，由 Flutter 通知 Native 关闭容器
- Native 只负责关闭宿主，不主动猜测 Flutter 栈状态

这正是 `RouteStackObserver + removeFlutterContainer` 这类模型存在的价值。

## 6. 事件系统最佳实践

## 6.1 事件分类

正式项目建议把事件分成三类：

1. 全局广播事件
   - 登录态变化
   - 城市切换
   - 配置刷新

2. 引擎级事件
   - 当前引擎页面栈同步
   - 当前引擎业务刷新

3. 页面级事件
   - 指定 `instanceId` 回调
   - 指定页面结果通知

如果所有事件都走“广播到全部引擎”，短期简单，长期一定混乱。

## 6.2 事件对象

推荐统一事件模型：

```dart
class NativeEvent {
  String? name;
  String? scope;
  String? engineId;
  String? instanceId;
  Map<String?, Object?>? arguments;
}
```

其中：

- `name` 表示事件类型
- `scope` 表示广播范围
- `engineId` / `instanceId` 用于精确投递

## 6.3 注册时机

正式项目里，FlutterApi handler 必须尽早注册。

推荐时机：

- `main()` 中 `WidgetsFlutterBinding.ensureInitialized()` 之后
- `runApp()` 之前

原因：

- 主引擎预热时 Native 可能已经开始发事件
- 如果页面创建后才注册，会丢首批关键事件

## 7. 错误模型与监控

## 7.1 错误分类

桥接错误至少分为三类：

1. 协议错误
   - 通道不存在
   - 反序列化失败
   - 必填字段缺失

2. 业务错误
   - 登录失效
   - 路由无权限
   - 页面未找到

3. 运行时错误
   - Activity 已销毁
   - 当前引擎不存在
   - Messenger 不可用

这三类错误不要混成一个通用 `PlatformException("error")`。

## 7.2 推荐错误返回模型

推荐：

- 业务失败尽量返回结构化结果
- 协议失败才抛桥接异常

例如：

- “页面不存在”更适合返回业务失败对象
- “Pigeon channel 建连失败”才适合抛 `PlatformException`

## 7.3 监控要求

正式项目里的桥接层应该具备最基本的监控能力：

- 接口调用耗时
- 失败率
- 高频错误码
- 丢事件率
- 引擎级别调用分布

否则桥接层一旦变重，问题只能靠人工排查。

## 8. 工程化要求

## 8.1 目录结构

推荐结构：

```text
easy_job_module/
  pigeons/
  lib/utils/channel/generated/
  lib/utils/channel/
  lib/utils/channel/flutter_api_bridge/

recruitment_android/
  flutter/flutter_base/src/main/java/.../generated/
  flutter/flutter_biz/src/main/java/.../bridge/
```

原则：

- 生成代码与业务代码分开
- FlutterApi bridge 与 HostApi 调用封装分开
- 协议定义与实现分开

## 8.2 代码生成

推荐要求：

- 生成命令脚本化
- CI 校验生成产物是否最新
- 不依赖人工记忆执行顺序

至少应具备：

- `generate_channels.sh` 或 `dart run ...`
- 变更后自动检查未提交生成文件

## 8.3 混淆与发布

正式项目需要明确：

- Pigeon 生成类的混淆规则
- Release 构建中的代码生成前置步骤
- Fat AAR / 源码接入下的生成产物位置一致性

否则最容易出现：

- Debug 正常，Release 桥接失败
- 本地正常，CI 构建失败

## 8.4 验证清单

每次新增桥接能力，建议至少验证：

1. Flutter 单测或桥接层测试
2. Flutter analyze
3. Android compileDebugKotlin
4. 容器打开/关闭链路
5. 多引擎场景下事件是否误投递

## 9. 落地到鱼泡项目的建议映射

当前鱼泡正式工程可以按下面理解：

- `easy_job_module/lib/utils/channel`：Flutter 调用封装层
- `easy_job_module/lib/utils/channel/generated`：Pigeon 生成层
- `easy_job_module/lib/utils/channel/flutter_api_bridge`：FlutterApi 事件桥接层
- `recruitment_android/flutter/flutter_biz/.../bridge`：Android HostApi 实现层
- `recruitment_android/flutter/flutter_base/.../container|manage|registry`：容器与引擎协同层

下一步真正值得继续收敛的，不是“要不要用 Pigeon”，而是：

- 协议如何按业务域继续拆清
- 事件如何从全局广播走向分级投递
- 页面结果如何从 `Map` 走向结构化结果对象
- 错误和埋点如何统一

## 10. 推荐落地顺序

如果要在正式项目里继续升级桥接层，建议按这个顺序推进：

1. 继续坚持 Pigeon 作为唯一主桥接方案
2. 收敛 HostApi / FlutterApi 的业务域拆分
3. 为页面结果、事件、错误建立统一模型
4. 补齐生成、校验、混淆、CI 规范
5. 最后再做事件分级和更细粒度路由治理

这个顺序的原因是：

- 先统一协议工具
- 再统一模型
- 最后统一治理策略

否则很容易出现“工具统一了，但协议还是乱的”。

## 11. 最终结论

面向鱼泡正式项目，Flutter 混合桥接层的最佳实践不是：

- 最少抽象
- 最少文件
- 最快打通

而是：

`用 Pigeon 统一协议入口，用分层控制复杂度，用结构化模型控制长期维护成本。`

如果只解决“能不能调通”，那只是 demo 水平。

如果要满足正式项目长期迭代，桥接层必须被当成平台基础设施来设计。
