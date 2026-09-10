import Foundation

/// 日期/周次工具 — 对齐 Android DateUtils
enum DateUtils {
    private static let isoFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .iso8601)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static func parse(_ s: String) -> Date? {
        isoFormatter.date(from: s.trimmingCharacters(in: .whitespaces))
    }

    static func format(_ d: Date) -> String {
        isoFormatter.string(from: d)
    }

    /// 归一到该日期所在周周一
    static func mondayOf(_ date: Date) -> Date {
        var cal = Calendar(identifier: .iso8601)
        cal.firstWeekday = 2 // Monday
        cal.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        let weekday = cal.component(.weekday, from: date)
        // weekday: Sun=1 ... Sat=7; Mon=2
        let offset = (weekday == 1) ? 6 : (weekday - 2)
        let start = cal.startOfDay(for: date)
        return cal.date(byAdding: .day, value: -offset, to: start) ?? date
    }

    static func normalizeStartDate(_ s: String) -> String {
        guard let d = parse(s) else { return s }
        return format(mondayOf(d))
    }

    /// 今天是星期几（1=周一 ... 7=周日）
    static func dayOfWeek(_ date: Date = Date()) -> Int {
        var cal = Calendar(identifier: .iso8601)
        cal.firstWeekday = 2
        cal.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        let weekday = cal.component(.weekday, from: date)
        return weekday == 1 ? 7 : weekday - 1
    }

    static func currentWeek(startDate: String, today: Date = Date()) -> Int {
        guard let start = parse(startDate) else { return 1 }
        let s = mondayOf(start)
        let t = mondayOf(today)
        var cal = Calendar(identifier: .iso8601)
        cal.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        let days = cal.dateComponents([.day], from: s, to: t).day ?? 0
        return max(1, days / 7 + 1)
    }

    static func dateOfWeek(startDate: String, week: Int, dayOfWeek: Int) -> Date? {
        guard let start = parse(startDate) else { return nil }
        let monday = mondayOf(start)
        return Calendar.current.date(byAdding: .day, value: (week - 1) * 7 + (dayOfWeek - 1), to: monday)
    }

    static func chineseDay(_ day: Int) -> String {
        switch day {
        case 1: return "周一"
        case 2: return "周二"
        case 3: return "周三"
        case 4: return "周四"
        case 5: return "周五"
        case 6: return "周六"
        case 7: return "周日"
        default: return ""
        }
    }

    static func shortDate(_ d: Date) -> String {
        let f = DateFormatter()
        f.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        f.dateFormat = "MM-dd"
        return f.string(from: d)
    }

    static func todayMondayString() -> String {
        format(mondayOf(Date()))
    }
}
