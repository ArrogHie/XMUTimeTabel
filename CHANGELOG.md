# Changelog

## v1.1.5 — 移除课程时间显示设置 · 表头只显日期

### 修复
- **移除「课程时间显示」设置(节次/时间)**: 原设置对网格视图无效且无意义 — 网格视图/周课表小组件表头小字统一只显示日期(不再显示课数/「无课」等节次信息), 完整视图课程行与小组件卡内时间恒定显示为时刻(HH:mm)。连带: `display_mode` 偏好移除, 表头日期默认开启(「网格视图表头显示日期」默认 true)。

### 变更
- 简体中文赞赏/去 Star 提示文案微调。

## v1.1.4 — 网格时间列 · 帮助页 · 赞赏 · 两日小组件重做

### 修复
- **网格视图左侧时间列**: 卡片高 52→62dp, 起始/结束时刻拆两行显示, 修复单行 `HH:mm-HH:mm` 在列宽内被截断。
- **「最近两天」小组件小尺寸渲染异常**: 直接改用与「本周课程」相同的**内部渲染窗口**方式 — 小组件本体即 ListView, 内容长图按行带切片、行高 = 位图像素高, 容器只做视口不缩放不裁剪。此前"静态整图 / 壳图+条带"二选一的两套几何在小尺寸下对不齐, 导致内容越界、裁剪、上下错位。
- **RemoteViews 位图生命周期**: 删除 `updateAppWidget` 后紧跟的 `Bitmap.recycle()`(启动器异步消费位图时可能拿到已释放像素 → 回落到"无法加载微件")。

### 变更
- **课程时间显示默认值**: 由「节次」改为「时间」。
- **「我的」页新增「帮助」**: 展示 `docs/HELP.md` 常见问题。
- **「我的」页新增「赞赏作者」**: 展示微信 / 支付宝赞赏码。
- **赞赏提醒**: 使用满 5 天后弹一次, 可跳 GitHub 点 Star、跳赞赏页、下次提醒、不再显示; debug 版「我的」页有手动触发按钮。

## v1.0.52-xmu11 — 行带切片 · 行高逐像素匹配

### 修复(「本周课程」可滚动窗口的重叠)
- **不再 48dp 等宽横切**: 改为**行带切片** — 每个 ListView Item = 一个完整节次行(表头 1 带 + 每节次 1 带), 切片边界落在"节次与节次之间的间隙"上 → 文字/卡片永不横跨两个 Item, 从根上消除"Item 内容高度与分配高度不匹配导致的跨 Item 重叠"。
- **行高 = 位图真实像素高**: 每个 Item 用独立布局(wrap_content + setMinimumHeight=位图像素高), ListView 测量行高 = 该行带位图实际高度 → 无 measure/layout 错位, View Recycling 后高度也自洽。
- 行带几何(表头高、每节次自然行高、行距)由 `weekGridBands` 单一公式给出, 渲染长图与切片共用, 不漂移。

## v1.0.52-xmu10 — 课程卡不重叠 · 文字裁切兜底

### 修复
- **课程卡互相重叠/文字串行**: 卡内字号改为**固定**(课名 12dp、信息 10dp × 字号密度, 不再随「每节课行高」同步膨胀) — 行距恒定, 行高再大也不产生"字比行高还高"的越界重叠。
- 每张课程卡与每个时间格绘制时都 `clipRect` 裁到自身边界: 文字、卡片背景、描边绝不越出所属格/卡, 相邻卡之间互不污染。
- 课名行数按"卡片高 − 至少 1 行信息"推算上限; 地点/老师按余下高度逐行排, 一律严格在卡内。
- 时间列(节次号 + 开始 + 结束): 整块在格内垂直居中; 行距下限 = 时间字号 + 1dp; 若整块高于格(极端小行高)自动等比例缩小字号, 保证不越格、不串到下一节。
- 分隔线与表格、卡块共用同一 `rowPitch`, 行高只被「每节课行高」与内容自然高度决定, 无重复叠加。

## v1.0.52-xmu9 — 小组件内部渲染窗口

