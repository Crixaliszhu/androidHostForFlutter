# AIDL 同应用双进程通信示例

入口：宿主的 **系统服务功能详解 → 进程通信：Messenger / AIDL → AIDL 双进程通信完整示例**。

本示例在同一 APK 内运行两个参与通信的进程：Activity 所在的主进程，以及 `:aidl_demo` 服务进程。页面显示两端 PID，便于确认发生了真正的跨进程调用。已有 Messenger 示例仍可单独运行。

## 代码与调用链

| 文件 | 职责 |
| --- | --- |
| `app/src/main/aidl/com/example/hybriddemo/ipc/aidl/IAidlDemoService.aidl` | 双方共享的服务接口、事务编号、业务错误码 |
| 同目录 `IAidlDemoCallback.aidl` | 服务端调用客户端的 oneway 回调 |
| 同目录 `IpcRecord.aidl` | 自定义 Parcelable 声明 |
| 同目录 `TaskReceipt.aidl` | 任务受理回执，显式携带业务错误码 |
| `app/src/main/java/com/example/hybriddemo/ipc/aidl/IpcRecord.java` | 对象序列化和 out/inout 回写 |
| 同目录 `AidlDemoService.kt` | Stub、身份检查、会话和任务管理、FD 传输 |
| 同目录 `AidlDemoClient.kt` | 绑定状态机、死亡监听、重连、超时和取消 |
| 同目录 `AidlDemoActivity.kt` | 可操作场景、两端 PID 和实验日志 |
| `app/src/androidTest/java/com/example/hybriddemo/ipc/aidl/AidlDemoInstrumentedTest.kt` | 真实 Binder 的设备测试 |

```text
主进程 Activity
  → AidlDemoClient.call（Dispatchers.IO）
  → 生成的 Proxy：写入 Parcel
  → Binder 驱动
  → :aidl_demo 的 Stub：读出 Parcel、执行方法
  → 同步返回结果 / 通过 IAidlDemoCallback 异步通知
  → 客户端 Binder 线程接收回调
  → 主线程检查连接身份、完成等待者并更新页面
```

`Stub.asInterface()` 在不同进程间返回 Proxy；同进程调用可能直接返回 Stub。后者不会经历序列化，不能用它验证参数方向、线程切换和异常传播。

## 按钮与预期结果

| 操作 | 预期结果 |
| --- | --- |
| 连接 / 重试 | BINDING → CONNECTED，两端 PID 不同 |
| 基本类型、Parcelable、null、分页 | `add(3,5)=8`；echo 字段相同、对象引用不同；null 原样返回；按页获取记录 |
| 验证 in / out / inout | in 保留原值；out 被服务端填充；inout 在传入值基础上更新 |
| 20 次并发累加 + 回调 | 无其他写入时计数增加 20，每次返回一个不同的计数值；日志显示 Binder 回调线程 |
| 提交 3 秒异步任务 | submitTask 只确认受理，约 3 秒后收到 STATUS_OK |
| 取消当前任务 | 尚未完成时返回 true，并收到 STATUS_CANCELLED；已经结束则返回 false |
| 3 秒任务只等 500ms | 等待超时，随后尝试 cancelTask；查询快照可见任务清理情况 |
| 非法参数 / 业务异常 | 显示远端 IllegalArgumentException 和 TaskReceipt 业务错误码；超长对象在客户端发送前被拒绝；后续加法仍成功 |
| 通过 FD 读取 256 KiB 数据 | 输出 262144 字节，数据经文件读取，不占用 Binder 的大块参数空间 |
| 终止远程进程并自动重连 | Debug 中仅终止服务所在进程；旧等待者失败，重连后 PID 改变、回调重新注册、内存计数清零 |
| 查询服务状态 | 显示 PID、计数、注册回调数、未完成任务数、服务端执行线程 |
| 主动断开 | 释放绑定和回调，状态变为 DISCONNECTED，不自动重连 |

页面不可见时会取消本页操作并解绑；返回页面重新连接。旋转屏幕也会创建新连接，日志和内存任务不跨 Activity 重建保留。

## 连接状态与生命周期

```text
DISCONNECTED --connect--> BINDING --握手成功--> CONNECTED
                              │                    │
                        超时/绑定失败          Binder 死亡
                              └──── RETRY_WAIT ────┘
                                       │
                                    重新绑定

任何状态 --disconnect--> DISCONNECTED
任何状态 --close-------> CLOSED
```

