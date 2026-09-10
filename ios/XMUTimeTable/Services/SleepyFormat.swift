import Foundation

/// sleepy-v1 纯函数层 — 对齐 Android SleepyNativeFormat
enum SleepyFormat {
    static let autoColor = "#FF6750A4"

    static let palette: [Int: String] = [
        1: "#FFEADDFF", 2: "#FFFFD8E4", 3: "#FFFFDCC4",
        4: "#FFFFF2B8", 5: "#FFD4F7C5", 6: "#FFC5F2E3",
        7: "#FFC9E8FF", 8: "#FFCDD7FF", 9: "#FFF2C4DE"
    ]

    static let ndPreset: [(String, String)] = [
        ("08:00", "08:45"), ("08:55", "09:40"),
        ("10:00", "10:45"), ("10:55", "11:40"),
        ("14:00", "14:45"), ("14:55", "15:40"),
        ("16:00", "16:45"), ("16:55", "17:40"),
        ("19:00", "19:45"), ("19:55", "20:40"),
        ("20:50", "21:35"), ("21:45", "22:30")
    ]

    private static let reserved: Set<Character> = ["\\", "|", "\"", "\n", "\t", "<", "{", "("]

    static func detectVersion(trimmed: String) -> Int {
        var seen = 0
        for raw in trimmed.split(separator: "\n", omittingEmptySubsequences: true) {
            seen += 1
            if seen > 32 { return -1 }
            let t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if let v = matchMagic(t) { return v }
        }
        return -1
    }

    static func matchMagic(_ line: String) -> Int? {
        let lowered = line.lowercased()
        var i = lowered.startIndex
        var skip = 0
        while i < lowered.endIndex, skip < 4, (lowered[i] == ">" || lowered[i] == " " || lowered[i] == "\t") {
            i = lowered.index(after: i)
            skip += 1
        }
        var hashes = 0
        while i < lowered.endIndex, lowered[i] == "#" || lowered[i] == "＃" {
            hashes += 1
            i = lowered.index(after: i)
            if hashes >= 2 { break }
        }
        if hashes < 1 { return nil }
        while i < lowered.endIndex, lowered[i] == " " { i = lowered.index(after: i) }
        guard lowered[i...].hasPrefix("sleepy") else { return nil }
        i = lowered.index(i, offsetBy: 6)
        // optional separators
        while i < lowered.endIndex, [" ", "-", "_", "－"].contains(lowered[i]) {
            i = lowered.index(after: i)
        }
        guard i < lowered.endIndex, lowered[i] == "v" else { return nil }
        i = lowered.index(after: i)
        var digits = ""
        while i < lowered.endIndex, lowered[i].isNumber {
            digits.append(lowered[i])
            i = lowered.index(after: i)
        }
        return Int(digits)
    }

    static func isMagicLine(_ line: String) -> Bool {
        (detectVersion(trimmed: line) >= 1)
    }

    static func escape(_ s: String) -> String {
        if !s.contains(where: { reserved.contains($0) }) { return s }
        var out = ""
        for c in s {
            switch c {
            case "\\": out += "\\\\"
            case "|": out += "\\|"
            case "\"": out += "\\\""
            case "\n": out += "\\n"
            case "\t": out += "\\t"
            case "<", "{", "(": out += "\\\(c)"
            default: out.append(c)
            }
        }
        return out
    }

    static func unescape(_ s: String) -> String {
        if !s.contains("\\") { return s }
        var out = ""
        var i = s.startIndex
        while i < s.endIndex {
            let c = s[i]
            if c == "\\" {
                let n = s.index(after: i)
                if n < s.endIndex {
                    let nxt = s[n]
                    if nxt == "n" { out += "\n" }
                    else if nxt == "t" { out += "\t" }
                    else { out.append(nxt) }
                    i = s.index(after: n)
                    continue
                }
                out.append("\\")
                i = n
            } else {
                out.append(c)
                i = s.index(after: i)
            }
        }
        return out
    }

    static func normalizeColor(_ color: String) -> String? {
        let t = color.trimmingCharacters(in: .whitespaces).uppercased()
        guard t.hasPrefix("#") else { return nil }
        let hex = String(t.dropFirst())
        guard hex.allSatisfy({ $0.isHexDigit }), hex.count == 6 || hex.count == 8 else { return nil }
        return hex.count == 6 ? "#FF\(hex)" : "#\(hex)"
    }

    static func colorToToken(_ color: String) -> String {
        guard let norm = normalizeColor(color) else { return "" }
        if norm == autoColor.uppercased() { return "" }
        if let idx = palette.first(where: { $0.value.uppercased() == norm })?.key {
            return "\(idx)"
        }
        if norm.hasPrefix("#FF") { return "#" + norm.dropFirst(3) }
        return norm
    }