### 变更
- **「本周课程」网格形态改为"内部渲染窗口"**: 不再向桌面推整张课表位图(外层 ImageView 受桌面格子约束/拉伸, 导致字体变形、高度受限、滚动异常)。现在小组件本体就是一个可滚动 ListView — 每条承载课表长图的 48dp 切片, ListView 即内部视口, 任意拖拽小组件大小都不拉伸字体。
- **滚动范围 = 固定上限**: 内部渲染窗口的课表长图总高度固定为"最大行高(每节课行高上限 240%)× 最多 11 节", 不再随当前课表数据/行高设置自适应 → 应用内把行高调到再大都能一路滑到底; 行高小于上限时下方留空但滚动总长不变。
- 空课表 / 学期外仍显示静态状态提示图; 「宽行卡片」形态保持静态(等比容器不拉伸)。

## v1.0.52-xmu8 — 小组件任意尺寸不拉伸 · 固定最大滚动范围

### 修复
- **字体随尺寸缩放变形**: 「本周课程」位图容器改为等比显示(centerCrop) — 位图与桌面实际比例不一致时只轻微裁边、绝不横向/纵向拉伸文字; 5×4 及任意拖拽后尺寸下字形比例都一致。
- **滚动滑不到底**: 「本周课程」可滚动内容的**总长度固定为上限** — 按最大行高(每节课行高滑杆上限 240%)× 最多 11 节计算, 不随当前课表行数/行高设置自适应。应用内把行高调到最大也必然能一路滑到最后一行, 不再因设置后未及时刷新而缩短滚动区。

## v1.0.52-xmu7 — 字体不拉伸 · 时间列排版 · 滚动到底

### 修复
- **字体"宽扁"**: 容器 ImageView 由 fitXY(比例不一致时会横向拉伸文字)改为 centerCrop(等比显示, 比例不符只轻微裁边不拉伸), 「本周课程」静态图与滚动壳图均生效。
- **时间列观感**: 节次号不再贴顶 — 改为「节次号 + 开始 + 结束」三行在单元格内垂直居中; 开始与结束时间之间拉开行距(≈1.55 倍行高), 不再上下间距为 0。
- **滚动滑不到底**: 滚动条带改为**强制自然行高**渲染(每节课行高 = 40dp ×「每节课行高」), 与可视壳图逐像素同距 — 行高设大后内容完整、可一路滑到底部最后一行, 不再截断。

## v1.0.52-xmu6 — 「本周课程」横向排版 · 可上下滚动

### 变更(网格/默认形态)
- **文字改横向、左上对齐**: 课程卡内课名不再竖排; 课名逐行(可换行), 地点、老师各自按卡片宽度换行, 不再一行撑出卡片边界。
- **可上下滚动**: 按「每节课行高」计算的自然内容高度超过小组件可视高度时, 改为可滚动条带(上下滑动查看后面节次), 不再把下方课程截断后完全看不到。
- **星期/日期栏收短约 1/3**(56dp → 38dp)。
- **时间列显示开始与结束**: 每节除节次号外, 显示该节开始时间与结束时间两行(如 08:00 / 08:45)。
- 行分隔线辅助横向阅读; 节次行高仍受「每节课行高」滑杆控制, 超出部分通过滚动查看。
- 保留 3 个小组件形态与右上角设置齿轮; 「宽行卡片」形态不受影响(已是横向逐行)。

## v1.0.52-xmu5 — 小组件精简为 3 个

### 变更
- **桌面小组件精简为 3 个**: 「今日课程」「本周课程」(原网格, 更名去掉"(网格)"后缀)「最近两天」。删除「本周课表（列表）」「本周课表（周视图）」及全部「· 小」同名变体(共 7 个 picker 项), 避免同名小组件混淆、设置改错对象。
- 三个保留的小组件都支持拖拽调整大小(horizontal|vertical); 「本周课程」的卡片高度/行高/卡片形态设置现在只作用于这一个「本周课程」组件, 不再有同名周列表/周视图分身。
- 通用设置里小组件的「显示时间 / 隐藏周六日」等全局项照常作用到保留的 3 个形态。
- 数据刷新(WidgetUpdater)、快捷添加(Pin)、可滚动条带(ScrollStrip)同步只面向 3 个保留组件; 删除孤立资源(provider 定义 / 预览图 / 布局)。

