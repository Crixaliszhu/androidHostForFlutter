# Sentry 集成、问题分析与线上监控实践

本文基于 `AndroidHostForFlutter` 当前接入过程整理，覆盖 Android 原生/Flutter 混合项目中 Sentry 的集成、正式包配置、崩溃/ANR/卡顿分析，以及线上 Monitor/Alert 策略。

## 1. 接入目标

Sentry 在移动端主要解决三类问题：

- 崩溃与异常：自动收集未捕获崩溃、手动上报已捕获异常、ANR、OOM 等。
- 性能分析：收集页面加载、手动事务、span 耗时、CPU Profile。
- 线上质量监控：基于 release、environment、session、crash free rate、error count 等指标创建 Monitor 和 Alert。

当前项目的目标不是只把 SDK 加进来，而是打通完整线上链路：

```text
release 包 -> production 环境 -> R8 混淆 -> mapping.txt 上传 -> Sentry 反混淆 -> Issue/Trace/Profile/Monitor 可用
```

## 2. 当前项目接入位置

核心文件：

```text
build.gradle.kts
settings.gradle.kts
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/com/example/hybriddemo/DemoApplication.kt
app/src/main/java/com/example/hybriddemo/sentry/SentryInitializer.kt
app/src/main/java/com/example/hybriddemo/sentry/SentryDemoActivity.kt
app/src/main/res/layout/activity_sentry_demo.xml
local.properties.template
app/proguard-rules.pro
```

当前 Sentry 版本：

```kotlin
implementation("io.sentry:sentry-android:8.51.0")
id("io.sentry.android.gradle") version "6.16.0"
```

## 3. Gradle 配置

顶层 `build.gradle.kts` 声明 Sentry Gradle Plugin：

```kotlin
plugins {
    id("io.sentry.android.gradle") version "6.16.0" apply false
}
```

`settings.gradle.kts` 中也声明插件版本，避免 Flutter 生成的 `.android/Flutter/build.gradle` 使用无版本 `com.android.library` 时解析失败：

```kotlin
pluginManagement {
    plugins {
        id("com.android.application") version "8.6.0"
        id("com.android.library") version "8.6.0"
        id("org.jetbrains.kotlin.android") version "1.9.22"
        id("io.sentry.android.gradle") version "6.16.0"
    }
}
```

`app/build.gradle.kts` 中启用插件：

```kotlin
plugins {
    id("io.sentry.android.gradle")
}
```

Sentry Gradle Plugin 主要用于构建期能力，例如：

- 上传 R8/ProGuard `mapping.txt`
- 关联 release
- 可选上传 native symbols
- 可选 source context

## 4. local.properties 配置

真实值放在 `local.properties` 或 CI Secret 中，不提交到 Git。

```properties
SENTRY_DSN=https://<public-key>@<org>.ingest.sentry.io/<project-id>
SENTRY_ORG=crixalis
SENTRY_PROJECT=android
SENTRY_AUTH_TOKEN=sntrys_xxx

SENTRY_TRACES_SAMPLE_RATE=0.05
SENTRY_PROFILES_SAMPLE_RATE=0.01
SENTRY_ATTACH_SCREENSHOT=false
SENTRY_ATTACH_VIEW_HIERARCHY=false
```

配置项说明：

```text
SENTRY_DSN
客户端上报地址，会被打进 APK。它不是密钥，属于公开写入地址。

SENTRY_AUTH_TOKEN
构建期 token，用于 Gradle Plugin 上传 mapping.txt。不能打进 APK，不能提交 Git。

SENTRY_ORG / SENTRY_PROJECT
决定 mapping.txt 上传到哪个组织和项目。

SENTRY_TRACES_SAMPLE_RATE
Trace 采样率。0.05 表示 5% 的事务会进入 Performance/Traces。

SENTRY_PROFILES_SAMPLE_RATE
Profile 采样率。它作用在已被 Trace 采样的事务上。0.01 表示已采样 trace 中 1% 继续采 CPU Profile。

SENTRY_ATTACH_SCREENSHOT / SENTRY_ATTACH_VIEW_HIERARCHY
截图和视图层级开关。对定位 UI 问题有帮助，但可能包含用户隐私，线上默认关闭。
```

## 5. SENTRY_AUTH_TOKEN 从哪里来

推荐路径：

```text
Sentry -> Settings -> Developer Settings -> Organization Tokens -> Create New Token
```

用途：

