import SwiftUI

struct ReminderView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var courseEnabled = true
    @State private var minutes = 10
    @State private var dailyEnabled = false
    @State private var hour = 22
    @State private var minute = 0
    @State private var authDenied = false

    var body: some View {
        NavigationView {
            Form {
                Section("课前提醒") {
                    Toggle("启用课前提醒", isOn: $courseEnabled)
                    Stepper("提前 \(minutes) 分钟", value: $minutes, in: 1...60)
                }
                Section("每日提醒") {
                    Toggle("启用每日课表提醒", isOn: $dailyEnabled)
                    DatePicker("时间", selection: Binding(
                        get: {
                            var c = DateComponents()
                            c.hour = hour
                            c.minute = minute
                            return Calendar.current.date(from: c) ?? Date()
                        },
                        set: { d in
                            let c = Calendar.current.dateComponents([.hour, .minute], from: d)
                            hour = c.hour ?? 22
                            minute = c.minute ?? 0
                        }
                    ), displayedComponents: .hourAndMinute)
                }
                if authDenied {
                    Section {
                        Text("系统通知权限未开启，请到 设置 > 通知 中允许「厦大课表」发送通知。")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                }
                Section {
                    Button("立即重建提醒") {
                        saveAndReschedule()
                    }
                }
                Section {
                    Text("应用会在课表数据变化、回到前台或点此按钮时重建未来通知。iOS 限制后台刷新，不保证与 Android 完全一致的即时性。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("提醒设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") {
                        saveAndReschedule()
                        dismiss()
                    }
                }
            }
            .onAppear {
                let n = NotificationService.shared
                courseEnabled = n.courseReminderEnabled
                minutes = n.reminderMinutesBefore
                dailyEnabled = n.dailyReminderEnabled
                hour = n.dailyReminderHour
                minute = n.dailyReminderMinute
                UNUserNotificationCenter.current().getNotificationSettings { s in
                    DispatchQueue.main.async {
                        authDenied = s.authorizationStatus == .denied
                    }
                }
            }
        }
    }

    private func saveAndReschedule() {
        let n = NotificationService.shared
        n.courseReminderEnabled = courseEnabled
        n.reminderMinutesBefore = minutes
        n.dailyReminderEnabled = dailyEnabled
        n.dailyReminderHour = hour
        n.dailyReminderMinute = minute
        n.requestAuthorization { ok in
            authDenied = !ok
            app.rescheduleNotifications()
            if ok { app.showToast("提醒已更新") }
        }
    }
}