### 修复
- 「本周课程」小组件的每节课行高、宽行卡片最小高度等调节此前可能作用在用户桌面上的同名「本周课表（列表/周视图）」组件上而看似无效 — 精简后设置必然落到唯一保留的「本周课程」。

## v1.0.52-xmu4 — 单通道自动导入 · 小组件卡片高度

### 变更
- **移除「手动登录」通道**: 手动 WebView 登录仍无法稳定抓取, 厦大统一身份认证对在校生不出现图形验证码, 账号密码自动登录可独立完成。教务导入现在只有「学号 + 密码自动登录抓取 → 自动建表」一条通道, 界面大幅简化。
- **小组件卡片高度可调**:
  - 「网格」形态新增 **每节课行高**(80%~240%): 调大课程卡更高、容纳更多信息; 时间列 / 卡片同步加高, 底部超出画布的课程自动不再绘制(可拉高小组件看到更多)。
  - 「宽行」形态新增 **卡片最小高度**(0 = 自适应): 固定更高的课程卡容纳更多行。
- **信息行左对齐 + 去省略号**: 小组件宽行卡片与 App「课表-网格」课程卡内的时间 / 教室 / 老师改居左排列; 空间不足时按行裁切(Clip), 不再在仍有空间时显示「…」省略。

### 修复
- 删除手动登录残留的 WebView 抓取桥与相关文案。

## v1.0.52-xmu3 — 手动登录修复 · 自动建表 · 小组件形态

### 修复
- **手动登录抓取失败 (TypeError: Failed to fetch)**: 此前在教务网页内跑 JS `fetch`, 会被页面 CSP / 跨域 / 移动版兜底拦截而直接失败。现改为把 WebView 登录后的会话 cookie 导出给与「账号密码自动登录」同一套 HttpURLConnection 抓取时序(与 schedule/fetch_xmu_schedule.py 一致), 在 WebView 内不再执行抓取 JS。
- **导入不再弹配置窗**: 抓取成功后直接自动建表 — 开学日期按当前学期自动推断、节次时间用教务节次字典(缺口用厦大作息 1–11 节兜底), 导入完自动回到课表页。
- **网格课程卡排版**: 有副信息时课程名不再居中、副信息贴底, 改为课名居上、时间 / 地点 / 老师逐行紧跟其下, 信息不会被裁掉。
- **logo 圆形遮罩裁切**: 各密度桌面图标重生成, 主图缩到画布 55% 居中(其余为品牌深底色), 圆形/方圆遮罩下主要元素完整可见。

### 新增
- **课表页左上角「设置」按钮** → 打开通用设置。
- **网格小组件右上角「设置」齿轮**(仅网格形态) → 点击直接打开应用内通用设置。
- **小组件卡片形态**(通用设置 → 小组件): 「网格」7 列窄卡(原样) / 「宽行」课程卡占满整行, 时间 / 地点 / 老师完整单行展示; 宽行形态可调卡片圆角与卡片间距。

## v1.0.51-xmu2 — 厦大专属版体验迭代

### 修复
- **手动登录抓取失败**: 登录 WebView 此前用手机 UA, 厦大统一身份认证/教务门户会渲染移动版, 与抓取脚本验证的桌面版接口不符。现 WebView 与自动登录统一使用桌面 Chrome UA。
- **同课多教室重叠**: 教务把同一门课(同课程代码/星期/节次/周次)因多个可用教室拆成多行, 网格出现重叠卡。解析现按这些键先合并: 教室 ` / ` 连接、教师去重合并, 再落课表。

### 网格视图自定义(课表-网格)
- 课程卡片内容: 时间 / 教室 / 教师 三个独立开关可自由组合(取代旧三选一)。
- 行间距、列间距独立滑杆(0~16dp); 冲突卡圆角跟随「卡片圆角」设置。

