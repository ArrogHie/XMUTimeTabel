import Foundation
import UserNotifications
import UIKit

/// 本地课程提醒 — 课前 N 分钟 + 每日课表
final class NotificationService {
    static let shared = NotificationService()

    private init() {}

    var reminderMinutesBefore: Int {
        get { UserDefaults.standard.object(forKey: "reminderMinutesBefore") as? Int ?? 10 }
        set { UserDefaults.standard.set(newValue, forKey: "reminderMinutesBefore") }
    }

    var dailyReminderEnabled: Bool {
        get { UserDefaults.standard.object(forKey: "dailyReminderEnabled") as? Bool ?? false }
        set { UserDefaults.standard.set(newValue, forKey: "dailyReminderEnabled") }
    }

    var dailyReminderHour: Int {
        get { UserDefaults.standard.object(forKey: "dailyReminderHour") as? Int ?? 22 }
        set { UserDefaults.standard.set(newValue, forKey: "dailyReminderHour") }
    }

    var dailyReminderMinute: Int {
        get { UserDefaults.standard.object(forKey: "dailyReminderMinute") as? Int ?? 0 }
        set { UserDefaults.standard.set(newValue, forKey: "dailyReminderMinute") }
    }

    var courseReminderEnabled: Bool {
        get { UserDefaults.standard.object(forKey: "courseReminderEnabled") as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: "courseReminderEnabled") }
    }

    func requestAuthorization(completion: @escaping (Bool) -> Void) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { ok, _ in
            DispatchQueue.main.async { completion(ok) }
        }
    }

    /// 课表变更后重建未来 7 天提醒
    func reschedule(table: TimeTable, courses: [Course]) {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()

        guard courseReminderEnabled else {
            if dailyReminderEnabled { scheduleDaily(table: table) }
            return
        }

        let today = Date()
        for offset in 0..<7 {
            guard let dayDate = Calendar.current.date(byAdding: .day, value: offset, to: today) else { continue }
            let week = DateUtils.currentWeek(startDate: table.startDate, today: dayDate)
            let dow = DateUtils.dayOfWeek(dayDate)
            let dayCourses = courses.filter { $0.day == dow && $0.inWeek(week) }
            for c in dayCourses {
                scheduleCourseReminder(course: c, table: table, dayDate: dayDate)
            }
        }
        if dailyReminderEnabled {
            scheduleDaily(table: table)
        }
    }

    private func scheduleCourseReminder(course: Course, table: TimeTable, dayDate: Date) {
        let slots = TimeTableUtils.timeSlots(for: table.timeJson)
        guard let slot = slots.first(where: { $0.node == course.startNode }) else { return }
        let startMinutes = TimeTableUtils.hhmmToMinutes(slot.start)
        let fire = startMinutes - reminderMinutesBefore
        guard fire >= 0, fire < 24 * 60 else { return }

        var comps = Calendar.current.dateComponents([.year, .month, .day], from: dayDate)
        comps.hour = fire / 60
        comps.minute = fire % 60
        comps.second = 0
        let content = UNMutableNotificationContent()
        content.title = "即将上课"
        content.body = "\(course.courseName) \(course.room.isEmpty ? "" : "· \(course.room)") 第\(course.startNode)-\(course.endNode)节"
        content.sound = .default

        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        let req = UNNotificationRequest(
            identifier: "course-\(course.id)-\(course.startNode)-\(DateUtils.format(dayDate))",
            content: content,
            trigger: trigger
        )
        UNUserNotificationCenter.current().add(req)
    }

    private func scheduleDaily(table: TimeTable) {
        var comps = DateComponents()
        comps.hour = dailyReminderHour
        comps.minute = dailyReminderMinute
        let content = UNMutableNotificationContent()
        content.title = "明日课表"
        content.body = "打开厦大课表查看「\(table.name)」安排"
        content.sound = .default
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
        let req = UNNotificationRequest(identifier: "daily-xmu", content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(req)
    }
}
