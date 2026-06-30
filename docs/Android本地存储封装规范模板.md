# Android 本地存储封装规范模板

## 1. 文档目标

这份文档面向正式项目，而不是只解释 demo。

目标是沉淀一套可落地的 Android 本地存储封装规范，用来回答五个问题：

1. `MMKV`、`DataStore`、`Room` 分别适合承载什么数据。
2. 为什么不能在页面层直接散写本地存储。
3. 正式项目中的 `LDS / Repository / Store` 边界应该如何划分。
4. 当前鱼泡项目封装方案哪些地方合理，哪些地方需要演进。
5. `android_host` demo 中应如何提供可运行、可迁移、可复用的标准模板。

本文档默认适用于以下场景：

- 大型存量 Android 工程
- 原生、Flutter、RN 同时存在本地缓存诉求
- 简单 KV、响应式配置、结构化数据同时存在
- 项目不能推翻重做，只能通过规范和模板逐步收口

## 2. 总体结论

正式项目建议采用以下默认策略：

- 简单 KV、高频同步读取、跨端共享数据优先 `MMKV`
- 需要 `Flow` 监听、偏好配置、用户状态流优先 `DataStore`
- 结构化、多表、查询、事务、可迁移数据优先 `Room`
- 非持久化临时数据使用内存缓存，例如 `LruCache` 或生命周期明确的变量缓存
- 页面层禁止直接调用底层存储，统一收敛到 `LDS / Repository`

可以把它理解成一条明确的选型线：

`MMKV = 高性能轻量 KV`

`DataStore = 可观察偏好状态`

`Room = 结构化关系数据`

`LruCache / Memory = 进程内临时缓存`

## 3. 存储方案职责边界

## 3.1 MMKV

### 推荐使用场景

- 登录态、Token、用户基础信息快照
- AB 实验、本地开关、轻量业务状态
- Flutter / RN / Native 需要共享的简单 KV
- 高频同步读取的数据
- 需要按业务域隔离的缓存文件

### 不推荐使用场景

- 多表关联查询
- 大批量列表数据
- 需要复杂迁移的数据
- 需要事务一致性的业务数据
- 需要天然响应式监听的数据流

### 正式项目封装边界

- 底层实现可以使用 `MMKV.defaultMMKV()` 或 `MMKV.mmkvWithID(id)`
- 业务不得直接散写字符串 key
- key 应该由业务域统一管理，例如 `AccountKeys`、`RecruitDraftKeys`
- 对象写入必须统一序列化策略，例如 Gson / kotlinx.serialization
- 用户隔离必须明确是“文件隔离”还是“key 前缀隔离”
- 登出时必须有明确的清理范围

### 推荐封装形态

```kotlin
interface KvStore {
    fun putString(key: String, value: String)
    fun getString(key: String, default: String? = null): String?
    fun remove(key: String)
    fun clear()
}

class MmkvKvStore(
    namespace: String,
) : KvStore {
    private val mmkv = MMKV.mmkvWithID(namespace)
}
```

正式项目里 `namespace` 应该是稳定常量，不能由页面临时拼接。

## 3.2 DataStore

### 推荐使用场景

- 需要 `Flow` 持续观察的数据
- 用户偏好设置
- 首页 tab、弹窗状态、配置开关
- 账号、角色、IM 配置等状态流
- 原 SharedPreferences 的渐进替代

### 不推荐使用场景

- 高频同步读取
- 大对象、大列表、复杂 JSON 集合
- 复杂查询或多表关系
- 跨端直接共享的核心数据

### 正式项目封装边界

- 页面层不直接操作 `DataStore<Preferences>`
- 不使用“写空字符串”模拟删除
- 必须提供 `remove(key)` 和 `clear()`
- 异常必须有日志或上报，不允许静默吞掉
- 所有 key 必须通过类型安全对象声明
- 复杂对象必须统一序列化和反序列化策略

### 推荐封装形态

```kotlin
data class PreferenceKey<T>(
    val name: String,
    val defaultValue: T,
)

interface PreferenceStore {
    fun <T> observe(key: PreferenceKey<T>): Flow<T>
    suspend fun <T> read(key: PreferenceKey<T>): T
    suspend fun <T> write(key: PreferenceKey<T>, value: T)
    suspend fun <T> remove(key: PreferenceKey<T>)
    suspend fun clear()
}
```

这种封装的目标不是把 DataStore 包得很厚，而是把“key、默认值、删除、异常处理”收口。

## 3.3 Room

### 推荐使用场景

- IM 会话、消息、联系人
- 地区库、CMS 资源、招聘草稿
- 水印、历史记录、复杂列表缓存
- 需要查询、排序、分页、事务的数据
- 需要版本迁移的数据