```text
sentry-cli
Gradle plugin
CI/CD
上传 mapping.txt
创建 release
上传 source map / native symbols
```

建议权限：

```text
org:read
project:read
project:releases
```

如果上传 mapping 时返回 403，再按 Sentry 提示补相关权限。

`Custom Integrations` 是创建完整内部集成应用的入口，不是当前 Gradle 上传 mapping 的首选路径。当前场景使用 `Organization Tokens` 更直接。

## 6. 运行时初始化

项目在 `DemoApplication.onCreate()` 中调用：

```kotlin
SentryInitializer.init(this)
```

`SentryInitializer` 里关键配置：

```kotlin
options.dsn = BuildConfig.SENTRY_DSN
options.isEnabled = BuildConfig.SENTRY_DSN.isNotBlank()
options.environment = BuildConfig.SENTRY_ENVIRONMENT
options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
options.tracesSampleRate = BuildConfig.SENTRY_TRACES_SAMPLE_RATE
options.profilesSampleRate = BuildConfig.SENTRY_PROFILES_SAMPLE_RATE
options.isEnableAutoSessionTracking = true
options.isAnrEnabled = true
options.isAttachAnrThreadDump = true
```

重点：

```text
release
必须和发包版本一致。当前格式类似：
com.example.hybriddemo@1.0.2+261020

environment
debug 包为 debug-local，release 包为 production。

auto session tracking
Crash Free Sessions / Crash Free Users 指标的数据来源。

ANR
isAnrEnabled 开启 ANR 监控；isAttachAnrThreadDump 用于附带线程栈。
```

`AndroidManifest.xml` 中关闭 Sentry 自动初始化：

```xml
<meta-data
    android:name="io.sentry.auto-init"
    android:value="false" />
```

原因：当前 DSN 从 `BuildConfig` 读取，而 Sentry 的默认 `SentryInitProvider` 会在 `Application.onCreate()` 前自动初始化，可能拿不到 DSN，导致启动异常。

## 7. Debug 与 Release 差异

Debug：

```text
environment = debug-local
tracesSampleRate = 1.0
profilesSampleRate = 1.0
attachScreenshot = true
attachViewHierarchy = true
anrReportInDebug = true
```

用于学习、验证、开发阶段排查。

Release：

```text
environment = production
tracesSampleRate = 0.05
profilesSampleRate = 0.01
attachScreenshot = false
attachViewHierarchy = false
anrReportInDebug = false
```

用于接近线上真实环境，控制性能和隐私成本。

## 8. 正式包签名与版本号

签名配置从 `local.properties` 或 CI Secret 读取：

```properties
RELEASE_STORE_FILE=D:\\android_keys\\androidHostForFlutter-release.jks
RELEASE_STORE_PASSWORD=xxx
RELEASE_KEY_ALIAS=androidHostForFlutter
RELEASE_KEY_PASSWORD=xxx
```

生成 keystore 脚本：

```text
scripts/generate-release-keystore.ps1
```

执行：

```powershell
cd D:\yupao_workspace\androidHostForFlutter
powershell -ExecutionPolicy Bypass -File .\scripts\generate-release-keystore.ps1
```

首页底部展示安装包真实版本：

```text
版本 1.0.2 (261020)
```

打包产物名携带版本号：

```text
AndroidHostForFlutter-v1.0.2-261020-release.apk
AndroidHostForFlutter-v1.0.2-261020-debug.apk
```

## 9. R8/ProGuard mapping 上传

Release 包开启：

```kotlin
isMinifyEnabled = true
isShrinkResources = true
proguardFiles(
    getDefaultProguardFile("proguard-android-optimize.txt"),
    "proguard-rules.pro"
)
```

Sentry 上传配置：

```kotlin
sentry {
    org.set(sentryOrg)
    projectName.set(sentryProject)
    authToken.set(sentryAuthToken)

    includeProguardMapping.set(true)
    autoUploadProguardMapping.set(sentryAuthToken.isNotBlank())
    includeSourceContext.set(false)
    telemetry.set(false)
}
```

打包时看到类似日志，说明上传成功：

```text
Task :app:uploadSentryProguardMappingsRelease
UPLOADED 1575cc31-895d-3b97-8d9e-9e8a836b18b6 (Proguard mapping)
```

Sentry 后台查看入口：

```text
Project Settings -> Debug Files / Debug Information Files
```

