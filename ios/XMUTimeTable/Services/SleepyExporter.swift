import Foundation

/// sleepy-v1 导出器 — 对齐 Android SleepyNativeExporter
enum SleepyExporter {
    static func exportFile(
        tableName: String, startDate: String, maxWeek: Int, nodesPerDay: Int,
        timeJson: String, courses: [Course]
    ) -> String {
        let body = buildBody(
            tableName: tableName, startDate: startDate, maxWeek: maxWeek,
            nodesPerDay: nodesPerDay, timeJson: timeJson, courses: courses
        )
        return body + "\nz|chk=crc32:" + SleepyFormat.crc32(Data(body.utf8))
    }

    static func exportShareText(
        tableName: String, startDate: String, maxWeek: Int, nodesPerDay: Int,
        timeJson: String, courses: [Course]
    ) -> String {
        let body = buildBody(
            tableName: tableName, startDate: startDate, maxWeek: maxWeek,
            nodesPerDay: nodesPerDay, timeJson: timeJson, courses: courses
        )
        return "【来自Sleepy】\n课程分享：\n\n<<<SLEEPY-BEGIN>>>\n\(body)\n<<<SLEEPY-END>>>"
    }

    private static func buildBody(
        tableName: String, startDate: String, maxWeek: Int, nodesPerDay: Int,
        timeJson: String, courses: [Course]
    ) -> String {
        var sb = ""
        sb += "#sleepy-v1\n"
        sb += "T" + SleepyFormat.escape(tableName.isEmpty ? "导入的课表" : tableName)
        sb += "|" + startDate
        sb += "|\(maxWeek)"
        sb += "|\(nodesPerDay)"
        sb += "|n=\(courses.count)\n"

        if SleepyFormat.matchesNdPreset(timeJson) {
            sb += "Nd\n"
        } else if !timeJson.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            for n in TimeTableUtils.parseNodes(timeJson) {
                sb += "N\(n.node)|\(n.start)|\(n.end)\n"
            }
        }

        let hasSameNameMultiGroup = hasSameNameMultipleGroups(courses)
        for line in exportCourses(courses, forceToken: hasSameNameMultiGroup) {
            sb += line + "\n"
        }
        if sb.hasSuffix("\n") { sb.removeLast() }
        return sb
    }

    private static func exportCourses(_ courses: [Course], forceToken: Bool) -> [String] {
        let sorted = courses.sorted {
            if $0.groupId != $1.groupId { return $0.groupId < $1.groupId }
            return $0.courseName < $1.courseName
        }
        var tokenMap: [String: String] = [:]
        var out: [String] = []
        for c in sorted {
            let token: String
            if c.groupId.isEmpty {
                token = ""
            } else if forceToken {
                token = tokenMap[c.groupId] ?? {
                    let t = "\(tokenMap.count + 1)"
                    tokenMap[c.groupId] = t
                    return t
                }()
            } else {
                let sameGroup = sorted.filter { $0.groupId == c.groupId }.count
                let sameName = sorted.filter { $0.courseName.trimmingCharacters(in: .whitespaces) == c.courseName.trimmingCharacters(in: .whitespaces) }.count
                if sameGroup > 0 && sameName > 1 {
                    token = tokenMap[c.groupId] ?? {
                        let t = "\(tokenMap.count + 1)"
                        tokenMap[c.groupId] = t
                        return t
                    }()
                } else {
                    token = ""
                }
            }
            out.append(buildCourseLine(c, token: token))
        }
        return out
    }

    private static func buildCourseLine(_ c: Course, token: String) -> String {
        var s = "C" + SleepyFormat.escape(c.courseName)
        s += "|\(c.day)"
        s += "|\(c.startNode)-\(c.endNode)"
        s += "|" + SleepyFormat.weekSpecToToken(start: c.startWeek, end: c.endWeek, type: c.type)
        s += "|" + SleepyFormat.escape(c.teacher)
        s += "|" + SleepyFormat.escape(c.room)
        s += "|" + SleepyFormat.colorToToken(c.color)
        s += "|" + SleepyFormat.escape(c.note)
        if c.ownTime && !c.startTime.isEmpty && !c.endTime.isEmpty {
            s += "|\(c.startTime)-\(c.endTime)"
        } else {
            s += "|"
        }
        s += "|" + token
        return s
    }

    private static func hasSameNameMultipleGroups(_ courses: [Course]) -> Bool {
        let byName = Dictionary(grouping: courses) { $0.courseName.trimmingCharacters(in: .whitespaces) }
        return byName.values.contains { group in
            Set(group.map(\.groupId)).count > 1
        }
    }
}