- `bindService()` 返回 true 只表示系统接受绑定，客户端在 `onServiceConnected` 内完成版本检查、`linkToDeath` 和回调注册后才开放调用。
- 每次绑定都有独立 Attempt、ServiceConnection、回调 Stub 和 DeathRecipient。旧 Binder 的迟到回调会被身份检查丢弃。
- `onServiceDisconnected` 和 DeathRecipient 可能先后到达，只有当前 Attempt 能触发一次恢复。
- `onBindingDied` 必须释放旧绑定再重新绑定。本例统一自行解绑并重试，不同时依赖系统原绑定重连。
- 绑定/握手超过 5 秒或 `bindService=false` 时按 0.5、1、2、4、8 秒退避，最多 5 次连续重试；成功后重置计数。
- `onNullBinding`、权限拒绝和协议版本不匹配停止自动重试，输出诊断。正常解绑不会收到 `onServiceDisconnected`。
- 主动 disconnect/close 会取消待重试任务；close 后禁止再次 connect。
- onStop/onDestroy 做显式资源清理。直接终止进程时，Application、Service 的 onDestroy 不保证执行，Binder 死亡机制才是对端清理的依据。

## 线程、回调与背压

同步业务方法通过 `call` 在 IO 线程运行。Binder 线程并不等于 Service 主线程；Service 的生命周期方法仍由主线程调用。计数器使用 AtomicLong；会话和任务表使用同一个锁保护。服务端不持有业务锁调用客户端代码，避免双向 Binder 调用导致锁重入/死锁。

回调接口使用 `oneway`。这表示跨进程调用不等待客户端处理完成，不提供可靠投递、持久化或无限吞吐保证；同进程调用也不会因此自动切线程。

RemoteCallbackList 负责跟踪回调 Binder 和客户端死亡。重复注册同一个 Binder 是幂等操作；每个回调 Binder 对应一个会话，最多 16 个会话、每个会话最多 16 个待处理任务。客户端死亡或主动注销会清理该会话的任务。

延迟工作由 2 个调度线程执行。所有计数广播使用单个事件线程，`beginBroadcast/finishBroadcast` 成对调用，避免并行广播破坏快照；通知队列最多 128 项。极端背压下通知可丢弃，业务应通过查询快照或超时恢复。并发计数通知不保证按数值递增到达，计数真值以服务端快照为准。

## 超时、取消和结果不确定性

本例有意区分三种操作：

1. **同步 Binder 方法**：如 add、registerCallback。放在 IO 线程能避免卡 UI，但给外层加协程超时无法保证及时中断已经进入的 transact。
2. **短提交 + 异步回调**：submitTask 快速确认受理，客户端从提交返回后开始计时，等待回调的超时不包含握手或提交所花的时间。
3. **显式取消协议**：等待超时或协程取消后，使用同一个会话和 requestId 尽力 cancelTask。取消本身也是同步 Binder 调用；若远端完全卡死，不能承诺一个严格的端到端截止时间。

参数错误通过 Android 支持封送的 IllegalArgumentException 返回；任务拒绝通过 TaskReceipt 显式传递错误码，客户端可将回执转为本地 TaskRejectedException。自定义异常类不会直接在 Binder 两端传播。

完成与取消竞争时，以服务端先移除任务登记的一方为准。任务对象还有独立身份令牌，防止旧执行回调误删取消后同名的新任务。客户端示例使用 UUID；不要复用 requestId 来区分旧的迟到业务回调。

`DeadObjectException`、`RemoteException` 或 `TransactionTooLargeException` 不能证明写操作未执行。因此连接恢复**不重放**计数累加或任务提交。重复 requestId 仅在当前会话的任务仍待执行时被拒绝；这不是持久化幂等或 exactly-once 保证。生产写操作需要持久化请求编号、去重结果和状态查询协议。

## 参数、大数据与安全