也可以通过 release 崩溃栈间接验证：如果开启 R8 后仍能看到原始类名、方法名和行号，例如：

```text
com.example.hybriddemo.sentry.SentryDemoActivity:56
```

说明 R8/ProGuard mapping 已经关联并生效。

注意：

```text
R8/ProGuard mapping
用于还原混淆后的堆栈。

Code Mapping
用于把堆栈文件映射到 GitHub/GitLab 源码位置，方便点击跳源码、识别可疑提交、辅助 Autofix。它不是反混淆必需项。
```

## 10. Code Mapping 是否必须

不是必须。

Code Mapping 的价值：

- 点击堆栈跳到远程仓库源码。
- 结合提交记录识别可能引入问题的 commit。
- 辅助 Issue Owner、Seer、Autofix。
- 多模块、多仓库、多人协作时降低定位成本。

但对于当前 Demo 或小项目：

```text
知道类名 -> 自己去仓库搜索源码
```

也能定位。因此线上更优先保证：

```text
release 准确
environment 准确
R8 mapping.txt 上传成功
crash/anr/session 正常上报
trace/profile 采样合理
```

## 11. 崩溃分析

崩溃主要看：

```text
Issues -> 具体 Issue -> Stack Trace
```

关注顺序：

1. `Unhandled` 还是 `Handled`
2. 异常类型，例如 `NullPointerException`
3. release / environment 是否正确
4. Stack Trace 中最靠上的业务帧
5. Breadcrumbs 中崩溃前用户做了什么
6. Screenshot / ViewHierarchy 是否提供了现场 UI
7. Tags / Contexts 中设备、系统、版本信息

典型结论模板：

```text
异常类型：NullPointerException
环境：production
版本：1.0.2 (261020)
触发页面：SentryDemoActivity
触发路径：用户点击 btnCrash
卡点/崩溃点：SentryDemoActivity.onCreate$lambda...
处理建议：对空对象做校验，或移除 Demo 崩溃入口。
```

## 12. ANR 分析

ANR 的核心思路：

```text
Breadcrumbs = 事发前用户/系统做了什么
thread-dump.txt = 事发瞬间各线程卡在哪里
```

Sentry 中 ANR 常见标识：

```text
ApplicationNotResponding
mechanism: AppExitInfo
level: fatal
```

### 12.1 先看 main thread

ANR 先看主线程：

```text
Stack Trace -> Threads -> main
```

如果 main thread 顶部有业务包名：

```text
com.example.hybriddemo
com.xxx.xxx
```

优先看最靠上的业务函数。

常见栈含义：

```text
Thread.sleep / SystemClock.sleep
主线程主动 sleep。

BinderProxy.transact
主线程同步 Binder 调用，可能等系统服务或其他进程。

FileInputStream / SQLite / SharedPreferences / Room / MMKV
主线程 IO 或数据库操作。

OkHttp / HttpURLConnection
主线程网络请求。

waiting to lock / locked
锁竞争，需要继续找持锁线程。

MessageQueue.next / Looper.loopOnce / __epoll_pwait
当前快照显示主线程在等消息，可能没抓到业务卡点，需要结合 Breadcrumbs、thread dump、其他 event、Perfetto。
```

### 12.2 Breadcrumbs 怎么用

Breadcrumbs 还原操作链路，例如：

```text
进入 SentryDemoActivity
点击 btnAnr
Schedule stable ANR demo, block_duration_ms=15000
连续点击其他按钮
ApplicationNotResponding
```

这说明：

```text
用户点击 btnAnr -> 安排 15 秒主线程阻塞 -> 阻塞期间输入事件无法处理 -> ANR
```

### 12.3 thread-dump.txt 怎么用

在附件中打开：

```text
Attachments -> thread-dump.txt
```

搜索：

```text
main
SentryDemoActivity
blockMainThreadForStableAnr
Thread.sleep
SystemClock.sleep
BinderProxy
waiting to lock
locked
```

如果 Breadcrumbs 已经指向某个按钮或页面，thread dump 用来找佐证：

```text
Breadcrumbs: UI Click btnAnr
thread dump: main thread 出现 SentryDemoActivity / Thread.sleep / blockMainThreadForStableAnr
结论：ANR 与 btnAnr 触发的主线程阻塞有关
```

如果 Breadcrumbs 没找到线索，thread dump 用来直接找卡点：