### 不推荐使用场景

- 单个布尔开关
- 简单字符串配置
- 高频极简 KV
- 跨端共享的轻量状态

### 正式项目封装边界

- UI 不直接访问 DAO
- Repository 对外暴露业务语义，不暴露表结构细节
- 数据库必须明确 `DB_NAME`、`version`、`exportSchema`
- 每次版本升级必须提供 migration 或明确 destructive 策略
- 预置数据库必须明确资产版本、删除重建规则和异常恢复
- Migration 应该有测试，至少覆盖主升级路径

### 推荐封装形态

```kotlin
@Database(
    entities = [DraftEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
}

object AppDatabaseProvider {
    private const val DB_NAME = "app_storage.db"

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
```

## 4. LDS / Repository 分层规范

正式项目建议把本地存储收敛到以下结构：

```text
UI / ViewModel
    ↓
Repository
    ↓
LDS(Local Data Source)
    ↓
MMKV / DataStore / Room
```

各层职责如下：

- `UI`：只关心展示和用户事件，不知道底层存储方案。
- `ViewModel`：组合状态，不直接拼 key，不直接访问 MMKV / DataStore / DAO。
- `Repository`：决定读远端还是读本地，处理缓存策略。
- `LDS`：唯一允许直接调用本地存储封装的业务层。
- `Store / DAO`：只提供存储能力，不包含业务流程。

## 5. 当前鱼泡项目方案评估

## 5.1 MMKV / yupao-storage

合理点：

- 以 MMKV 作为轻量 KV 底座，适合移动端高频读写。
- `StorageFactory.createOptWithID(id)` 支持业务域隔离。
- `SharedStorage` 引入了 `FILE / MEMORY` 和 `CURR_USER / ALL_USER` 的统一语义。
- Flutter / RN 可以通过 Native API 使用同一套缓存语义。

需要演进点：

- `createKeyOpt` 依赖动态代理和注解，编译期约束弱。
- key 命名和对象序列化仍然依赖业务自觉。
- `cache.config.StorageConfig.cacheStrategy` 使用 `by lazy`，必须保证首次访问前完成初始化。
- 需要补齐 key 清理、用户切换、跨端一致性测试规范。

建议：

- 保留 MMKV 主力地位。
- 新业务优先通过 LDS 暴露业务方法。
- 字符串 key 不进入 Activity / Fragment / Composable。
- 复杂对象统一 JSON adapter，不在各业务散写 Gson 调用。

## 5.2 DataStorePreference

合理点：

- 用 DataStore 承接账号、角色、IM、CMS、招聘配置等可观察状态。
- 封装了基础 `Flow` 读取和 suspend 写入。
- 比继续扩散 SharedPreferences 更适合现代 Kotlin 数据流。

需要演进点：

- `runCatching` 吞异常，缺少日志和上报。
- 缺少单 key 删除能力。
- 业务清理常用空字符串替代删除，语义不清晰。
- 类型 key 没有集中声明，默认值策略分散。
- 复杂对象序列化散落在业务层。

建议：

- 增加 `remove(key)`。
- 增加统一异常处理。
- 使用类型安全 key 声明。
- 将 JSON 对象读写封装成业务 LDS 方法。

## 5.3 Room

合理点：

- 用于 IM、地区库、CMS、招聘、水印等结构化数据，选型正确。
- 多数模块通过 Hilt Provider 管理数据库实例。
- 部分模块已有 migration 和预置数据库处理。

需要演进点：

- 各业务数据库 Provider 风格不统一。
- 部分数据库缺少 migration 策略说明。
- 部分模块没有明确 destructive migration 是否可接受。
- schema 导出和 migration test 规范需要统一。

建议：

- 新数据库必须使用统一 Provider 模板。
- 每次版本升级必须补 migration 或说明可丢弃原因。
- 业务层只能依赖 Repository，不直接依赖 DAO。

## 6. 选型决策表

| 数据类型 | 推荐方案 | 原因 |
|---|---|---|
| Token / 用户 ID / 简单身份态 | MMKV | 同步读取快，跨端共享成本低 |
| AB 实验命中结果 | MMKV 或 DataStore | 高频同步读用 MMKV，需要观察变化用 DataStore |
| 设置页开关 | DataStore | 适合 Flow 监听和偏好配置 |
| 弹窗展示状态 | DataStore 或 MMKV | 需要观察用 DataStore，只做轻量记录用 MMKV |
| IM 会话列表 | Room | 结构化查询和迁移 |
| 地区库 | Room | 预置 DB、查询、版本升级 |
| 招聘草稿 | Room | 字段多、可查询、可迁移 |
| 页面内临时曝光 ID | 内存变量 | 生命周期短，不需要持久化 |
| 图片 Bitmap | LruCache | 大对象，需要容量淘汰 |

