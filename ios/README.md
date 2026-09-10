# 厦大课表 iOS（越狱 / 免签）

Theos + SwiftUI 原生工程，构建于 WSL，不依赖 macOS / Apple 签名。

## 依赖

- Theos（`/home/arroghie/theos`）
- iPhoneOS 16.5 SDK
- Linux clang/swiftc toolchain
- `binutils-gold`、`libxml2.so.2` 兼容链接（宿主 Ubuntu 26.04）

## 构建

```bash
export THEOS=/home/arroghie/theos
export PATH=$THEOS/bin:$PATH
cd ios
./scripts/package.sh
```

产物：

- `artifacts/com.arroghie.xmutimetable_*.deb` — 越狱包管理器（Sileo/Zebra）安装到 `/Applications`
- `artifacts/XMUTimeTable_*.ipa` — TrollStore 安装

## 一键 Theos

```bash
make package FINALPACKAGE=1
```

## 功能

- 今日 / 周视图 / 网格视图
- 桌面小组件：今日课程 / 最近两天 / 周课表网格（WidgetKit appex 嵌入 PlugIns）
- 多课表、课程增删改
- `.sleepy` 导入导出、ICS 导出
- 厦大教务自动登录
- 本地课前 / 每日提醒

## 最低系统

iOS 15.0，arm64。

## 与 Android 差异

小组件（WidgetKit）与灵动岛未包含在 1.0.0；OPPO 流体云、APK 自更新不可移植。
