import Foundation
import WidgetKit

/// 主 App → Widget 快照导出
enum WidgetBridge {
    static func publish(table: TimeTable?, courses: [Course]) {
        guard let table else {
            WidgetStore.save(.empty)
            WidgetCenter.shared.reloadAllTimelines()
            return
        }
        let slots = TimeTableUtils.parseNodes(table.timeJson).map {
            WidgetSlot(node: $0.node, start: $0.start, end: $0.end)
        }
        let wcourses = courses.map {
            WidgetCourse(
                id: $0.id,
                groupId: $0.groupId,
                name: $0.courseName,
                teacher: $0.teacher,
                room: $0.room,
                day: $0.day,
                startNode: $0.startNode,
                step: $0.step,
                startWeek: $0.startWeek,
                endWeek: $0.endWeek,
                type: $0.type,
                color: $0.color
            )
        }
        let snap = WidgetSnapshot(
            tableName: table.name,
            updatedAt: Date().timeIntervalSince1970,
            startDate: table.startDate,
            maxWeek: table.maxWeek,
            nodesPerDay: table.nodesPerDay,
            timeSlots: slots,
            courses: wcourses
        )
        WidgetStore.save(snap)
        WidgetCenter.shared.reloadAllTimelines()
    }
}
