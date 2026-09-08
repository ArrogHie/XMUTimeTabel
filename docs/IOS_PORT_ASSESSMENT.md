# iOS 移植评估与实施计划

日期：2026-09-08  
评估对象：`WakeUpPure` / 厦大课表 Android 项目  
评估结论：**可以做成 iOS 版，但不是在现有 Android 工程上做小范围适配，而是“共享领域逻辑 + iOS 原生壳 + iOS 系统能力重写”的中大型移植项目。**

## 1. 当前项目基线

仓库目前没有 iOS、Swift、Xcode 或 Kotlin Multiplatform 工程；只有一个 Android Gradle application module。主源码约 91 个 Kotlin 文件、23,524 行，测试和 Android 资源另计。

关键证据：

- `app/build.gradle.kts:14-178` 使用 Android Gradle Plugin、Jetpack Compose、AndroidX Activity/Lifecycle/Navigation、Room、DataStore、WorkManager、Coil、KSP 和 Android 资源编译链。
- `app/src/main/AndroidManifest.xml:6-19` 声明了安装 APK、媒体读取、通知、前台服务、精确闹钟、开机广播和唤醒锁等 Android 权限。
- `README.md:45-54` 明确产品包含教务直连导入、多视图课表、三类桌面小组件、课程提醒和 Material You 个性化。
- `app/src/main/java/com/lingion/sleepy/data/entity/CourseEntity.kt:1-110`、`TimeTableEntity.kt:1-42` 和 `app/src/main/java/com/lingion/sleepy/data/parser/ScheduleParser.kt:28-1219` 承载了较多与平台无关的业务语义。
- `app/src/main/java/com/lingion/sleepy/widget/RemoteViewsWidgetHelper.kt:1-140`、`ScrollStripService.kt:28-149` 和 `WidgetBitmapRenderers.kt` 直接依赖 Android RemoteViews、Bitmap、Canvas 和 RemoteViewsService。
- `app/src/main/java/com/lingion/sleepy/widget/notification/CourseNotificationScheduler.kt:40-540` 直接使用 AlarmManager、PendingIntent、BroadcastReceiver、NotificationCompat 和前台服务。
- `app/src/main/java/com/lingion/sleepy/util/UpdateManager.kt:1-139` 下载并通过 FileProvider / `ACTION_VIEW` 安装 APK，这条更新路径是 Android 专属。

## 2. 复用率与改动大小

这里的“可复用”指业务语义或测试契约可复用，不代表当前 Kotlin 文件可以直接放进 iOS 工程编译。

| 模块 | 当前实现 | iOS 复用判断 | 预计改动 |
|---|---|---|---|
| 课表领域模型 | `CourseEntity`、`TimeTableEntity`、课程组、单双周/按周次 | 业务规则可复用；Room 注解、`Context` 扩展要移除 | 中 |
| 日期、节次、冲突布局、颜色算法 | `DateUtils`、`TimeTableUtils`、`ConflictLayoutEngine`、`CourseColorUtil` | 大部分算法可提取到 KMP common 或改写为 Swift | 小到中 |
| 解析/导出 | WakeUp JSON、ICS、CSV、HTML、Sleepy native 格式 | 格式契约应保持；Kotlin/JVM、jsoup、Java URL 编码和时间 API 要替换 | 中 |
| 本地数据 | Room + DAO + DataStore/SharedPreferences | 无法直接复用；选 SwiftData/Core Data/SQLite 并写迁移 | 中到大 |
| 主界面 | Jetpack Compose Material 3、Android Navigation、Android ViewModel | SwiftUI 需要基本重写；若选 Compose Multiplatform 仍需拆除 Android 依赖 | 大 |
| 教务直连 | `HttpURLConnection`、CookieJar、AES-CBC、厦大接口和 JSON/HTML | 协议逻辑可复用；网络、密码学、会话、错误 UI 需要 iOS 实现 | 中到大 |
| 文件导入/导出/分享 | MediaStore、FileProvider、Android Intent chooser | 功能可重做为 `fileImporter` / `ShareLink` / document picker；现有实现不可复用 | 中 |
| 本地提醒 | AlarmManager + Receiver + WorkManager | 可改为 UNUserNotificationCenter 的本地日历通知；调度模型要重写 | 中 |
| 课程进行中动态提醒 | Android 前台服务 + `ProgressStyle` +“流体云” | 只能评估 Live Activity 等替代方案，不能保证相同呈现和更新机制 | 大/部分不可移植 |
| 桌面小组件 | 3 类 AppWidget，含可滚动 RemoteViews 和 Canvas 位图 | 必须用 WidgetKit/SwiftUI 重写；尺寸、交互、刷新预算均不同 | 大 |
| Android/OPPO 集成 | PinWidgetActivity、ADB debug receiver、OPPO UPK/Seedling | iOS 没有对应能力或对应宿主 | 不可一比一移植 |
| Android 自更新 | GitHub Release 下载 APK 后拉起安装器 | iOS 应改成 App Store/TestFlight 更新流程 | 不可一比一移植 |