```text
1. 找 main 线程
2. 看 main 栈顶 20 行
3. 找最靠上的业务包名
4. 判断是 CPU、sleep、IO、Binder、锁等待还是 Looper polling
5. 如果是锁等待，搜索 waiting to lock / locked，找持锁线程
```

如果 main thread 只有：

```text
MessageQueue.next
Looper.loopOnce
__epoll_pwait
```

说明这份快照没有抓到业务函数。此时不能强行下结论，只能说：

```text
Sentry 证明发生过 ANR，但当前 main stack 无法定位业务卡点。
```

下一步应查看：

```text
其他 ANR events
thread-dump.txt
Android 系统 traces
Perfetto
logcat
业务埋点
```

## 13. 卡顿与性能分析

Sentry 中 Performance 现在主要在：

```text
Explore -> Traces
Explore -> Profiles
```

Traces 看：

- 页面加载耗时
- 手动 transaction
- span duration
- slow/frozen frames
- 哪个 transaction 慢

Profiles 看：

- CPU 函数采样
- flamegraph
- 哪个函数 self time / total time 高

常见字段：

```text
Self Time
函数自身消耗的 CPU 时间。

Total Time
函数自身 + 子调用总耗时。

Top Down
从入口调用链往下看。

Bottom Up
从最耗时函数反推调用来源。

Left Heavy
把最重路径靠左聚合，适合快速找热点。
```

定位卡顿函数：

```text
1. 进入 Profiles
2. 选择对应 transaction
3. 先看 main 线程
4. 切 Bottom Up 找 Self Time/Total Time 高的业务函数
5. 再切 Top Down 看它由哪个页面/点击路径触发
6. 对照 Breadcrumbs/Trace 确认用户操作
```

## 14. Profiles、P50、P75、P95、P99

Profiles 用于看 CPU 采样和火焰图，回答：

```text
CPU 时间花在哪些函数上？
哪个函数是热点？
主线程是否被业务计算打满？
```

百分位含义：

```text
P50
50% 样本小于等于这个值，中位数。

P75
75% 样本小于等于这个值。

P95
95% 样本小于等于这个值，常用于衡量慢用户体验。

P99
99% 样本小于等于这个值，代表极端慢 case。
```

例如 `Duration P95 > 2000ms` 表示：

```text
95% 的样本耗时不超过该值，但最慢 5% 已经超过 2 秒。
```

## 15. Monitor 类型说明

Sentry Monitor 用来持续观察数据，超过规则后创建 Issue，再通过 Alert 发通知。

### 15.1 Metric Monitor

监控指标异常。

适合：

```text
Crash Free Sessions
Crash Free Users
ANR 数量
错误数量
受影响用户数
接口耗时
Span failure rate
自定义业务指标
```

移动端线上质量监控最常用。

### 15.2 Error Monitor

Sentry 根据错误/崩溃自动创建，不是在新建 Monitor 页面手动创建。

适合：

```text
NullPointerException
IllegalStateException
ApplicationNotResponding
OOM
```

### 15.3 Mobile Build

监控移动端构建产物体积和体积回归。

适合：

```text
APK/AAB 变大
dex 变大
资源变大
so 变大
下载体积异常
```

### 15.4 Cron

监控定时任务是否按时执行、是否失败、是否超时。更适合服务端、脚本、CI 定时任务。

### 15.5 Uptime

监控 HTTP endpoint 是否可用。适合后端接口健康检查，不是直接监控 Android App 本体。

## 16. Metric 指标类型说明

### 16.1 Errors

```text
Number of Errors
错误事件数量。

Users Experiencing Errors
受错误影响的用户数。
```

### 16.2 Spans

```text
Throughput
吞吐量，某类 span/transaction 出现次数。

Duration
耗时，通常看 avg、p95、p99。

Failure Rate
失败率。

Largest Contentful Paint
Web 前端 LCP 指标，Android 原生一般不用。
```

### 16.3 Logs

日志数量或日志过滤结果。适合接入 Sentry Logs 后按 level/message 监控。

### 16.4 Application Metrics

应用自定义指标或 SDK 自动采集指标。

### 16.5 Releases

```text
Crash Free Session Rate
无崩溃会话率。

Crash Free User Rate
无崩溃用户率。
```

Android 线上质量最常用：

```text
Crash Free Session Rate < 99.9%
Crash Free User Rate < 99.9%
Number of Errors > N
Users Experiencing Errors > N
Duration P95 > N ms
Failure Rate > N%
```

## 17. Issue Detection 三种策略