- Parcelable 的读写字段顺序一致，并提供公开的无参构造和 `readFromParcel`，以支持生成代码回写 out/inout。不同进程不共享对象引用或普通静态变量。
- echo 显式支持 nullable；示例的其他对象参数约定为非 null。
- 文本最多 2048 字符，列表最多返回 20 条。本例的高层 echo 方法先校验再发送，服务端再次验证。学习用的底层 `call` 暴露完整接口，调用者仍需遵守协议大小约束。
- Binder 事务缓冲区由进程中的在途事务共享，不能把常见的约 1 MiB 上限当成每次请求可安全使用的固定额度。不要为了演示主动发送超大事务。
- 大数据示例由服务端打开固定缓存文件并返回只读 ParcelFileDescriptor，客户端用 AutoCloseInputStream 关闭流及 FD。实际业务可按场景使用 URI、管道或共享内存。若额外创建本地 FD 副本，持有方也必须关闭自己的副本。
- Service 和 Activity 都是 `exported=false`；每个 Binder 业务入口还检查调用 UID。UID 必须在切线程前读取。这里的权限模型是同一应用内部信任，不将不同回调会话当成安全隔离边界。
- 模拟死亡仅在 Debug 开放，并只调用服务进程自身的 `Process.myPid()`；Release 调用会收到 SecurityException。
- 新进程会独立创建 Application。DemoApplication 对 `:aidl_demo` 提前返回，避免重复初始化 Flutter、ARouter 和主进程监控；其他已有进程行为保留。主包和马甲包均开启 AIDL，马甲包同时复用接口源目录。
- 本协议用于同一 APK 的两端，提供版本握手和固定事务编号；手写 Parcelable 不承诺跨版本字段兼容。跨应用发布时需要另行设计版本演进和权限机制。

## 编译与验证

在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app_vest:compileDebugKotlin
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.hybriddemo.ipc.aidl.AidlDemoInstrumentedTest"
```

设备测试要求已授权的 Android 设备或兼容当前 APK ABI 的模拟器。主包当前只配置 ARM ABI，不能假定 x86 模拟器一定能加载 Flutter 原生库。

12 个设备测试覆盖：真实 PID/UID 与对象拷贝、参数方向、原子并发写、业务错误隔离、零延迟和延迟回调、超时和协程取消、双客户端同名任务隔离、重复注册/重复任务/队列上限、回调线程和注销、FD 内容、服务死亡后恢复且不重放、主动断开。

下列分支需要额外故障注入或人工检查，不能由上面的正常绑定测试替代：`bindService=false`、`onNullBinding`、安装更新触发的 `onBindingDied`、权限拒绝、客户端进程被系统杀死、通知队列极端背压、系统级 TransactionTooLargeException。实现中有对应处理或约束，但设备测试没有伪造系统行为来宣称已覆盖。

人工回归建议：

1. 连续进出页面、旋转屏幕、提交后马上返回，再进入查询 pendingTasks 与 callbacks，确认没有旧任务/重复回调。
2. 提交长任务后终止远程进程，确认等待者失败、新 PID 建立、回调恢复且计数重置。
3. 快速反复连接、断开，确认不出现 ServiceConnection 泄漏或主动断开后自动重连。
4. 运行 FD 场景多次，确认能持续读取；客户端流和描述符按作用域关闭。

## 本次验证记录（2026-09-08）

- 主应用 `assembleDebug` 和 `assembleDebugAndroidTest` 成功，已生成应用 APK 和测试 APK；测试源码已编译。
- 本次修改文件的 `detektChanged` 检查通过；全量 `:app:detekt` 被已有的 18 个 DataClassContract（缺少 `@Keep`）问题阻断，报告没有涉及新增 AIDL 文件。
- 已从生成的 APK 检查 Manifest，确认服务进程为 `:aidl_demo`，服务和 Activity 均为 `exported=false`。
- 马甲包 `compileDebugAidl` 成功；其完整 Kotlin 编译被既有共享存储源码缺失依赖导致的 KAPT 错误阻断（`IRecruitDraftKv` 的 `local_mmkv` 注解无法解析）。本示例未调整其他功能的依赖。
- 当前 `adb devices` 无设备，且没有已配置的 AVD，因此 12 个设备测试尚未运行，也未宣称真机交互验证通过。

本机 JDK 17 曾在启动 Gradle 时出现 `Unable to establish loopback connection`。本次仅在构建进程设置下面的临时目录参数后继续，未修改项目全局 Gradle 配置：

```powershell
New-Item -ItemType Directory -Force build\aidl-check\tmp | Out-Null
$aidlTemporaryDir = (Resolve-Path build\aidl-check\tmp).Path
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$aidlTemporaryDir"
```

## 官方参考

- [AIDL 接口、参数和线程规则](https://developer.android.com/develop/background-work/services/aidl)
- [ServiceConnection 生命周期](https://developer.android.com/reference/android/content/ServiceConnection)
- [RemoteCallbackList](https://developer.android.com/reference/android/os/RemoteCallbackList)
- [IBinder 与死亡监听](https://developer.android.com/reference/android/os/IBinder)
