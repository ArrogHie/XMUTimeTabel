import Foundation
import CryptoKit

/// sleepy-v1 解析器 — 对齐 Android SleepyNativeParser 语义
enum SleepyParser {
    static func detectAndParse(text: String, defaultTableId: Int64, defaultColor: String) throws -> ParseResult {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard SleepyFormat.detectVersion(trimmed: trimmed) >= 1 else {
            throw AppError.message("不是 sleepy-v1 文本")
        }
        return parse(trimmed: trimmed, defaultTableId: defaultTableId, defaultColor: defaultColor)
    }

    static func parse(trimmed: String, defaultTableId: Int64, defaultColor: String) -> ParseResult {
        let lines = trimmed
            .split(separator: "\n", omittingEmptySubsequences: false)
            .map { $0.hasSuffix("\r") ? String($0.dropLast()) : String($0) }

        var dropped: [String] = []
        var warnings: [String] = []

        var tableName = ""
        var startDateStr = ""
        var maxWeekRaw: Int?
        var nodesPerDayRaw: Int?
        var declaredCount: Int?
        var bodyStart = 0
        var foundMagic = false
        var tSeen = false

        for (idx, raw) in lines.enumerated() {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }
            if !foundMagic {
                if SleepyFormat.isMagicLine(line), idx < 32 {
                    foundMagic = true
                    bodyStart = idx + 1
                }
                continue
            }
            let upper = line.uppercased()
            if !tSeen && upper.hasPrefix("T") && !upper.hasPrefix("ND") {
                tSeen = true
                let cols = SleepyFormat.splitRespectingEscape(String(line.dropFirst()))
                if !cols.isEmpty {
                    tableName = SleepyFormat.unescape(cols[0].trimmingCharacters(in: .whitespaces))
                    if cols.count > 1 { startDateStr = cols[1].trimmingCharacters(in: .whitespaces) }
                    if cols.count > 2 { maxWeekRaw = Int(cols[2].trimmingCharacters(in: .whitespaces)) }
                    if cols.count > 3 { nodesPerDayRaw = Int(cols[3].trimmingCharacters(in: .whitespaces)) }
                    for kv in cols.dropFirst(4) {
                        let parts = kv.split(separator: "=", maxSplits: 1)
                        if parts.count == 2, parts[0].trimmingCharacters(in: .whitespaces) == "n" {
                            declaredCount = Int(parts[1].trimmingCharacters(in: .whitespaces))
                        }
                    }
                }
            }
        }

        var startDate = DateUtils.todayMondayString()
        if !startDateStr.isEmpty {
            if let d = SleepyFormat.parseDate(startDateStr) {
                startDate = d
            } else {
                warnings.append("开始日期「\(startDateStr)」无法解析，已使用今天")
            }
        }

        let maxWeekClamped = clampField(maxWeekRaw, def: 20, lo: 1, hi: 60, label: "总周数", warnings: &warnings)
        let declaredNodes = clampField(nodesPerDayRaw, def: nil, lo: 1, hi: 30, label: "每天节数", warnings: &warnings)

        var courses: [Course] = []
        var tokenByIndex: [Int: String] = [:]
        var nodeTimes: [Int: (String, String)] = [:]
        var ndSeen = false
        var seenExact = Set<String>()
        var seenCourseLines = false
        var secondHeader = false
        var chkLine: String?

