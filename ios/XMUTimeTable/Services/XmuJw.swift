import Foundation

/// 厦大教务常量与纯函数 — 对齐 Android XmuJw
enum XmuJw {
    static let idpBase = "https://ids.xmu.edu.cn"
    static let jwBase = "https://jw.xmu.edu.cn"
    static let portalService = jwBase + "/login?service=" + jwBase + "/new/index.html"
    static let loginUrl = jwBase + "/login"
    static let desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    static let appEntryPath = "/gsapp/sys/wdkbapp/*default/index.do?EMAP_LANG=zh&THEME=cherry"
    static let gsappApiBase = jwBase + "/gsapp/sys/wdkbapp"

    static let fallbackPeriod: [(Int, String, String)] = [
        (1, "08:00", "08:45"), (2, "08:55", "09:40"),
        (3, "10:10", "10:55"), (4, "11:05", "11:50"),
        (5, "14:30", "15:15"), (6, "15:25", "16:10"),
        (7, "16:40", "17:25"), (8, "17:35", "18:20"),
        (9, "19:10", "19:55"), (10, "20:05", "20:50"),
        (11, "21:00", "21:45")
    ]

    static func encode(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? s
    }

    static func inferSemesterCode(now: Date = Date()) -> String? {
        let cal = Calendar(identifier: .gregorian)
        let year = cal.component(.year, from: now)
        let month = cal.component(.month, from: now)
        let cur = month >= 9 ? year : year - 1
        if (1000...9999).contains(cur) { return "\(cur)1" }
        return nil
    }

    /// 解析金智课表 JSON 为 Course 数组
    static func parseCourseJson(
        _ json: String, tableId: Int64, timeJson: String, maxWeek: Int
    ) -> (courses: [Course], timeJson: String, maxWeek: Int, startDate: String)? {
        guard let data = json.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }

        var rows: [[String: Any]] = []
        if let datas = root["datas"] as? [String: Any] {
            for (_, v) in datas {
                if let d = v as? [String: Any], let r = d["rows"] as? [[String: Any]] {
                    rows = r
                    break
                }
            }
        }
        if rows.isEmpty, let r = root["rows"] as? [[String: Any]] {
            rows = r
        }
        if rows.isEmpty { return nil }

        var courses: [Course] = []
        var maxWeekSeen = 1
        var maxNodeSeen = 0
        var colorIdx = 0
        let palette = SleepyFormat.palette

        for row in rows {
            guard let name = (row["KCMC"] as? String ?? row["courseName"] as? String ?? row["kcmc"] as? String)?
                .trimmingCharacters(in: .whitespaces),
                  !name.isEmpty else { continue }

            let teacher = (row["JSXM"] as? String ?? row["teacher"] as? String ?? "") as String
            let room = (row["JASMC"] as? String ?? row["room"] as? String ?? "") as String
            let day = intOr(row["XQ"] ?? row["day"] ?? row["XQJ"], 1)
            let startNode = intOr(row["KSJC"] ?? row["startNode"] ?? row["start"], 1)
            let endNode = intOr(row["JSJC"] ?? row["endNode"] ?? row["end"], startNode)
            let startWeek = intOr(row["KSZ"] ?? row["startWeek"] ?? row["start"], 1)
            let endWeek = intOr(row["JSZ"] ?? row["endWeek"] ?? row["end"], 16)
            var weekType = intOr(row["SFJSMC"] ?? row["type"] ?? row["weekType"], 0)
            // 金智有时用文字描述单双周
            if let t = row["SFJSMC"] as? String {
                if t.contains("单") { weekType = 1 }
                else if t.contains("双") { weekType = 2 }
                else if t.contains("周") && !t.contains("单") && !t.contains("双") { weekType = 0 }
            }

            let step = max(1, endNode - startNode + 1)
            maxWeekSeen = max(maxWeekSeen, endWeek)
            maxNodeSeen = max(maxNodeSeen, endNode)

            colorIdx = (colorIdx % 9) + 1
            let color = palette[colorIdx] ?? SleepyFormat.autoColor

            courses.append(Course(
                id: 0,
                groupId: UUIDv5(namespace: name + "|" + teacher),
                tableId: tableId,
                courseName: name,
                teacher: teacher,
                room: room,
                note: "",
                day: max(1, min(7, day)),
                startNode: max(1, startNode),
                step: step,
                startWeek: max(1, startWeek),
                endWeek: max(endWeek, startWeek),
                type: weekType,
                color: color
            ))
        }

        return (courses, timeJson, max(maxWeek, maxWeekSeen), DateUtils.todayMondayString())
    }

    private static func intOr(_ any: Any?, _ def: Int) -> Int {
        if let i = any as? Int { return i }
        if let n = any as? NSNumber { return n.intValue }
        if let s = any as? String { return Int(s.trimmingCharacters(in: .whitespaces)) ?? def }
        return def
    }
}