综合判断：

- 采用 SwiftUI + KMP 共享核心时，预计只有领域规则、序列化格式、解析/导出测试契约能获得较高复用；当前 Android UI 和平台层基本不应假设可直接搬运。
- 采用 Compose Multiplatform 可以提高 UI 源码复用的上限，但现有代码大量使用 `android.content.Context`、Android resource ID、AndroidX Room/Navigation/Activity 和 RemoteViews，仍需要先做一次较大拆分；WidgetKit、ActivityKit、通知和文件系统仍然要写 Swift/Objective-C 互操作层。
- 推荐第一阶段选择 **KMP 共享 core + SwiftUI iOS UI**，同时保持现有 Android UI 不动。这样可以先降低数据格式和算法分叉风险，不把 Android 专属 UI 迁移问题和 iOS 系统扩展问题绑在一起。

## 3. 功能级移植结论

### 3.1 可以保留产品语义、但需要重写实现的部分

这些功能在 iOS 上有合理落点，属于“可移植功能”，不是“可直接移植代码”：

- 多课表、周视图/网格视图/今日视图、课程组编辑、冲突课程显示。
- 手动添加课程、节次时间表、单双周和按周次课程、主题色与课程色。
- WakeUp JSON、ICS、CSV、HTML、Sleepy native 等导入导出格式。
- 厦门大学教务系统的接口协议，只要服务端接口和认证流程继续兼容。
- 本地课程提醒和每日课表提醒。
- 通过系统分享菜单发送文本、JSON、ICS 或 `.sleepy` 文件。

### 3.2 需要产品决策的部分

#### 小组件

Android 版本当前有“今日课程 / 本周课程 / 最近两天”三类组件，并且当内容超出高度时通过 `RemoteViewsService` 提供滚动条带；入口见 `TodayWidget.kt:31-107`、`TwoDayWidget.kt:25-75`、`WeekGridWidgetProvider.kt` 和 `ScrollStripService.kt:28-149`。

iOS 版本应改为三个 WidgetKit configuration：

1. 今日课程：优先提供小号/中号静态摘要，按下一节课和当天课程时间生成 timeline。
2. 最近两天：提供中号/大号摘要，不承诺与 Android 的可滚动列表完全一致。
3. 周网格：优先提供大号周视图或简化卡片；“任意高度的可滚动周网格”不作为第一版硬性验收项。

