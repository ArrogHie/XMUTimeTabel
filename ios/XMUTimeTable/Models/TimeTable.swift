import Foundation

/// 课表实体 — 与 Android TimeTableEntity 对齐
struct TimeTable: Identifiable, Equatable, Codable {
    var id: Int64 = 0
    var name: String
    /// yyyy-MM-dd，规范为所在周周一
    var startDate: String
    var maxWeek: Int = 20
    var nodesPerDay: Int = 12
    var timeJson: String = TimeTableUtils.defaultTimeJson
    var color: String = "#FF6750A4"
    var isDefault: Bool = false
    var smartConfigJson: String = ""
    var createdAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
}

struct TimeSlot: Identifiable, Equatable {
    var id: Int { node }
    var node: Int
    var start: String
    var end: String
}