### 桌面小组件自定义
- 全局开关: 小组件显示时间、隐藏周六日(独立于主页课表的显示星期设置)。
- 「网格小组件」外观: 课程卡圆角 / 行间距 / 列间距 / 字号密度 可调(卡片尺寸由桌面格子决定)。

### 其它
- 桌面图标更新为新 logo。

## v1.0.51-xmu — 厦门大学专属版 (XMU edition)

本版把多学校课表应用改造为厦门大学专属课表应用。

### 教务直连仅限厦门大学
- 移除原 181 所 `schools.json` 学校目录与学校选择页 / 自定义 URL 直连。
- 移除其余约 27 套教务协议解析器与抓取脚本 (强智/正方/URP/通用金智 wisedu/超星/EAMS5/单校自建等), 仅保留厦大专属单协议 `xmu`。
- 厦大走真实接口: 统一身份认证 (ids.xmu.edu.cn) + 金智 gsapp/wdkbapp 微服务课表 (`wdkcb/queryXspkjg.do` → `pkjgList`), 与 `schedule/fetch_xmu_schedule.py` 验证过的时序一致。
- 新增两条导入通道: **手动登录** (WebView 内亲自输学号/密码/验证码, 登录后点「导入课表」自动抓取) 与 **账号密码自动登录** (按教务 AES 加密流程自动登录抓取; 密码仅当次使用不保存; 教务启用图形验证码时请改手动)。
- 学号记忆到本地 (仅学号, 不存密码)。
- 文件/粘贴/ICS 等其它导入通道、课表/今日/管理/我的各屏与桌面小组件保持不变。
- 应用显示名改为「厦大课表」; 包名保持不变。

### v1.0.35

### Grid card sub-info — room, teacher, or nothing

Week grid cards showed course name plus a "3-4节" node label under it. The leftmost column already carries the node numbers and times. The card's vertical position *is* the node. That second line repeated information you get for free.

Setting now in 通用设置 → 课程显示 → 网格卡片副信息. Three options:

- 教室 — the room under the course name. Default.
- 教师 — the teacher instead.
- 无 — course name only, centered in the card.

Empty room or empty teacher renders as "none" for that card; no blank line. Changes take effect immediately, no restart.

### Three pages stopped ignoring your theme

You pick spring green. Most of the app turns spring green. Three screens didn't get the memo:

The JW import page (教务导入) hardcoded `themeKey = "default"` and followed the *system* dark mode instead of the in-app setting. So with spring green selected and dark mode set to "always light" in-app, the import flow showed default purple in dark. Same story in the week grid preview. The import conflict card also drew its red background from two hex literals — `0xFFFFEBEE` / `0xFFB71C1C` — which are light-theme pink-red and stay pink-red in every theme. Now they read `errorContainer` / `onErrorContainer` from whatever theme you picked.

All three pages now subscribe to `AppPrefs.themeKeyFlow` and `AppPrefs.isDarkMode`, same as the main schedule. Change theme, they follow.

### Course capsule text no longer hugs the bottom

With sub-info showing, the course name sat directly on top of the room line — zero gap at two-line heights. The card now splits its space: name centered in the upper region, sub-info pinned to the bottom edge. Cards without sub-info center the name as before.

### Settings pages split

外观 (Appearance) held theme colors, course display, and widget settings — three groups, one page. The course-display and widget groups moved to 通用设置 (General). Appearance is now theme colors only. The `refreshWidgets()` pipeline moved with them, so display changes still refresh widgets instantly.

The shared card components (SectionHeader / SettingsCard / DisplayModeOption / SettingToggleRow) were pulled into a single `SettingsCards.kt` used by both pages, instead of two diverging copies.

### Build

- versionCode: 36
- versionName: 1.0.35
- APKs: `app-arm64-v8a-release.apk` (most phones), `app-armeabi-v7a-release.apk` (older arm32), `app-x86_64-release.apk` (emulator)

— Lingion

---

## v1.0.34

### Course color split — one toggle became two

Last release shipped a "unified course color" toggle. One switch, two jobs: it stripped color from the desktop widgets *and* from the in-app schedule grid. If you wanted gray widgets but colored capsules in the app, too bad. One switch drove both.

