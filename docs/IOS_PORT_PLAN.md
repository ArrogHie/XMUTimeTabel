# iOS 移植实施计划（越狱 deb/ipa）

日期：2026-09-10  
目标：在 WSL + Theos 上构建 **无需 Apple 签名** 的 iOS 版厦大课表，交付 `deb` + `ipa`，供有能力越狱 / TrollStore 的用户安装。

## 1. 与评估文档的差异

| 维度 | 评估文档（App Store） | 本次（越狱分发） |
|---|---|---|
| 工具链 | macOS + Xcode | WSL + Theos + iPhoneOS16.5 SDK |
| UI | SwiftUI + 可选 KMP core | SwiftUI 直接重写领域层 |
| 签名 | 正式证书 + TestFlight | ldid 伪签名，免证书 |
| 分发 | App Store Connect | deb（Sileo/Zebra）+ ipa（TrollStore） |
| 小组件 | WidgetKit Extension | 第一阶段尝试主 App 内嵌；失败则二期 |
| 最低系统 | 产品决策 | **iOS 15.0** |

## 2. 工程位置与技术栈

- 路径：`XMUTimeTabel/ios/`
- 包名：`com.arroghie.xmutimetable`
- 显示名：厦大课表
- 语言：Swift 5 + SwiftUI + SQLite3
- 构建：Theos `APPLICATION_NAME = XMUTimeTable`
- 伪签名 entitlements：`platform-application`（越狱可装 `/Applications`）

## 3. 功能对齐矩阵（尽量对齐 Android）

| Android 能力 | iOS 第一版 | 说明 |
|---|---|---|
| 今日 / 周视图 / 网格视图 | ✅ | SwiftUI 重写 |
| 多课表管理 | ✅ | SQLite `time_tables` + `courses` |
| 课程增删改 | ✅ | CourseEditView |
| `.sleepy` 导入导出 | ✅ | 格式契约对齐 SleepyNative* |
| ICS 导出 | ✅ | 简化 VEVENT |
| 厦大教务自动登录 | ✅ | URLSession + AES-128-CBC |
| 课前/每日本地提醒 | ✅ | UNUserNotificationCenter |
| 桌面小组件 | ✅ 1.0.0 | 今日/最近两天/周网格，Theos APPEX 嵌入 PlugIns |
| OPPO 流体云 / 动态岛 | ❌ | 平台不可移植 |
| APK 自更新 | ❌ | 改为提示 GitHub Release（网页） |

## 4. 实施阶段（本仓库内执行）

1. **骨架**：Theos 工程、Info.plist、图标、Tab 导航、空状态 → 可 `make package`
2. **数据层**：模型、SQLite、DateUtils、TimeTableUtils、Sleepy 解析导出
3. **课表 UI**：今日 / 周 / 网格 + 详情
4. **编辑与文件**：加课、课表管理、导入导出、分享
5. **教务直连**：ids 登录 + wdkbapp 抓取 + 解析落库
6. **提醒**：课前 N 分钟 + 每日课表
7. **打包**：`scripts/package.sh` 产出 `artifacts/XMUTimeTable-v*.deb` 与 `.ipa`

## 5. 数据契约

### 5.1 SQLite

```sql
time_tables(id INTEGER PK, name, startDate, maxWeek, nodesPerDay, timeJson, color, isDefault, smartConfigJson, createdAt)
courses(id INTEGER PK, groupId, tableId FK, courseName, teacher, room, note,
        day, startNode, step, startWeek, endWeek, type, color,
        ownTime, startTime, endTime, credit, level)
```

### 5.2 timeJson

`[{"node":1,"start":"08:00","end":"08:45"}, ...]` — 与 Android `TimeTableUtils.DEFAULT_TIME_JSON` 同源。

### 5.3 sleepy-v1

- Magic：`#sleepy-v1`
- 行类型：`T` / `N` / `Nd` / `C` / `z`
- C 行 10 列竖线分隔；转义与调色板、周次 token、CRC32 与 Android 实现对齐

### 5.4 厦大教务

- ids：`https://ids.xmu.edu.cn/authserver/login?type=userNameLogin&service=...`
- 密码：AES-128-CBC，key=`pwdEncryptSalt`，IV=随机 16 字符，明文=`64随机+密码`，PKCS7
- 课表：`POST /gsapp/sys/wdkbapp/wdkcb/queryXspkjg.do`（先 GET 入口 index.do）
- UA：固定桌面版 Chrome（与 Android / 脚本一致）

## 6. 打包输出

```
artifacts/
  XMUTimeTable_<ver>_iphoneos-arm64.deb
  XMUTimeTable_<ver>.ipa
```

- deb：`/Applications/XMUTimeTable.app`，越狱包管理器安装
- ipa：`Payload/XMUTimeTable.app`，TrollStore 安装

## 7. 风险与回退

| 风险 | 回退 |
|---|---|
| 教务登录出现验证码 | UI 明确报错，提示改用手动 `.sleepy` 导入 |
| WidgetKit appex 装不上 | 二期单独做；主 App 不依赖小组件 |
| SwiftUI 某些 API 在 iOS15 边缘 | 最低部署 15.0，避免 16+ API |
| libxml2/gold 等宿主依赖 | 已在 WSL 修复并记录于会话 |

## 8. 验收

- [ ] `make package` 成功产出 deb
- [ ] `package.sh` 产出 deb + ipa
- [ ] 可导入 `.sleepy` 并在周视图显示
- [ ] 可手动添加/删除课程
- [ ] 厦大账号登录可拉取课表（真实环境）
- [ ] 课前通知可排程