Metric Monitor 中的 Issue Detection 决定什么时候创建 Issue。

### 17.1 Threshold

固定阈值。

例如：

```text
5 分钟内 ANR 数 > 10 -> High
5 分钟内 ANR 数 > 3 -> Medium
5 分钟内 ANR 数 <= 3 -> Resolve
```

适合明确红线：

```text
ANR 数
Crash Free Rate
错误数
接口 P95
```

### 17.2 Change

变化比例阈值。

例如：

```text
过去 5 分钟错误数比前 1 小时上涨 200%
```

适合抓突增/突降，但低基线时容易误报。

### 17.3 Dynamic

动态异常检测。Sentry 根据历史数据、均值、波动和周期性判断异常。

适合：

```text
请求量
日志量
接口耗时
有明显日周期/周周期的指标
```

数据刚接入、历史样本少时不建议优先使用。

## 18. ANR 预警配置

推荐使用 Metric Monitor：

```text
Monitors -> Create Monitor -> Metric
```

ANR 数量预警：

```text
Dataset: Errors
Visualize: Number of Errors / count
Interval: 5 minutes
Filter: error.type is ApplicationNotResponding
Issue Detection: Threshold
High: Above 10
Medium: Above 3
Resolve: Below or equal 0 或 3
```

ANR 影响用户数预警：

```text
Dataset: Errors
Visualize: Users Experiencing Errors
Interval: 5 minutes
Filter: error.type is ApplicationNotResponding
High: Above 5
Medium: Above 2
Resolve: Below or equal 0 或 1
```

如果 Filter 里选不到 `ApplicationNotResponding`：

```text
说明项目可能还没有上报过 ANR，Sentry 的筛选候选通常来自已有事件。
```

解决方式：

```text
1. 先制造一次 ANR，让 Sentry 出现 ApplicationNotResponding 事件。
2. 回到 Monitor 里选择 error.type is ApplicationNotResponding。
3. 如果 UI 允许，也可以手动输入。
```

## 19. Crash Rate / Crash Free 预警配置

“Crash 率 > 万分之三”等价于：

```text
Crash Free Sessions < 99.97%
```

常见配置：

```text
Dataset: Releases
Visualize: crash_free_rate
Dimension: session
Interval: 1 hour
Filter: environment is production
High: Below 99.97
Medium: Below 99.99
Resolve: Above or equal 99.97 / 99.99
```

如果只是演示，可以用更容易触发的阈值：

```text
Crash Free Sessions < 97%
```

线上不要只看 `Number of Errors`，因为错误数受用户量影响很大。Crash Free Session/User Rate 更适合衡量版本质量。

## 20. Alert 和 Monitor 的关系

Monitor 负责发现问题并创建 Monitor Issue。

Alert 负责通知：

```text
Email
Slack
Webhook
GitHub
```

关键点：

```text
Assignee 不等于邮件收件人。
Team 只有你，也不代表一定会发邮件。
Metric Monitor 必须在 Connected Alerts 中绑定 Email Alert，才会稳定发邮件。
Project Alerts 只是项目级 Issue Alert，不等于当前 Monitor 的专属 Alert。
```

推荐在每个重要 Metric Monitor 中配置：

```text
Connected Alerts -> Create a New Alert
THEN Send notification via Email to Member: crixalis
Action Throttle: 测试阶段 every trigger，线上 30min/1h
```

如果新建 Alert 后没有立刻收到邮件：

```text
旧的 ongoing issue 不一定补发。
需要下一次新建 issue、resolved 后再次触发，或使用 Send Test Notification 验证邮件链路。
```

## 21. Release Adoption 指标

Releases 页面中的 `Adoption` 是版本采用率。

当右上角显示：

```text
Display: Sessions
```

Adoption 表示：

```text
当前 release 的活跃 session 数 / 所选时间范围内全部 release 活跃 session 数
```

当切换到：

```text
Display: Users
```

Adoption 表示：

```text
当前 release 的用户数 / 全部 release 用户数
```

用途：

```text
判断新版本是否铺开
灰度发布时看覆盖比例
结合 Crash Free Rate 判断是否继续放量
发现旧版本是否仍有大量用户
```

例如：

```text
1.0.2 Adoption 2%，Crash Free Rate 50%
```

说明新版本刚开始铺开，但质量很差，应暂停继续放量。

## 22. 线上推荐监控策略

### 22.1 必配 Monitor