Now they're two independent settings.

- `widget_colorless` — desktop widgets only. Behavior unchanged.
- `course_colorless` — the in-app schedule grid and today page only. New, default off.

Default off means the app's course capsules come back in color after you update, even if you had the old unified toggle on. The two sides no longer touch each other.

The split is enforced in `AppPrefs`, not just in the UI. The two keys live side by side, neither reads the other, neither seeds the other. There's no migration step — the old widget toggle keeps its stored value, the new course toggle starts from its default. Zero cross-read, so the independence can't regress silently. The course toggle's switch handler deliberately does *not* call `refreshWidgets()`, because it has nothing to do with widgets.

### CourseColorUtil — one source of truth, plus text luminance

Course color logic existed in four places. Four copies of the same HSL-picking code, drifting. This release pulls them into a single `CourseColorUtil` with three layers: constants, pure logic, and platform adapters. The four call sites now point at one implementation.

Along the way, a real bug: text color on custom course capsules used a fixed source, so a dark custom course color produced dark text you couldn't read. `CourseColorUtil` gained `luminance` and `textColorOn` pure functions. Deep custom colors now get white text; light ones keep the old behavior. This lands on every surface that renders a course capsule — the in-app card, the lesson rows, the today card, and the widget bitmap renderers.

### Glance layer deleted

The Glance framework is gone. All five widgets now render through synchronous RemoteViews + Canvas — no `SessionWorker`, no async render that OPPO freezes mid-draw. `CourseColorRules` went with it, and `loadDataSync` moved into the receivers.

This is internal. No user-visible behavior change. But it's why the widget refresh story from 1.0.31/1.0.33 finally holds everywhere: one code path, no async race.

### Settings screen reorganized

The settings entry grew to eight items. It's five now: theme, appearance, widget settings, and the rest. A new `AppearanceScreen` groups three sections — theme colors, course display, widget. The dead `ThemeColorScreen` and `MoreSettingsScreen` routes were deleted outright.

The "follow system" label is also split in two: light-follows-system and dark-follows-system, each its own switch.

### inWeek semantics — courses no longer forced into a start-to-end range

Single/double-week courses were being expanded into a continuous "start week to end week" span, whether that matched reality or not. Fixed. Courses now show on their actual weeks.

This means some schedules look like they lost cells after updating. They didn't. The old display was wrong. If a course only meets on odd weeks, it shows on odd weeks — not as a block running from week 1 to week 16.

### Android backup rules pointed at the wrong filename

Sleepy has no cloud service and no in-app backup. The `backup_rules.xml` and `data_extraction_rules.xml` files are the platform-side rules Android reads when the OS does auto-backup or device-transfer. They listed `settings.xml` as the preference file to back up. That file doesn't exist — `AppPrefs` writes to `sleepy_prefs.xml`. The result: whenever Android tried to back up or transfer your Sleepy settings, it copied nothing.

Three rule entries — `backup_rules.xml` plus the cloud-backup and device-transfer sections of `data_extraction_rules.xml` — now point at the real filename. Whether anything actually gets backed up is still up to Android and your device vendor.

### Internationalization

Sixteen theme keys still had untranslated Simplified Chinese left in the English, Japanese, Spanish, and Traditional Chinese locales. Translated. Plus terminal-pass consistency: Traditional Chinese now uses 檔案/匯出/文字 across the board, Japanese uses 授業 for courses, Spanish uses Período for periods.

### ICS export

Single/double-week courses now export with `INTERVAL=2` — they were exporting as ordinary weekly courses before. Custom times (`ownTime`) export too.

### Stability

- lintRelease ran with eight errors. All cleared: notification permission checks inlined before each post (guards the coroutine window where the permission could be revoked), widget size reads wrapped in a `TIRAMISU` guard that fixes a real `NoSuchMethodError` crash on API 31/32, `ExportScreen` falls back through `FileProvider` on API 26-28.
- The reminder subsystem's `cancelAll` moved off `runBlocking` onto a coroutine — the screen no longer blocks while alarms cancel.
- Minute input debounced.
- Deleting a schedule table now explicitly cancels the before-class alarms of the courses it cascades away.