WidgetKit 由系统渲染 SwiftUI 视图，使用 timeline entry 和 reload policy，且每个组件有系统控制的刷新预算。应用可以在课表变化后请求刷新，但不能把 Android 的“数据变化即后台立即重绘 + RemoteViews 推送”作为 iOS 设计前提。见 Apple 的 [TimelineProvider](https://developer.apple.com/documentation/widgetkit/timelineprovider) 与 [Keeping a widget up to date](https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date/) 文档。

因此，小组件的“内容”可以移植，但以下行为不能承诺一一相同：

- 任意尺寸下的精准 Canvas 位图布局。
- RemoteViewsService 提供的可滚动条带。
- Android Launcher 的自由拉伸、PinWidgetActivity 主动固定和 ADB 自动化。
- 频繁、即时、无预算限制的后台刷新。

#### 课程提醒与动态岛

每日提醒和课前提醒可以使用 `UNUserNotificationCenter` + `UNCalendarNotificationTrigger` 预先排程；Apple 官方 API 支持在指定日期时间投递本地通知，也支持取消待处理请求，见 [UNCalendarNotificationTrigger](https://developer.apple.com/documentation/usernotifications/uncalendarnotificationtrigger) 和 [Scheduling a notification locally](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app)。

当前 Android 实现还会使用 `FluidCloudService` 每 15 秒重发 `NotificationCompat.ProgressStyle`，并通过前台服务维持课前进度状态，见 `FluidCloudService.kt:21-168` 和 `CourseNotificationScheduler.kt:469-484`。iOS 没有等价的任意常驻前台服务；可以研究 ActivityKit Live Activity / Dynamic Island，但它是系统控制的实时活动，不是 Android 前台服务的替代品。若要使用服务器推送更新，还需要 APNs 服务端支持，见 Apple 的 [ActivityKit push notifications](https://developer.apple.com/documentation/ActivityKit/starting-and-updating-live-activities-with-activitykit-push-notifications)。

建议：

- MVP 先交付本地通知，保证“上课前 N 分钟提醒”和“每日课表提醒”。
- Live Activity 作为独立可选阶段，不把 Dynamic Island 展示、更新频率或所有设备可见性写成核心验收条件。
- 如需在课表编辑后立即重排未来提醒，应用在前台、导入完成和数据保存后重新生成通知请求；不要依赖“开机广播”或常驻服务。

### 3.3 当前实现明确无法一比一移植的部分

| 部分 | 结论 | iOS 处理建议 |
|---|---|---|
| OPPO Fluid Cloud / Seedling UPK | **不可移植**。这是 ColorOS/OPPO 宿主能力，且仓库的 UPK 仍有 `REPLACE_WITH_OPPO_*` 占位符；`oppo-fluid-cloud-upk/README.md:8-25` 也说明目前不能提交发布 | 从 iOS 目标中移除；另立 ActivityKit Live Activity 产品方案 |
| Android RemoteViews / RemoteViewsService | **实现不可移植** | 用 WidgetKit + SwiftUI 重写三个组件 |
| Android 前台服务维持 15 秒进度更新 | **机制不可移植** | 本地通知或可选 Live Activity；接受系统调度和展示差异 |
| `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` Receiver | **无一比一替代** | 在应用启动、前台恢复、数据变更时重排通知；后台刷新仅作机会性补偿 |
| Exact Alarm + PendingIntent Receiver | **无一比一替代** | 预排本地日历通知；对系统投递时间不作超出 iOS 能力的承诺 |
| `PinWidgetActivity` / `requestPinAppWidget` | **不可移植** | 让用户通过 iOS 主屏组件编辑流程手动添加 |
| ADB debug receiver、Android widget preview/render Activity | **不可移植** | 用 Xcode UI tests、Preview、截图测试和真机测试替代 |
| 下载 APK 并拉起安装器 | **不可移植** | App Store、TestFlight 或受支持的企业/替代分发渠道；应用内不能沿用 `ACTION_VIEW` 安装 APK |
| Material You 动态取色的完全一致效果 | **视觉不可保证一致** | 迁移为 iOS semantic colors / 自有主题色；保留用户主题配置，不追求同一套系统取色结果 |
| Android `Context`、Resource ID、FileProvider、MediaStore、Intent chooser | **代码不可移植，功能可重做** | 分别换成 SwiftUI environment/Bundle、DocumentPicker、ShareLink/Share Sheet |

需要特别区分：上表中的“不可移植”是指当前 Android 机制或效果不能在 iOS 上原样复现，不等于整个产品功能没有替代方案。

## 4. 推荐实施计划

估算按 1 名熟悉 Kotlin/Swift/iOS 的高级开发者、已有 Apple Developer 账号、没有新建后端为前提；不包含教务系统接口变更、视觉重新设计、运营审核反复和长期兼容维护。

### Phase 0：目标冻结与 iOS 工程准备（2–3 人日）

- 确定 MVP 是否包含三类小组件、Live Activity、教务直连和 iCloud 同步。
- 决定最低 iOS 版本、是否支持 iPad、是否支持深色模式和 Dynamic Type。
- 在 macOS/Xcode 创建 iOS App、Widget Extension、测试 target 和 App Group。
- 记录 Android 数据格式、导出格式和关键 UI 快照作为跨端验收基线。

出口条件：产品明确接受“OPPO 流体云、自更新 APK、ADB 固定组件不可用”，并接受 iOS 组件刷新/尺寸差异。

### Phase 1：抽离共享核心（8–15 人日）

- 新建 `shared` 或 KMP `commonMain` 层。
- 抽离 `CourseEntity` / `TimeTableEntity` 的纯数据模型和 `inWeek()` 规则。
- 抽离日期、学期周次、节次时间、冲突布局、课程色、撤销快照的纯算法。
- 抽离 `ScheduleParser`、`SleepyNativeParser/Exporter`、WakeUp JSON、ICS、CSV/HTML 的格式契约。
- 将 `Context` 取字符串、Android resource、Room 注解、Android `java.*` 调用移到平台适配层。
- 为 Android 和 iOS 共用一套 round-trip fixture：导入 → 落库模型 → 导出 → 再导入。

出口条件：Android 原有单测继续通过；共享 core 不再依赖 `android.*` / `androidx.*`。

### Phase 2：iOS 本地数据和主界面（12–20 人日）

- 使用 SwiftData、Core Data 或 SQLite 建立课表/课程表；推荐先以 SQLite/Core Data 明确迁移策略，不把 Android Room schema 当作 iOS 存储实现。
- 实现 SwiftUI 导航、首页课表、今日视图、周视图、网格视图、课程详情、课程编辑和多课表管理。
- 用 `AppStorage`/Keychain 替代普通偏好和账号记忆；密码不落盘。
- 适配 iOS 深色模式、动态字体、横竖屏和 VoiceOver。

出口条件：不依赖网络时，可以完整查看、增删改课程和切换课表；核心视觉回归通过。

### Phase 3：导入、导出、分享和教务直连（7–12 人日）

- 用 `fileImporter` / document picker 接入 JSON、ICS、CSV、HTML 和 `.sleepy`。
- 用 `ShareLink` / `UIActivityViewController` 分享文本与文件。
- 将厦大登录客户端从 `HttpURLConnection`、Java `Cipher`、自定义 CookieJar 改为 `URLSession`、CryptoKit 或经过验证的跨平台密码学实现。
- 对可能出现验证码、二次验证或登录页变化的情况，增加 `WKWebView`/Safari 登录兜底；当前 Android 注释已经明确没有该备用入口，见 `XmuAutoLoginClient.kt:19-35`。
- 保持现有 `JwXmuParser` 的接口响应映射和导入命名行为。

出口条件：使用脱敏真实样本验证自动登录/手动导入、中文/英文文件名、ICS 导入 Apple Calendar 和跨 App 分享。

### Phase 4：通知与可选 Live Activity（5–10 人日）

- 用本地日历通知生成每日提醒和逐节课前提醒。
- 在表数据变更、提醒设置变更、应用启动/回到前台时取消并重建未来通知。
- 明确通知数量上限、跨周生成策略、时区/夏令时和通知权限拒绝后的 UI。
- 若产品仍需要动态岛，再增加 ActivityKit extension、状态模型、结束逻辑和真机兼容测试；如采用远程更新，另计 APNs 服务端工作量。

出口条件：应用被杀死或处于后台时，已排程通知能按配置送达；不把后台代码按秒执行作为验收项。

### Phase 5：WidgetKit 小组件（8–15 人日）

- 建立 WidgetKit extension 和 App Group 共享数据快照。
- 重写今日、最近两天、周网格三种 widget view、timeline provider 和配置项。
- 用 WidgetKit family 适配小/中/大号布局；移除对任意高度滚动和 Android Launcher 尺寸的依赖。
- 实现课程变更后的 `WidgetCenter` reload；为预算受限和无数据状态提供稳定快照。
- 真机验证主屏、锁屏/待机（若纳入目标）、深色模式、字体放大和组件编辑流程。

出口条件：三个组件在目标 iOS 版本和目标 family 上显示正确，课程跨日/跨周时间线正确；接受刷新不保证实时。

### Phase 6：数据迁移、测试和上架（8–15 人日）

- 用 `.sleepy` / WakeUp JSON 作为 Android → iOS 首次迁移通道，必要时再设计 iCloud 同步。
- 补齐 parser、schedule、notification、widget timeline、数据迁移和 UI tests。
- 在真实 iPhone 上测试通知、组件、分享、登录、网络切换、时区、低电量和系统杀后台。
- 在 macOS/Xcode 完成签名、归档、TestFlight 和 App Store Connect 提交。

Apple 的发布流程需要将 iOS build 上传到 App Store Connect，再选择 build 提交审核，不能沿用 Android 的 APK 下载/安装路径，见 [Upload builds](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/)。

## 5. 工作量结论

| 目标 | 范围 | 估算 |
|---|---|---:|
| iOS MVP | 课表查看/编辑、多课表、核心导入导出、本地提醒；暂不承诺三类组件完整 parity、Live Activity、教务自动登录 | 35–50 人日 |
| 可发布版本 | MVP + 厦大直连、文件分享、三类 WidgetKit 组件、迁移工具、真机和 TestFlight 验证 | 50–75 人日 |
| 高度接近 Android 功能集 | 可选 Live Activity、复杂组件配置、视觉回归、全面迁移和兼容测试 | 70–100 人日 |

单人全职大致是 2–4 个月；两人并行时通常仍需 6–10 周，因为 core 抽离、iOS 存储契约、WidgetKit 和真机 QA 有明显串行关系。若没有 macOS/iPhone、Apple Developer 账号或需要新增 APNs 后端，需另计环境和外部依赖时间。

## 6. 建议的首个里程碑

不建议一开始就重写全部页面。第一个可验证里程碑应是：

1. 建立独立 iOS target 和 App Group。
2. 把 `CourseEntity`、`TimeTableEntity`、周次判断、日期计算、冲突布局和 Sleepy native round-trip 测试抽成无 Android 依赖的 core。
3. iOS 先实现一个只读课表首页和 `.sleepy` 文件导入。
4. 用导入后的数据生成一个 Today Widget timeline。
5. 在真机上确认数据共享、组件刷新和通知权限流程。

这个里程碑能较早暴露三项最大风险：KMP/Swift 数据边界、WidgetKit 刷新模型和 iOS 文件/通知生命周期；通过后再投入完整 UI 和厦大自动登录，风险最低。

## 7. 工程与环境限制记录

- 当前仓库没有 iOS 工程或 Swift 源码，因此本评估不是基于已存在的 iOS 分支。
- 当前工作区是 Windows Android 构建环境；iOS 的 Xcode、签名、Widget Extension 和真机验证必须在 macOS 上完成。
- 本次仅做移植评估，没有修改现有 Android 业务代码；工作区原有未提交修改应保持不变。
- 已尝试运行 `:app:testDebugUnitTest`，但 Gradle 在编译前因当前环境无法创建 `C:\.gradle\wrapper\dists\...\.lck` 失败；这不代表现有 Android 单测本身失败，后续应在可写的 Gradle 用户目录重新验证。

## 8. 官方能力参考

- [WidgetKit TimelineProvider](https://developer.apple.com/documentation/widgetkit/timelineprovider)
- [Keeping a widget up to date](https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date/)
- [UNCalendarNotificationTrigger](https://developer.apple.com/documentation/usernotifications/uncalendarnotificationtrigger)
- [Scheduling a notification locally](https://developer.apple.com/documentation/usernotifications/scheduling-a-notification-locally-from-your-app)
- [ActivityKit push notifications](https://developer.apple.com/documentation/ActivityKit/starting-and-updating-live-activities-with-activitykit-push-notifications)
- [Using background tasks to update your app](https://developer.apple.com/documentation/UIKit/using-background-tasks-to-update-your-app)
- [App Store Connect: Upload builds](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds/)
