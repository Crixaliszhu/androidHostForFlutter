# 自研 ANR 监控上报系统设计

本文基于 `AndroidHostForFlutter` 当前工程设计一套不依赖第三方平台的线上 ANR 监控上报系统。目标不是替换调试期的 GodEye，也不是复刻 Sentry 全量能力，而是在宿主 App 内建立一条可控的最小闭环：

```text
App 采集 -> 本地落盘 -> 自建接口上报 -> 服务端聚合 -> 告警与排查
```

## 1. 接入位置

当前代码入口：

```text
anr_monitor/src/main/java/com/example/anrmonitor/
app/src/main/java/com/example/hybriddemo/DemoApplication.kt
```

初始化：

```kotlin
SelfAnrInitializer.init(
    application = this,
    config = SelfAnrConfig(
        enabled = BuildConfig.SELF_ANR_ENABLED,
        reportUrl = BuildConfig.SELF_ANR_REPORT_URL,
    ),
)
```

构建配置：

```kotlin
BuildConfig.SELF_ANR_ENABLED
BuildConfig.SELF_ANR_REPORT_URL
```

`SELF_ANR_REPORT_URL` 可以放在 `local.properties` 或 CI 环境变量中：

```properties
SELF_ANR_REPORT_URL=https://example.com/mobile-monitor/anr
```

未配置地址时，事件只会写入本地 `files/self_anr/` 队列，不会向外发送请求。

## 2. 分层设计

```text
SelfAnrInitializer
  负责初始化、生命周期注册、启动 watchdog、触发历史 ANR 补报。

AnrWatchDog
  后台线程定时向主线程 Handler 投递心跳，超过 5 秒未响应时采集线程栈。

AppExitAnrCollector
  Android 11/API 30+ 读取 ApplicationExitInfo.REASON_ANR，补报系统真实 ANR。

AnrBreadcrumbs
  记录 Activity 生命周期、前后台状态、当前页面。

AnrThreadDumper
  采集 main thread 和全线程堆栈。

AnrSnapshotStore
  本地 JSON 队列，先落盘再上传，避免 ANR 现场因进程死亡丢失。

AnrHttpReporter
  使用 HttpURLConnection 上报到自建服务端。
```

## 3. 双通道 ANR 识别

### 3.1 Watchdog 疑似 ANR

适用于所有 Android 版本。

检测逻辑：

```text
后台线程记录 tick
向 main Handler post { tick++ }
sleep 5s
如果 tick 没变，说明主线程 5s 内没有处理消息
采集 main/all threads
写入本地队列并尝试上报
```

它的价值是实时发现主线程卡死，但它不等于系统最终 ANR。系统 ANR 还和输入事件、Broadcast、Service、ContentProvider 等超时机制有关。

### 3.2 ApplicationExitInfo 系统 ANR

适用于 Android 11/API 30+。

检测逻辑：

```text
下次启动读取 ActivityManager.getHistoricalProcessExitReasons
筛选 ApplicationExitInfo.REASON_ANR
读取 traceInputStream
按 pid + timestamp + reason 去重
写入本地队列并尝试上报
```

这是更接近系统判定的 ANR，但只能在进程重启后补报。

推荐线上口径：

```text
watchdog = 疑似主线程卡死，用于提前发现和补充现场
system_exit_info = 系统确认 ANR，用于核心指标和告警
```

## 4. 上报字段

当前事件结构：

```text
id
type: watchdog / system_exit_info
timestampMillis
processName
foreground
currentActivity
lastBreadcrumbs
mainThreadStack
allThreadStacks
systemTrace
extra
```

生产环境建议继续扩展：

```text
appVersion
versionCode
channel
deviceModel
androidVersion
abi
memoryInfo
networkType
batteryState
flutterEngineId
flutterRoute
userActionBreadcrumbs
requestBreadcrumbs
```

Flutter 混合项目尤其建议补充：

```text
Flutter 页面路由
Flutter Engine 类型：main / cached / new
Native -> Flutter bridge 调用记录
Flutter -> Native HostApi 调用记录
```

这些字段可以在现有 Pigeon/路由层统一打 breadcrumb，避免 ANR 发生时只知道 Activity，不知道 Flutter 页面。

## 5. 服务端设计

接口建议：

```http
POST /mobile-monitor/anr
Content-Type: application/json
```

服务端处理：

```text
1. 校验 appId、版本、签名或 token
2. 保存原始 JSON 和 trace
3. 提取 main thread 栈顶业务帧
4. 生成 fingerprint
5. 按 fingerprint + versionCode 聚合
6. 统计 count、affectedUsers、foreground/background、机型和系统分布
7. 超阈值发送企业微信/飞书/邮件告警
```

fingerprint 可以先用：

```text
type + mainThreadTopBusinessFrames + processName
```

后续再针对锁等待、Binder、IO、Flutter bridge 等场景做归因增强。

## 6. 告警策略

建议以 `system_exit_info` 作为主告警指标：

```text
5 分钟 system_exit_info ANR 数 > 10 -> P1
5 分钟 system_exit_info 影响用户数 > 5 -> P1
1 小时某版本 ANR 率 > 0.3% -> 暂停灰度
新版本 ANR 数较上一版本上涨 100% -> 版本风险告警
```

`watchdog` 可单独做趋势观察：

```text
5 分钟 watchdog 数 > 50 -> 卡死风险告警
单一 fingerprint 占比 > 30% -> 聚合问题告警
```

## 7. 隐私与稳定性

客户端侧约束：

```text
只在后台线程做 IO 和网络
先落盘再上传
本地队列限制最大 20 条
trace 限制最大 180000 字符
同一次 freeze 只报一次 watchdog
ApplicationExitInfo 做去重
```

隐私侧约束：

```text
不要上传页面输入框文本
不要上传截图
breadcrumb 只记录动作类型和页面标识
用户 ID 使用 hash 或内部匿名 ID
服务端保留周期建议 7-30 天
```

## 8. 后续演进

短期：

```text
接入真实 SELF_ANR_REPORT_URL
补 appVersion/device/network/memory 字段
在 Flutter 路由和 Pigeon 调用处写 breadcrumb
```

中期：

```text
服务端做 fingerprint 聚合
接入告警机器人
增加 mapping 反混淆支持
增加 release 灰度质量看板
```

长期：

```text
结合 Perfetto/trace marker 分析卡顿
建立 Crash、ANR、OOM、慢启动统一自研质量平台
按页面、业务线、版本负责人自动归属
```
