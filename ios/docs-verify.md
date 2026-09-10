# 构建与内容核对记录

## 已完成

1. Theos 同时构建 APPLICATION + APPEX（WidgetKit）
2. deb 内含 `/Applications/XMUTimeTable.app/PlugIns/XMUTimeTableWidget.appex`
3. ipa 内含 `Payload/XMUTimeTable.app/PlugIns/XMUTimeTableWidget.appex`
4. appex Info.plist：`NSExtensionPointIdentifier=com.apple.widgetkit-extension`
5. 三类 Widget：今日 / 最近两天 / 周课表网格
6. 主 App 在课表变更时 `WidgetBridge.publish` + `WidgetCenter.reloadAllTimelines`
7. 共享数据：App Group `group.com.arroghie.xmutimetable` + `/var/mobile/Documents/.../widget.json` 回退
8. 登录文案：明确「账号密码自动登录，无需验证码」
9. 宿主侧逻辑测试：单双周/固定周、周次计算、周一归一、sleepy week token

## 真机待验（无法在 WSL 模拟）

- 主屏添加小组件并读到课表数据
- 教务登录拉取课表
- 通知权限与课前提醒