### Known issue (BG-14)

Fresh installs don't get the 15-minute fallback widget refresh — `WidgetUpdater.schedule` is a dead link. Slated for v1.0.35. If your widget goes stale, open the app and touch some data once.

### Build

- versionCode: 35
- versionName: 1.0.34
- APKs: `app-arm64-v8a-release.apk` (most phones), `app-armeabi-v7a-release.apk` (older arm32), `app-x86_64-release.apk` (emulator)

— Lingion

---

## v1.0.35

### 网格卡片副信息——教室 / 教师 / 无

周视图网格卡之前在课程名下面垫一行「3-4节」。左栏本来就印着节次和时间，卡片在网格里的纵向位置本身就对应节次。那行字等于重复了一遍免费就能看到的信息。

设置入口:通用设置 → 课程显示 → 网格卡片副信息。三个选项:

- 教室——课程名下方显示上课地点。默认。
- 教师——显示授课教师。
- 无——只显示课程名，居中。

某门课没填教室/教师时，那张卡自动按「无」处理，不会留一行空白。改完立即生效。

### 三个页面终于肯跟主题走了

你选了春绿，App 大部分地方跟着变绿。有三处没跟上:

教务导入页把 `themeKey` 写死成 `"default"`,深浅色跟的是系统而不是 App 内设置。于是你 App 里选了春绿+强制浅色，导入流程照样渲染默认紫+暗色。周视图预览页同样的问题。导入冲突数卡片还有两处写死的红色——`0xFFFFEBEE` / `0xFFB71C1C`,浅色主题的粉红，在任何主题下都是那个粉红。现在改读主题的 `errorContainer` / `onErrorContainer`。

三个页面现在都订阅 `AppPrefs.themeKeyFlow` 和 `AppPrefs.isDarkMode`,跟主课表同一条线。换主题，它们跟着换。

### 课程名不再贴着副文字

显示副信息时，课程名紧贴在教室/教师那行上面——两行文字零间距。现在卡片空间一分为二:课程名在上半区居中，副信息贴卡片底边。没副信息的卡保持原来的整体居中。

### 设置页拆分

外观页一度装了主题色彩、课程显示、小组件三组。课程显示和小组件两组迁去了通用设置，外观页只剩主题色彩。`refreshWidgets()` 管线随迁，改显示项后小组件照旧即时刷新。

公共卡片组件(SectionHeader / SettingsCard / DisplayModeOption / SettingToggleRow)抽成了单独的 `SettingsCards.kt`,两页共用，不再各养一份各漂各的。

### 构建

- versionCode: 36
- versionName: 1.0.35
- APK:`app-arm64-v8a-release.apk`(多数手机)、`app-armeabi-v7a-release.apk`(旧款 arm32)、`app-x86_64-release.apk`(模拟器)

— Lingion

---

## v1.0.34

### 课程颜色拆分——一个开关变成两个

上个版本出了一个「统一课程底色」开关。一个开关干两件事:既去掉桌面小组件的颜色,又去掉 App 内课表的颜色。你想要小组件变灰、但 App 里胶囊保持彩色?不行,一个开关两头都绑死了。

现在拆成两个独立设置。

- `widget_colorless`——只管桌面小组件。行为不变。
- `course_colorless`——只管 App 内的课表和今日页。新增,默认关。

默认关意味着更新之后 App 的课程胶囊会恢复彩色,哪怕你之前把旧的统一开关开着。两边不再互相影响。

这个拆分是在 `AppPrefs` 层做的,不是只在 UI 上。两个 key 并排放着,谁也不读谁,谁也不给谁播种。没有迁移步骤——旧的小组件开关保留它存的值,新的课程开关从默认值开始。零互读,独立性没法静默退化。课程开关的 handler 特意**不**调 `refreshWidgets()`,因为它跟小组件没关系。

### CourseColorUtil——单一事实来源 + 文字亮度