## 7. 反模式

以下写法不建议在正式项目继续扩散：

```kotlin
// 页面层直接散写 key
StorageFactory.createDefaultOpt().putString("user_token", token)
```

```kotlin
// 用空字符串模拟删除
dataStore.saveData(context, "accountBasic", "")
```

```kotlin
// Activity 直接访问 DAO
val list = database.draftDao().queryAll()
```

```kotlin
// Map 无限增长，没有清理策略
private val cache = mutableMapOf<String, Bitmap>()
```

## 8. 推荐模板：MMKV

```kotlin
object RecruitDraftKeys {
    const val NAMESPACE = "recruit_draft"
    const val LAST_DRAFT_ID = "last_draft_id"
}

class RecruitDraftLds(
    private val kvStore: KvStore,
) {
    fun saveLastDraftId(id: String) {
        kvStore.putString(RecruitDraftKeys.LAST_DRAFT_ID, id)
    }

    fun getLastDraftId(): String? {
        return kvStore.getString(RecruitDraftKeys.LAST_DRAFT_ID)
    }

    fun clearLastDraftId() {
        kvStore.remove(RecruitDraftKeys.LAST_DRAFT_ID)
    }
}
```

## 9. 推荐模板：DataStore

```kotlin
object RecruitPreferenceKeys {
    val AutoSaveDraft = PreferenceKey(
        name = "auto_save_draft",
        defaultValue = true,
    )
}

class RecruitPreferenceLds(
    private val preferenceStore: PreferenceStore,
) {
    fun observeAutoSaveDraft(): Flow<Boolean> {
        return preferenceStore.observe(RecruitPreferenceKeys.AutoSaveDraft)
    }

    suspend fun setAutoSaveDraft(enabled: Boolean) {
        preferenceStore.write(RecruitPreferenceKeys.AutoSaveDraft, enabled)
    }
}
```

## 10. 推荐模板：Room

```kotlin
class RecruitDraftRepository(
    private val dao: RecruitDraftDao,
) {
    fun observeDrafts(): Flow<List<RecruitDraftEntity>> {
        return dao.observeAll()
    }

    suspend fun saveDraft(entity: RecruitDraftEntity) {
        dao.upsert(entity)
    }

    suspend fun deleteDraft(id: String) {
        dao.deleteById(id)
    }
}
```

Repository 负责业务语义，DAO 负责数据库操作。页面和 ViewModel 不直接依赖 DAO。

## 11. android_host demo 落地目标

`android_host` 中的 demo 应该提供一套优化后的正式项目模板：

- `MMKV`：提供 namespace 隔离、类型安全入口、remove/clear 能力。
- `DataStore`：提供类型安全 key、Flow observe、read/write/remove/clear 能力。
- `Room`：提供 Entity、DAO、Database、Provider、Repository 的完整链路。
- `UI`：提供一个可运行页面展示三套存储的读写结果。
- `docs`：说明它们分别对应鱼泡正式项目中的哪些使用场景。

目标不是替代鱼泡项目所有存量封装，而是给新业务提供一份更稳的默认模板。

## 12. 演进建议

短期建议：

- 新增本地缓存需求必须先判断属于 `MMKV / DataStore / Room / Memory` 哪一类。
- 新页面禁止直接操作底层存储。
- 新增 key 必须归属到明确业务域。
- 新增 Room 表必须同步 migration 策略。

中期建议：

- 将散落的 MMKV key 收敛到业务 LDS。
- 为 DataStore 补齐 remove 和异常日志。
- 统一 Room Provider 模板。
- 补充核心数据库 migration test。

长期建议：

- Native / Flutter / RN 共用同一份存储语义文档。
- 跨端缓存 key 使用统一协议生成。
- 用户切换、登出、隐私授权撤回时有统一清理矩阵。
- 本地存储访问纳入 lint 或 code review checklist。

## 13. Code Review Checklist

- 是否在 UI 层直接访问 MMKV / DataStore / DAO？
- key 是否有明确业务域？
- 是否有 remove / clear 语义？
- 用户维度数据是否正确隔离？
- Room 是否有 migration 或明确 destructive 策略？
- DataStore 是否有默认值和异常处理？
- 对象序列化是否统一？
- 内存缓存是否有容量或生命周期边界？
- 跨端共享数据是否有协议说明？
- 登出和切换账号时是否清理正确范围？
