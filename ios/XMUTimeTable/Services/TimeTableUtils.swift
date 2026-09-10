import Foundation

/// timeJson 解析与节次查询 — 对齐 Android TimeTableUtils
enum TimeTableUtils {
    static let defaultTimeJson = """
    [{"node":1,"start":"08:00","end":"08:45"},{"node":2,"start":"08:55","end":"09:40"},{"node":3,"start":"10:00","end":"10:45"},{"node":4,"start":"10:55","end":"11:40"},{"node":5,"start":"14:00","end":"14:45"},{"node":6,"start":"14:55","end":"15:40"},{"node":7,"start":"16:00","end":"16:45"},{"node":8,"start":"16:55","end":"17:40"},{"node":9,"start":"19:00","end":"19:45"},{"node":10,"start":"19:55","end":"20:40"},{"node":11,"start":"20:50","end":"21:35"},{"node":12,"start":"21:45","end":"22:30"}]
    """

    struct NodeTime: Equatable {
        var node: Int
        var start: String
        var end: String
    }

    static func parseNodes(_ timeJson: String) -> [NodeTime] {
        guard let data = timeJson.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return [] }
        return arr.compactMap { o in
            guard let node = o["node"] as? Int,
                  let start = o["start"] as? String,
                  let end = o["end"] as? String
            else { return nil }
            return NodeTime(node: node, start: start, end: end)
        }
        .sorted { $0.node < $1.node }
    }

    static func timeSlots(for timeJson: String) -> [TimeSlot] {
        parseNodes(timeJson).map { TimeSlot(node: $0.node, start: $0.start, end: $0.end) }
    }

    static func courseTimeString(
        startNode: Int, step: Int, timeJson: String,
        ownTime: Bool = false, startTime: String = "", endTime: String = ""
    ) -> String? {
        if ownTime && !startTime.isEmpty && !endTime.isEmpty {
            return "\(startTime)-\(endTime)"
        }
        let nodes = parseNodes(timeJson)
        let endNode = startNode + max(step, 1) - 1
        guard let first = nodes.first(where: { $0.node == startNode }),
              let last = nodes.first(where: { $0.node == endNode })
        else { return nil }
        return "\(first.start)-\(last.end)"
    }

    /// ownTime 时间反算节次
    static func timeToNode(startTime: String, endTime: String, timeJson: String) -> (Int, Int)? {
        let nodes = parseNodes(timeJson)
        guard !nodes.isEmpty else { return nil }
        guard let st = parseHHmm(startTime), let et = parseHHmm(endTime) else { return nil }
        let startNode = nodes.filter { hhmmToMinutes($0.start) <= st }.map(\.node).max() ?? nodes.first!.node
        let endNode = nodes.filter { hhmmToMinutes($0.end) >= et }.map(\.node).min() ?? nodes.last!.node
        if endNode < startNode { return nil }
        return (startNode, endNode - startNode + 1)
    }

    static func parseHHmm(_ s: String) -> Int? {
        let t = s.replacingOccurrences(of: "：", with: ":")
        let parts = t.split(separator: ":")
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]),
              (0...23).contains(h), (0...59).contains(m)
        else { return nil }
        return h * 60 + m
    }

    static func hhmmToMinutes(_ s: String) -> Int {
        parseHHmm(s) ?? 0
    }

    static func fmtTime(h: Int, m: Int) -> String {
        String(format: "%02d:%02d", h, m)
    }

    /// 合并作息：取两边更完整的节次
    static func mergeMostComplete(current: String, incoming: String, requiredNodeCount: Int = 0) -> String {
        let cur = current.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? [] : parseNodes(current)
        let inc = incoming.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? [] : parseNodes(incoming)
        let curMap = Dictionary(uniqueKeysWithValues: cur.map { ($0.node, $0) })
        let incMap = Dictionary(uniqueKeysWithValues: inc.map { ($0.node, $0) })
        let declared = max(cur.map(\.node).max() ?? 0, inc.map(\.node).max() ?? 0)
        if declared == 0 && requiredNodeCount <= 0 { return defaultTimeJson }
        let count = max(declared, requiredNodeCount, 1)
        var rows: [NodeTime] = []
        for node in 1...count {
            let i = incMap[node], c = curMap[node]
            let start = (i?.start.isEmpty == false ? i!.start : nil)
                ?? (c?.start.isEmpty == false ? c!.start : nil)
                ?? smartStartDefault(node)
            let end = (i?.end.isEmpty == false ? i!.end : nil)
                ?? (c?.end.isEmpty == false ? c!.end : nil)
                ?? smartEndDefault(node)
            rows.append(NodeTime(node: node, start: start, end: end))
        }
        return buildJson(rows)
    }

    static func buildJson(_ rows: [NodeTime]) -> String {
        let parts = rows.map {
            "{\"node\":\($0.node),\"start\":\"\($0.start)\",\"end\":\"\($0.end)\"}"
        }
        return "[\(parts.joined(separator: ","))]"
    }

    private static func smartStartDefault(_ node: Int) -> String {
        switch node {
        case ...2: return "08:00"
        case ...4: return "10:00"
        case ...6: return "14:00"
        case ...8: return "16:00"
        case ...10: return "19:00"
        default: return "20:50"
        }
    }

    private static func smartEndDefault(_ node: Int) -> String {
        switch node {
        case ...2: return "09:40"
        case ...4: return "11:40"
        case ...6: return "15:40"
        case ...8: return "17:40"
        case ...10: return "20:40"
        default: return "22:30"
        }
    }
}