```text
Crash Free Sessions < 99.9% / 1h
Crash Free Users < 99.9% / 1h
ANR count > 10 / 5min
ANR affected users > 5 / 5min
Fatal error count > N / 10min
Users experiencing errors > N / 10min
```

### 22.2 性能类 Monitor

```text
关键页面 load duration P95 > 2s
关键接口 span duration P95 > 2s
关键 span failure rate > 5%
```

### 22.3 版本发布观察

每次 release 后重点看：

```text
Adoption
Crash Free Session Rate
Crash Free User Rate
New Issues
Crashes
ANR
Slow/Frozen Frames
Profiles 中 main 线程热点
```

灰度策略示例：

```text
Adoption 1% -> 观察 30-60 分钟
Crash Free Sessions 正常 -> 放到 5%
继续观察 -> 20% -> 50% -> 100%
任何阶段 Crash Free Rate 明显下降或 ANR 暴涨 -> 暂停/回滚
```

## 23. 线上采样建议

初始建议：

```properties
SENTRY_TRACES_SAMPLE_RATE=0.05
SENTRY_PROFILES_SAMPLE_RATE=0.01
SENTRY_ATTACH_SCREENSHOT=false
SENTRY_ATTACH_VIEW_HIERARCHY=false
```

流量较大时可降低：

```properties
SENTRY_TRACES_SAMPLE_RATE=0.01
SENTRY_PROFILES_SAMPLE_RATE=0.002
```

注意：

```text
tracesSampleRate = 0.05
表示 5% transaction 进入 Sentry。

profilesSampleRate = 0.01
表示已采样 transaction 中 1% 继续采集 CPU Profile。
```

## 24. 常见问题

### 24.1 崩溃能收到，Metric Monitor 邮件收不到

检查：

```text
Metric Monitor -> Connected Alerts 是否为空
Alert action 是否是 Email
收件人是否固定为 Member: crixalis
是否只配置了 Project Alert，而不是 Monitor Connected Alert
是否已有 ongoing issue，导致新建 alert 后不会补发旧邮件
```

### 24.2 ANR Filter 里选不到 ApplicationNotResponding

原因：

```text
项目还没有上报过 ANR 类型事件，候选值来自已有数据。
```

处理：

```text
先制造一次 ANR，确认 Issues 里出现 ApplicationNotResponding，再创建 Monitor。
```

### 24.3 main thread 栈看不到业务函数

如果看到：

```text
MessageQueue.next
Looper.loopOnce
__epoll_pwait
```

说明当前快照没有抓到业务卡点。需要结合：

```text
Breadcrumbs
thread-dump.txt
其他 event
Perfetto
logcat ANR traces
业务埋点
```

### 24.4 Code Mapping 会不会泄露源码

Code Mapping 本身是源码位置映射，通常不会直接上传整个源码。但如果接入源码仓库、开启 Source Context 或授权 Sentry 访问仓库，Sentry 会获得相应代码访问能力。

线上建议：

```text
先保证 R8 mapping 上传。
Code Mapping 按团队需要再配置。
Source Context 默认关闭。
```

### 24.5 截图功能线上要不要开

默认不建议全量开启。

原因：

```text
可能包含用户隐私
增加事件体积
增加上传成本
```

可选策略：

```text
debug 开启
线上关闭
必要时小流量或特定事件 beforeSend 过滤后开启
```

## 25. 排查清单

每次发正式包前：

```text
1. versionName / versionCode 是否正确
2. 首页版本号是否与打包版本一致
3. release = applicationId@versionName+versionCode 是否符合预期
4. environment 是否为 production
5. R8 是否开启
6. mapping.txt 是否生成
7. uploadSentryProguardMappingsRelease 是否成功
8. Sentry Debug Files 是否有 Proguard mapping
9. release 包崩溃是否能反混淆
10. Crash Free Sessions 是否有数据
11. Metric Monitor 是否有 Connected Alerts
12. Email Alert 是否固定到具体 Member
```

问题分析时：

```text
崩溃：Stack Trace -> 最靠上的业务帧 -> Breadcrumbs -> Tags/Contexts
ANR：main thread -> Breadcrumbs -> thread-dump.txt -> 其他 event -> Perfetto
卡顿：Traces -> Profiles -> main thread -> Bottom Up/Top Down -> 业务函数
版本质量：Releases -> Adoption -> Crash Free Rate -> New Issues -> Crashes
```