    static func colorFromToken(_ token: String) -> String {
        let t = token.trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return autoColor }
        if let idx = Int(t), let c = palette[idx] { return c }
        if t.hasPrefix("#") {
            let hex = String(t.dropFirst()).uppercased()
            switch hex.count {
            case 6: return "#FF\(hex)"
            case 8:
                return hex.allSatisfy { $0.isHexDigit } ? "#\(hex)" : autoColor
            default: return autoColor
            }
        }
        return autoColor
    }

    struct WeekSpec {
        var start: Int
        var end: Int
        var type: Int
    }

    /// 周次五形态解析
    static func parseWeekSpec(_ s: String) -> WeekSpec? {
        let t = s.trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return nil }
        // Simplified regex-like parse
        var rest = t
        var suffix = ""
        for suf in ["odd", "even", "单", "双", "定", "散", "奇", "偶", "o", "e"] {
            if rest.lowercased().hasSuffix(suf) {
                suffix = suf
                rest = String(rest.dropLast(suf.count))
                break
            }
        }
        // separators
        var startStr = rest, endStr: String? = nil
        if let r = rest.range(of: "[-~–—〜至]") {
            startStr = String(rest[..<r.lowerBound])
            endStr = String(rest[r.upperBound...])
        }
        guard let start = Int(startStr.trimmingCharacters(in: .whitespaces)) else { return nil }
        var end = start
        if let es = endStr {
            guard let e = Int(es.trimmingCharacters(in: .whitespaces)) else { return nil }
            end = e
        }
        let type: Int
        switch suffix.lowercased() {
        case "", " ":
            type = (endStr == nil) ? 3 : 0
        case "单", "奇", "o", "odd": type = 1
        case "双", "偶", "e", "even": type = 2
        case "定", "散": type = 3
        default: return nil
        }
        return WeekSpec(start: start, end: end, type: type)
    }

    static func weekSpecToToken(start: Int, end: Int, type: Int) -> String {
        let range = "\(start)-\(end)"
        switch type {
        case 1: return "\(range)单"
        case 2: return "\(range)双"
        case 3: return start == end ? "\(start)定" : "\(range)定"
        default: return range
        }
    }

    static func parseDay(_ s: String) -> Int? {
        let t = s.trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return nil }
        if t.count == 1, let d = t.first, d.isNumber { return Int(String(d)) }
        var stripped = t
        for p in ["礼拜", "星期", "周"] { stripped = stripped.replacingOccurrences(of: p, with: "") }
        let map: [String: Int] = [
            "一": 1, "二": 2, "三": 3, "四": 4,
            "五": 5, "六": 6, "日": 7, "天": 7
        ]
        if let v = map[stripped] { return v }
        if stripped.count == 1, let d = stripped.first, d.isNumber { return Int(String(d)) }
        return nil
    }

    static func parseClock(_ s: String) -> (Int, Int)? {
        let t = s.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "：", with: ":")
        let parts = t.split(separator: ":")
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]),
              (0...23).contains(h), (0...59).contains(m)
        else { return nil }
        return (h, m)
    }

    static func parseDate(_ s: String) -> String? {
        let t = s.trimmingCharacters(in: .whitespaces)
        let cleaned = t
            .replacingOccurrences(of: "/", with: "-")
            .replacingOccurrences(of: ".", with: "-")
            .replacingOccurrences(of: "／", with: "-")
        // support compact YYYYMMDD
        if cleaned.count == 8, cleaned.allSatisfy(\.isNumber) {
            let y = cleaned.prefix(4), m = cleaned.dropFirst(4).prefix(2), d = cleaned.suffix(2)
            let s2 = "\(y)-\(m)-\(d)"
            guard let d0 = DateUtils.parse(s2) else { return nil }
            return DateUtils.format(DateUtils.mondayOf(d0))
        }
        guard let d0 = DateUtils.parse(cleaned) else { return nil }
        return DateUtils.format(DateUtils.mondayOf(d0))
    }

    static func parseNodeSpan(_ s: String) -> (Int, Int)? {
        let t = s.trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return nil }
        let parts = t.split(separator: "-", maxSplits: 1, omittingEmptySubsequences: false)
        if parts.count == 1, let a = Int(parts[0]) { return (a, a) }
        if parts.count == 2, let a = Int(parts[0]), let b = Int(parts[1]) { return (a, b) }
        return nil
    }

    static func matchesNdPreset(_ timeJson: String) -> Bool {
        let nodes = TimeTableUtils.parseNodes(timeJson)
        guard nodes.count == ndPreset.count else { return false }
        for (i, n) in nodes.enumerated() {
            if n.node != i + 1 { return false }
            if n.start != ndPreset[i].0 || n.end != ndPreset[i].1 { return false }
        }
        return true
    }

    static func crc32(_ data: Data) -> String {
        var crc: UInt32 = 0xFFFF_FFFF
        let table: [UInt32] = {
            var t = [UInt32](repeating: 0, count: 256)
            for i in 0..<256 {
                var c = UInt32(i)
                for _ in 0..<8 {
                    c = (c & 1) == 1 ? (0xEDB8_8320 ^ (c >> 1)) : (c >> 1)
                }
                t[i] = c
            }
            return t
        }()
        for b in data {
            crc = table[Int((crc ^ UInt32(b)) & 0xFF)] ^ (crc >> 8)
        }
        crc ^= 0xFFFF_FFFF
        return String(format: "%08x", crc)
    }

    static func splitRespectingEscape(_ body: String) -> [String] {
        var cols: [String] = []
        var sb = ""
        var i = body.startIndex
        while i < body.endIndex {
            let c = body[i]
            if c == "\\" {
                let n = body.index(after: i)
                if n < body.endIndex {
                    sb.append(c)
                    sb.append(body[n])
                    i = body.index(after: n)
                    continue
                }
            }
            if c == "|" {
                cols.append(sb)
                sb = ""
                i = body.index(after: i)
                continue
            }
            sb.append(c)
            i = body.index(after: i)
        }
        cols.append(sb)
        return cols
    }
}
