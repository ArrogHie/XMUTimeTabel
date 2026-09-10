import Foundation

/// 简化 ICS 导出（VCALENDAR + VEVENT）
enum ICSExporter {
    static func export(table: TimeTable, courses: [Course]) -> String {
        var lines: [String] = []
        lines.append("BEGIN:VCALENDAR")
        lines.append("VERSION:2.0")
        lines.append("PRODID:-//XMUTimeTable//iOS//CN")
        lines.append("CALSCALE:GREGORIAN")
        lines.append("X-WR-CALNAME:\(table.name)")

        let slots = TimeTableUtils.timeSlots(for: table.timeJson)
        let now = Date()

        for c in courses {
            guard let date = DateUtils.dateOfWeek(startDate: table.startDate, week: c.startWeek, dayOfWeek: c.day) else { continue }
            // emit each week occurrence
            for week in c.startWeek...max(c.endWeek, c.startWeek) {
                guard c.inWeek(week) else { continue }
                guard let dayDate = DateUtils.dateOfWeek(startDate: table.startDate, week: week, dayOfWeek: c.day) else { continue }
                if dayDate < Calendar.current.startOfDay(for: now).addingTimeInterval(-86400) { continue }

                let startT: String
                let endT: String
                if c.ownTime && !c.startTime.isEmpty && !c.endTime.isEmpty {
                    startT = c.startTime
                    endT = c.endTime
                } else {
                    guard let s = slots.first(where: { $0.node == c.startNode }),
                          let e = slots.first(where: { $0.node == c.endNode }) else { continue }
                    startT = s.start
                    endT = e.end
                }
                let dayStr = DateUtils.format(dayDate).replacingOccurrences(of: "-", with: "")
                let startStamp = dayStr + "T" + startT.replacingOccurrences(of: ":", with: "") + "00"
                let endStamp = dayStr + "T" + endT.replacingOccurrences(of: ":", with: "") + "00"
                let uid = "\(c.id)-\(week)-\(startStamp)@xmutimetable"

                lines.append("BEGIN:VEVENT")
                lines.append("UID:\(uid)")
                lines.append("DTSTART;TZID=Asia/Shanghai:\(startStamp)")
                lines.append("DTEND;TZID=Asia/Shanghai:\(endStamp)")
                lines.append("SUMMARY:\(escapeICS(c.courseName))")
                var loc = c.room
                if !c.teacher.isEmpty { loc = loc.isEmpty ? c.teacher : loc + " " + c.teacher }
                if !loc.isEmpty { lines.append("LOCATION:\(escapeICS(loc))") }
                lines.append("DESCRIPTION:\(escapeICS("第\(week)周 \(c.nodeRangeText())"))")
                lines.append("END:VEVENT")
            }
            _ = date
        }
        lines.append("END:VCALENDAR")
        return lines.joined(separator: "\r\n")
    }

    private static func escapeICS(_ s: String) -> String {
        s.replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: ",", with: "\\,")
            .replacingOccurrences(of: ";", with: "\\;")
            .replacingOccurrences(of: "\n", with: "\\n")
    }
}