        for raw in lines.dropFirst(bodyStart) {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }
            if line.hasPrefix("#") {
                if SleepyFormat.isMagicLine(line) {
                    dropped.append(String(line.prefix(40)))
                    secondHeader = true
                }
                continue
            }
            if SleepyFormat.isMagicLine(line) {
                dropped.append(String(line.prefix(40)))
                secondHeader = true
                continue
            }
            if seenExact.contains(line) {
                dropped.append(String(line.prefix(40)))
                continue
            }
            guard let prefix = line.first else { continue }
            let p = String(prefix).uppercased()
            switch p {
            case "T":
                if tSeen { continue }
                dropped.append(String(line.prefix(40)))
            case "Z":
                chkLine = line
            case "N":
                if line.count >= 2 {
                    let second = line[line.index(line.startIndex, offsetBy: 1)]
                    if second == "d" || second == "D" {
                        ndSeen = true
                    } else {
                        seenExact.insert(line)
                        parseNodeLine(line, nodeTimes: &nodeTimes, dropped: &dropped)
                    }
                }
            case "C":
                seenCourseLines = true
                seenExact.insert(line)
                parseCourseLine(line, tableId: defaultTableId, defaultColor: defaultColor,
                                courses: &courses, dropped: &dropped, tokenByIndex: &tokenByIndex)
            default:
                dropped.append(String(line.prefix(40)))
            }
        }

        if ndSeen {
            for (i, preset) in SleepyFormat.ndPreset.enumerated() {
                let node = i + 1
                if nodeTimes[node] == nil {
                    nodeTimes[node] = preset
                }
            }
        }

        if let chk = chkLine,
           let regex = try? NSRegularExpression(pattern: "chk=([a-z0-9]+):([0-9a-fA-F]{8})") {
            let range = NSRange(chk.startIndex..., in: chk)
            if let match = regex.firstMatch(in: chk, range: range),
               let algoR = Range(match.range(at: 1), in: chk),
               let valR = Range(match.range(at: 2), in: chk) {
                let algo = String(chk[algoR])
                if algo == "crc32" {
                    if let zIdx = lines.firstIndex(where: { $0.trimmingCharacters(in: .whitespaces) == chk }) {
                        let body = lines[..<zIdx].joined(separator: "\n")
                        let actual = SleepyFormat.crc32(Data(body.utf8))
                        if actual.lowercased() != String(chk[valR]).lowercased() {
                            warnings.append("完整性校验不符，文件可能被截断或修改")
                        }
                    }
                } else {
                    warnings.append("未知校验算法 \(algo)，已跳过校验")
                }
            }
        }

        if let n = declaredCount, n != courses.count {
            warnings.append("课程行计数 n=\(n) 与实际 \(courses.count) 不符")
        }
        if secondHeader {
            warnings.append("检测到第 2 张表头，其课程已并入当前表")
        }

        let courseReach = courses.map { $0.startNode + $0.step - 1 }.max() ?? 0
        var nodesPerDay = declaredNodes ?? max(12, nodeTimes.keys.max() ?? 0)
        if declaredNodes == nil && nodeTimes.isEmpty && !courses.isEmpty {
            nodesPerDay = max(12, courseReach)
        }
        if courseReach > nodesPerDay {
            nodesPerDay = courseReach
            warnings.append("课程到达第 \(courseReach) 节，已自动扩展每天节数")
        }

        let timeJson: String
        if nodeTimes.isEmpty {
            timeJson = ""
        } else {
            let rows = nodeTimes.keys.sorted().map {
                TimeTableUtils.NodeTime(node: $0, start: nodeTimes[$0]!.0, end: nodeTimes[$0]!.1)
            }
            timeJson = TimeTableUtils.buildJson(rows)
        }

        assignGroupIds(&courses, tokens: tokenByIndex, tableName: tableName)

        if seenCourseLines && courses.isEmpty {
            // keep empty result with warnings rather than throwing hard — UI can show
            warnings.append("未能解析任何课程（\(dropped.count) 行被丢弃）")
        }

        return ParseResult(
            tableName: tableName,
            startDate: startDate,
            courses: courses,
            timeJson: timeJson,
            nodesPerDay: nodesPerDay,
            droppedLines: dropped,
            warnings: warnings,
            maxWeek: maxWeekClamped ?? 20,
            groupIdsAuthoritative: true
        )
    }

    private static func clampField(_ raw: Int?, def: Int?, lo: Int, hi: Int, label: String, warnings: inout [String]) -> Int? {
        guard let raw else { return def }
        if raw < lo {
            warnings.append("\(label) \(raw) 低于下限，已调整为 \(lo)")
            return lo
        }
        if raw > hi {
            warnings.append("\(label) \(raw) 超过上限，已调整为 \(hi)")
            return hi
        }
        return raw
    }

    private static func parseNodeLine(_ line: String, nodeTimes: inout [Int: (String, String)], dropped: inout [String]) {
        let cols = SleepyFormat.splitRespectingEscape(String(line.dropFirst()))
        guard let nodeNo = Int(cols[safe: 0]?.trimmingCharacters(in: .whitespaces) ?? ""),
              nodeNo > 0,
              let st = cols[safe: 1].flatMap({ SleepyFormat.parseClock($0) }),
              let et = cols[safe: 2].flatMap({ SleepyFormat.parseClock($0) }),
              st.0 * 60 + st.1 < et.0 * 60 + et.1
        else {
            dropped.append(String(line.prefix(40)))
            return
        }
        if nodeTimes[nodeNo] != nil {
            dropped.append(String(line.prefix(40)))
            return
        }
        nodeTimes[nodeNo] = (TimeTableUtils.fmtTime(h: st.0, m: st.1), TimeTableUtils.fmtTime(h: et.0, m: et.1))
    }

    private static func parseCourseLine(
        _ line: String, tableId: Int64, defaultColor: String,
        courses: inout [Course], dropped: inout [String], tokenByIndex: inout [Int: String]
    ) {
        var cols = SleepyFormat.splitRespectingEscape(String(line.dropFirst()))
        if cols.count < 10 {
            let alt = SleepyFormat.splitRespectingEscape(String(line.dropFirst()).replacingOccurrences(of: "｜", with: "|"))
            if alt.count == 10 { cols = alt }
        }
        func col(_ i: Int) -> String {
            guard i < cols.count else { return "" }
            return cols[i].trimmingCharacters(in: .whitespaces)
        }
        func text(_ i: Int) -> String { SleepyFormat.unescape(col(i)) }

        let name = text(0)
        if name.isEmpty || name.contains("\u{FFFD}") {
            dropped.append(String(line.prefix(40)))
            return
        }

        var day: Int
        let dayRaw = col(1)
        if dayRaw.isEmpty {
            day = 1
        } else if let d = SleepyFormat.parseDay(dayRaw) {
            day = max(1, min(7, d))
            if d < 1 || d > 7 { dropped.append(String(line.prefix(40))) }
        } else {
            dropped.append(String(line.prefix(40)))
            return
        }

        let spanRaw = col(2)
        var nodeStart: Int
        var nodeEnd: Int
        if spanRaw.isEmpty {
            nodeStart = 1; nodeEnd = 1
        } else if let span = SleepyFormat.parseNodeSpan(spanRaw) {
            nodeStart = span.0; nodeEnd = span.1
            if nodeEnd < nodeStart { swap(&nodeStart, &nodeEnd); dropped.append(String(line.prefix(40))) }
            if nodeStart < 1 { nodeStart = 1; dropped.append(String(line.prefix(40))) }
        } else {
            dropped.append(String(line.prefix(40)))
            return
        }
        let step = nodeEnd - nodeStart + 1

        let weekRaw = col(3)
        var wStart: Int, wEnd: Int, wType: Int
        if weekRaw.isEmpty {
            wStart = 1; wEnd = 16; wType = 3
        } else if let w = SleepyFormat.parseWeekSpec(weekRaw) {
            wStart = w.start; wEnd = w.end; wType = w.type
            if wEnd < wStart { swap(&wStart, &wEnd); dropped.append(String(line.prefix(40))) }
            if wStart < 1 { wStart = 1; dropped.append(String(line.prefix(40))) }
            if wEnd > 300 { wEnd = 300; dropped.append(String(line.prefix(40))) }
        } else {
            dropped.append(String(line.prefix(40)))
            return
        }

        let teacher = text(4)
        let room = text(5)

        let colorRaw = col(6)
        var color: String
        if colorRaw.isEmpty || colorRaw == "0" {
            color = defaultColor.isEmpty ? SleepyFormat.autoColor : defaultColor
        } else {
            color = SleepyFormat.colorFromToken(colorRaw)
            if Int(colorRaw) != nil, Int(colorRaw)! > 9 {
                dropped.append(String(line.prefix(40)))
            }
        }

        let note = text(7)

        var ownTime = false
        var startTime = ""
        var endTime = ""
        let timeRaw = col(8)
        if !timeRaw.isEmpty {
            let parts = timeRaw.replacingOccurrences(of: "～", with: "-")
                .replacingOccurrences(of: "~", with: "-")
                .split(separator: "-", maxSplits: 1)
            if parts.count == 2,
               let st = SleepyFormat.parseClock(String(parts[0])),
               let et = SleepyFormat.parseClock(String(parts[1])),
               st.0 * 60 + st.1 < et.0 * 60 + et.1 {
                ownTime = true
                startTime = TimeTableUtils.fmtTime(h: st.0, m: st.1)
                endTime = TimeTableUtils.fmtTime(h: et.0, m: et.1)
            } else {
                dropped.append(String(line.prefix(40)))
            }
        }

        let token = text(9)
        let course = Course(
            id: 0, groupId: "", tableId: tableId,
            courseName: name, teacher: teacher, room: room, note: note,
            day: day, startNode: nodeStart, step: step,
            startWeek: wStart, endWeek: wEnd, type: wType,
            color: color, ownTime: ownTime, startTime: startTime, endTime: endTime
        )
        tokenByIndex[courses.count] = token
        courses.append(course)
    }

    private static func assignGroupIds(_ courses: inout [Course], tokens: [Int: String], tableName: String) {
        var tokenGroups: [String: String] = [:]
        var nameGroups: [String: String] = [:]
        for idx in courses.indices {
            let c = courses[idx]
            let token = tokens[idx] ?? ""
            let gid: String
            if !token.isEmpty {
                if let existing = tokenGroups[token] {
                    gid = existing
                } else {
                    gid = UUIDv5(namespace: tableName + "|" + token)
                    tokenGroups[token] = gid
                }
            } else {
                let key = c.courseName.trimmingCharacters(in: .whitespaces)
                    .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
                    .lowercased()
                if let existing = nameGroups[key] {
                    gid = existing
                } else {
                    gid = UUIDv5(namespace: tableName + "|" + key)
                    nameGroups[key] = gid
                }
            }
            courses[idx].groupId = gid
        }
    }
}

/// Deterministic UUID from name (UUID v5-like using SHA1)
func UUIDv5(namespace: String) -> String {
    let data = Data(namespace.utf8)
    var hash = [UInt8](repeating: 0, count: 20)
    // Use CryptoKit SHA1
    hash = Array(Insecure.SHA1.hash(data: data))
    var uuid = hash.prefix(16)
    uuid[6] = (uuid[6] & 0x0F) | 0x50 // version 5
    uuid[8] = (uuid[8] & 0x3F) | 0x80 // variant
    let hex = uuid.map { String(format: "%02x", $0) }.joined()
    return "\(hex.prefix(8))-\(hex.dropFirst(8).prefix(4))-\(hex.dropFirst(12).prefix(4))-\(hex.dropFirst(16).prefix(4))-\(hex.dropFirst(20).prefix(12))"
}

extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
