import Foundation
import SwiftUI

/// 应用全局状态
final class AppState: ObservableObject {
    @Published var tables: [TimeTable] = []
    @Published var currentTable: TimeTable?
    @Published var courses: [Course] = []
    @Published var selectedWeek: Int = 1
    @Published var viewMode: ViewMode = .week
    @Published var themeColor: Color = Color(hex: "#FF6750A4")
    @Published var toast: String?

    enum ViewMode: String, CaseIterable, Identifiable {
        case today = "今日"
        case week = "周视图"
        case grid = "网格"
        var id: String { rawValue }
    }

    private let db = DatabaseService.shared

    init() {
        reloadTables()
        if let t = currentTable {
            selectedWeek = max(1, DateUtils.currentWeek(startDate: t.startDate))
        }
    }

    func reloadTables() {
        tables = db.listTables()
        if currentTable == nil || tables.contains(where: { $0.id == currentTable?.id }) == false {
            currentTable = db.defaultTable() ?? tables.first
        } else if let id = currentTable?.id {
            currentTable = db.table(id: id)
        }
        reloadCourses()
        if let t = currentTable {
            themeColor = Color(hex: t.color)
        }
    }

    func reloadCourses() {
        guard let t = currentTable else {
            courses = []
            WidgetBridge.publish(table: nil, courses: [])
            return
        }
        courses = db.courses(tableId: t.id)
        WidgetBridge.publish(table: t, courses: courses)
    }

    func select(table: TimeTable) {
        currentTable = table
        selectedWeek = max(1, DateUtils.currentWeek(startDate: table.startDate))
        reloadCourses()
        themeColor = Color(hex: table.color)
    }

    func coursesOn(day: Int, week: Int? = nil) -> [Course] {
        let w = week ?? selectedWeek
        return courses.filter { $0.day == day && $0.inWeek(w) }
            .sorted { $0.startNode < $1.startNode }
    }

    func todayCourses() -> [Course] {
        let week = currentTable.map { DateUtils.currentWeek(startDate: $0.startDate) } ?? 1
        selectedWeek = max(1, week)
        return coursesOn(day: DateUtils.dayOfWeek(), week: week)
    }

    func addCourse(_ c: Course) {
        var cc = c
        cc.tableId = currentTable?.id ?? 0
        if cc.groupId.isEmpty { cc.groupId = UUID().uuidString }
        db.insertCourse(cc)
        reloadCourses()
        rescheduleNotifications()
    }

    func updateCourse(_ c: Course) {
        db.updateCourse(c)
        reloadCourses()
        rescheduleNotifications()
    }

    func deleteCourse(_ c: Course) {
        db.deleteCourse(id: c.id)
        reloadCourses()
        rescheduleNotifications()
    }

    func deleteGroup(_ c: Course) {
        db.deleteGroup(groupId: c.groupId, tableId: c.tableId)
        reloadCourses()
        rescheduleNotifications()
    }

    func saveTable(_ t: TimeTable) {
        if t.id == 0 {
            db.createTable(t)
        } else {
            db.updateTable(t)
        }
        reloadTables()
    }

    func addTable(name: String) {
        _ = db.createTable(TimeTable(
            name: name.isEmpty ? "新课表" : name,
            startDate: DateUtils.todayMondayString()
        ))
        reloadTables()
    }

    func deleteCurrentTable() {
        guard let t = currentTable, tables.count > 1 else { return }
        db.deleteTable(id: t.id)
        currentTable = nil
        reloadTables()
    }

    func importSleepy(text: String, mode: ImportMode) throws {
        guard let t = currentTable else { return }
        let result = try SleepyParser.detectAndParse(
            text: text, defaultTableId: t.id, defaultColor: t.color
        )
        var table = t
        if mode == .replace || mode == .newTable {
            var newTable = t
            newTable.id = 0
            newTable.name = result.tableName.isEmpty ? t.name : result.tableName
            newTable.startDate = result.startDate
            newTable.maxWeek = max(result.maxWeek, 12)
            newTable.nodesPerDay = result.nodesPerDay
            if !result.timeJson.isEmpty {
                newTable.timeJson = result.timeJson
            }
            newTable.isDefault = tables.count == 0
            let newId = db.createTable(newTable)
            db.replaceCourses(tableId: newId, courses: result.courses)
            reloadTables()
            if let created = db.table(id: newId) {
                select(table: created)
            }
        } else {
            // append
            var merged = t.timeJson
            if !result.timeJson.isEmpty {
                let reach = result.courses.map { $0.startNode + $0.step - 1 }.max() ?? 0
                merged = TimeTableUtils.mergeMostComplete(current: t.timeJson, incoming: result.timeJson, requiredNodeCount: reach)
            }
            table.timeJson = merged
            table.nodesPerDay = max(table.nodesPerDay, result.nodesPerDay)
            table.maxWeek = max(table.maxWeek, result.maxWeek)
            db.updateTable(table)
            for c in result.courses {
                var cc = c
                cc.tableId = t.id
                db.insertCourse(cc)
            }
            reloadTables()
        }
        showToast(result.warnings.isEmpty ? "导入成功：\(result.courses.count) 节课" : result.warnings.joined(separator: "; "))
    }

    func exportSleepyFile() -> String {
        guard let t = currentTable else { return "" }
        return SleepyExporter.exportFile(
            tableName: t.name, startDate: t.startDate, maxWeek: t.maxWeek,
            nodesPerDay: t.nodesPerDay, timeJson: t.timeJson, courses: courses
        )
    }

    func exportICS() -> String {
        guard let t = currentTable else { return "" }
        return ICSExporter.export(table: t, courses: courses)
    }

    func rescheduleNotifications() {
        guard let t = currentTable else { return }
        NotificationService.shared.reschedule(table: t, courses: courses)
    }

    func showToast(_ msg: String) {
        toast = msg
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { [weak self] in
            if self?.toast == msg { self?.toast = nil }
        }
    }
}

enum ImportMode {
    case replace
    case append
    case newTable
}

extension Color {
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        if s.count == 6 { s = "FF" + s }
        var value: UInt64 = 0
        Scanner(string: s).scanHexInt64(&value)
        let a = Double((value >> 24) & 0xFF) / 255.0
        let r = Double((value >> 16) & 0xFF) / 255.0
        let g = Double((value >> 8) & 0xFF) / 255.0
        let b = Double(value & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: a)
    }
}
