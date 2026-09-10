import Foundation

/// 小组件共享快照 — 主 App 写入，Widget 扩展读取
struct WidgetSnapshot: Codable, Equatable {
    var tableName: String
    var updatedAt: Double
    var startDate: String
    var maxWeek: Int
    var nodesPerDay: Int
    var timeSlots: [WidgetSlot]
    var courses: [WidgetCourse]

    static let empty = WidgetSnapshot(
        tableName: "厦大课表",
        updatedAt: 0,
        startDate: "",
        maxWeek: 20,
        nodesPerDay: 12,
        timeSlots: [],
        courses: []
    )
}

struct WidgetSlot: Codable, Equatable, Identifiable {
    var node: Int
    var start: String
    var end: String
    var id: Int { node }
}

struct WidgetCourse: Codable, Equatable, Identifiable {
    var id: Int64
    var groupId: String
    var name: String
    var teacher: String
    var room: String
    var day: Int
    var startNode: Int
    var step: Int
    var startWeek: Int
    var endWeek: Int
    var type: Int
    var color: String

    var endNode: Int { startNode + max(step, 1) - 1 }

    func inWeek(_ week: Int) -> Bool {
        if week < startWeek || week > endWeek { return false }
        switch type {
        case 1: return week % 2 == 1
        case 2: return week % 2 == 0
        default: return true
        }
    }
}

enum WidgetDateHelper {
    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .iso8601)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    static func parse(_ s: String) -> Date? {
        formatter.date(from: s)
    }

    static func format(_ d: Date) -> String {
        formatter.string(from: d)
    }

    static func mondayOf(_ date: Date) -> Date {
        var cal = Calendar(identifier: .iso8601)
        cal.firstWeekday = 2
        cal.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        let weekday = cal.component(.weekday, from: date)
        let offset = (weekday == 1) ? 6 : (weekday - 2)
        return cal.date(byAdding: .day, value: -offset, to: cal.startOfDay(for: date)) ?? date
    }

    static func dayOfWeek(_ date: Date = Date()) -> Int {
        var cal = Calendar(identifier: .iso8601)
        cal.firstWeekday = 2
        cal.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        let weekday = cal.component(.weekday, from: date)
        return weekday == 1 ? 7 : weekday - 1
    }

    static func currentWeek(startDate: String, today: Date = Date()) -> Int {
        guard let start = parse(startDate) else { return 1 }
        var cal = Calendar(identifier: .iso8601)
        cal.timeZone = TimeZone(identifier: "Asia/Shanghai") ?? .current
        let days = cal.dateComponents([.day], from: mondayOf(start), to: mondayOf(today)).day ?? 0
        return max(1, days / 7 + 1)
    }

    static func dateOfWeek(startDate: String, week: Int, dayOfWeek: Int) -> Date? {
        guard let start = parse(startDate) else { return nil }
        return Calendar.current.date(
            byAdding: .day,
            value: (week - 1) * 7 + (dayOfWeek - 1),
            to: mondayOf(start)
        )
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

    static func courseTime(startNode: Int, endNode: Int, slots: [WidgetSlot]) -> String {
        guard let s = slots.first(where: { $0.node == startNode }),
              let e = slots.first(where: { $0.node == endNode })
        else { return "\(startNode)-\(endNode)节" }
        return "\(s.start)-\(e.end)"
    }
}

/// 小组件数据读写 — App Group 优先，越狱环境回退到共享文件
enum WidgetStore {
    static let appGroup = "group.com.arroghie.xmutimetable"
    static let key = "snapshot"
    /// 越狱无容器场景下的共享路径（主 App 与 Widget 均可读）
    static let sharedFileURL = URL(fileURLWithPath: "/var/mobile/Documents/com.arroghie.xmutimetable/widget.json")
    static let legacyPreferenceKey = "com.arroghie.xmutimetable.widget"

    static func save(_ snapshot: WidgetSnapshot) {
        let encoder = JSONEncoder()
        guard let data = try? encoder.encode(snapshot) else { return }
        UserDefaults(suiteName: appGroup)?.set(data, forKey: key)
        // also write plain file for jailbreak
        do {
            try FileManager.default.createDirectory(
                at: sharedFileURL.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try data.write(to: sharedFileURL, options: .atomic)
            try? FileManager.default.setAttributes(
                [.posixPermissions: 0o644],
                ofItemAtPath: sharedFileURL.path
            )
        } catch {
            // ignore file write failures on non-jailbreak sandbox
        }
    }

    static func load() -> WidgetSnapshot {
        if let data = UserDefaults(suiteName: appGroup)?.data(forKey: key),
           let snap = try? JSONDecoder().decode(WidgetSnapshot.self, from: data) {
            return snap
        }
        if let data = try? Data(contentsOf: sharedFileURL),
           let snap = try? JSONDecoder().decode(WidgetSnapshot.self, from: data) {
            return snap
        }
        return .empty
    }

    static func courses(on day: Int, week: Int, from snapshot: WidgetSnapshot) -> [WidgetCourse] {
        snapshot.courses
            .filter { $0.day == day && $0.inWeek(week) }
            .sorted { $0.startNode < $1.startNode }
    }
}
