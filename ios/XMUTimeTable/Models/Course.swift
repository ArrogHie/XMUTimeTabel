import Foundation

/// 课程实体 — 与 Android CourseEntity 字段对齐
struct Course: Identifiable, Equatable, Codable {
    var id: Int64 = 0
    var groupId: String = ""
    var tableId: Int64 = 0
    var courseName: String
    var teacher: String = ""
    var room: String = ""
    var note: String = ""
    /// 1=周一 ... 7=周日
    var day: Int
    var startNode: Int
    var step: Int
    var startWeek: Int
    var endWeek: Int
    /// 0=每周 1=单周 2=双周 3=按周次
    var type: Int = 0
    var color: String = "#FF6750A4"
    var ownTime: Bool = false
    var startTime: String = ""
    var endTime: String = ""
    var credit: Float = 0
    var level: Int = 0

    var endNode: Int { startNode + max(step, 1) - 1 }

    /// 第 N 周是否上这门课
    func inWeek(_ week: Int) -> Bool {
        if week < startWeek || week > endWeek { return false }
        switch type {
        case 0: return true
        case 1: return week % 2 == 1
        case 2: return week % 2 == 0
        default: return true
        }
    }

    func nodeRangeText() -> String {
        if ownTime && !startTime.isEmpty && !endTime.isEmpty {
            return "\(startTime)-\(endTime)"
        }
        return "\(startNode)-\(endNode)节"
    }
}

struct ParseResult {
    var tableName: String = ""
    var startDate: String = ""
    var courses: [Course] = []
    var timeJson: String = ""
    var nodesPerDay: Int = 12
    var droppedLines: [String] = []
    var warnings: [String] = []
    var maxWeek: Int = 20
    var groupIdsAuthoritative: Bool = false
}