课程配色逻辑原本散在四处。四份 HSL 选色的拷贝,各自漂移。这个版本把它们收敛进一个 `CourseColorUtil`,三层结构:常量、纯逻辑、平台适配。四个调用点现在指向同一个实现。

顺带修了一个真 bug:自定义课程胶囊的文字色用了固定来源,深色的自定义课程底配深色文字,根本看不清。`CourseColorUtil` 加了 `luminance` 和 `textColorOn` 两个纯函数。深色自定义底色现在出白字,浅色底保持原样。所有渲染课程胶囊的地方都接上了——App 内的卡片、lesson 行、今日卡片,还有小组件的 bitmap renderer。

### Glance 层整个删掉

Glance 框架没了。五个小组件现在全走同步的 RemoteViews + Canvas——没有 `SessionWorker`,没有那个 OPPO 渲染到一半就冻住的异步流程。`CourseColorRules` 一起删,`loadDataSync` 迁进了 receiver。

这是内部改动,用户看不到行为变化。但它就是 1.0.31/1.0.33 里那个小组件刷新问题终于到处都成立的原因:一条代码路径,没有异步竞态。

### 设置页重排

设置入口一度涨到八项。现在是五项:主题、外观、小组件设置,加其余。新增 `AppearanceScreen`,三组结构——主题色彩、课程显示、小组件。死掉的 `ThemeColorScreen` 和 `MoreSettingsScreen` 路由整个删掉。

「跟随系统」标签也拆成两个:浅色跟随系统、深色跟随系统,各一个开关。

### inWeek 语义——课程不再被硬塞进起止区间

单双周课程之前被扩成一个连续的「起始周—结束周」区间,不管符不符合实际。修了。课程现在按实际周次显示。

这意味着有些课表更新后看起来「少了几格」。其实没少。是旧的显示错了。一门只上单周的课,就显示在单周——不是从第 1 周到第 16 周拉一条。

### Android 备份规则指向了错的文件名

Sleepy 没有云服务,也没有应用内备份。`backup_rules.xml` 和 `data_extraction_rules.xml` 是 Android 系统做自动备份或换机迁移时读取的平台侧规则。里面把偏好文件写成了 `settings.xml`。这文件根本不存在——`AppPrefs` 写的是 `sleepy_prefs.xml`。结果就是:Android 一旦尝试备份或迁移你的 Sleepy 设置,拷过去的是空气。

三处规则——`backup_rules.xml` 加上 `data_extraction_rules.xml` 的 cloud-backup 和 device-transfer 两段——现在指向真实文件名。到底有没有真的备份上去,还是看 Android 和手机厂商。

### 国际化

十六个主题 key 在英语、日语、西班牙语、繁体中文里还残留着没翻译的简体中文。翻完了。加上终审的一致性:繁体中文统一用 檔案/匯出/文字,日语课程统一用 授業,西班牙语节次统一用 Período。

### ICS 导出

单双周课程现在导出 `INTERVAL=2`——之前被当普通周课导。自定义时间(`ownTime`)也能导出了。

### 稳定性

- lintRelease 原本报 8 个 error,全清零:通知权限校验在每次 post 前内联(守住协程窗口期权限被撤销的兜底),小组件尺寸读取套 `TIRAMISU` 守卫修了一个 API 31/32 上的真 `NoSuchMethodError` 崩溃,`ExportScreen` 在 API 26-28 走 `FileProvider` 回退。
- 提醒子系统的 `cancelAll` 从 `runBlocking` 挪到协程——取消闹钟时界面不再卡死。
- 分钟输入加了 debounce。
- 删课表时现在显式取消级联删除课程的课前闹钟。

### 既知问题(BG-14)

新装用户拿不到 15 分钟兜底的小组件刷新——`WidgetUpdater.schedule` 是个死链。排在 v1.0.35。小组件要是过期了,进 App 碰一下任意数据。

### 构建

- versionCode: 35
- versionName: 1.0.34
- APK:`app-arm64-v8a-release.apk`(多数手机)、`app-armeabi-v7a-release.apk`(旧款 arm32)、`app-x86_64-release.apk`(模拟器)

— Lingion
