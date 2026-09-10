import WidgetKit
import SwiftUI

struct XmuWidgetEntry: TimelineEntry {
    var date: Date
    var snapshot: WidgetSnapshot
    var kind: XmuWidgetKind
}

enum XmuWidgetKind: String {
    case today = "xmu.today"
    case twoDay = "xmu.twoday"
    case weekGrid = "xmu.weekgrid"
}

struct XmuWidgetProvider: TimelineProvider {
    let kind: XmuWidgetKind

    func placeholder(in context: Context) -> XmuWidgetEntry {
        XmuWidgetEntry(date: Date(), snapshot: .empty, kind: kind)
    }

    func getSnapshot(in context: Context, completion: @escaping (XmuWidgetEntry) -> Void) {
        let snap = WidgetStore.load()
        completion(XmuWidgetEntry(date: Date(), snapshot: snap, kind: kind))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<XmuWidgetEntry>) -> Void) {
        let snap = WidgetStore.load()
        let now = Date()
        let cal = Calendar.current
        var entries: [XmuWidgetEntry] = [XmuWidgetEntry(date: now, snapshot: snap, kind: kind)]

        // refresh every 30 min; also after next class if known
        var next = now.addingTimeInterval(30 * 60)
        if !snap.startDate.isEmpty {
            let week = WidgetDateHelper.currentWeek(startDate: snap.startDate)
            let day = WidgetDateHelper.dayOfWeek(now)
            let today = WidgetStore.courses(on: day, week: week, from: snap)
            if let nextCourse = today.first(where: { c in
                guard let slot = snap.timeSlots.first(where: { $0.node == c.startNode }) else { return false }
                let mins = (Int(slot.start.prefix(2)) ?? 0) * 60 + (Int(slot.start.suffix(2)) ?? 0)
                let nowMins = cal.component(.hour, from: now) * 60 + cal.component(.minute, from: now)
                return mins > nowMins
            }),
               let slot = snap.timeSlots.first(where: { $0.node == nextCourse.startNode }) {
                let h = Int(slot.start.prefix(2)) ?? 12
                let m = Int(slot.start.suffix(2)) ?? 0
                if let t = cal.date(bySettingHour: h, minute: m, second: 0, of: now), t > now {
                    next = min(next, t.addingTimeInterval(60))
                }
            }
        }
        entries.append(XmuWidgetEntry(date: next, snapshot: snap, kind: kind))
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

// MARK: - Shared chrome

struct WidgetHeader: View {
    let title: String
    let subtitle: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Text(subtitle)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 2)
    }
}

struct CourseBlock: View {
    let course: WidgetCourse
    let slots: [WidgetSlot]
    var compact = false

    var body: some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 3)
                .fill(Color(hexWidget: course.color))
                .frame(width: 4)
            VStack(alignment: .leading, spacing: 1) {
                Text(course.name)
                    .font(compact ? .caption2.weight(.semibold) : .caption.weight(.semibold))
                    .lineLimit(1)
                Text(timeText + (course.room.isEmpty ? "" : " · \(course.room)"))
                    .font(.system(size: 9))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, compact ? 2 : 3)
        .padding(.horizontal, 6)
        .background(Color(hexWidget: course.color).opacity(0.18), in: RoundedRectangle(cornerRadius: 6))
    }

    private var timeText: String {
        WidgetDateHelper.courseTime(startNode: course.startNode, endNode: course.endNode, slots: slots)
    }
}

extension Color {
    init(hexWidget: String) {
        var s = hexWidget.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        if s.count == 6 { s = "FF" + s }
        var value: UInt64 = 0
        Scanner(string: s).scanHexInt64(&value)
        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255.0,
            green: Double((value >> 8) & 0xFF) / 255.0,
            blue: Double(value & 0xFF) / 255.0,
            opacity: Double((value >> 24) & 0xFF) / 255.0
        )
    }
}

// MARK: - Today

struct TodayWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: XmuWidgetEntry

    var body: some View {
        let snap = entry.snapshot
        let week = snap.startDate.isEmpty ? 1 : WidgetDateHelper.currentWeek(startDate: snap.startDate)
        let day = WidgetDateHelper.dayOfWeek(entry.date)
        let list = WidgetStore.courses(on: day, week: week, from: snap)
        let maxCount = family == .systemSmall ? 2 : 4

        VStack(alignment: .leading, spacing: 6) {
            WidgetHeader(
                title: "今日 · \(WidgetDateHelper.chineseDay(day))",
                subtitle: snap.tableName.isEmpty ? "第 \(week) 周" : "\(snap.tableName) · 第 \(week) 周"
            )
            if list.isEmpty {
                Spacer(minLength: 0)
                Text("今天没有课")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                Spacer(minLength: 0)
            } else {
                ForEach(list.prefix(maxCount)) { c in
                    CourseBlock(course: c, slots: snap.timeSlots, compact: family == .systemSmall)
                }
                if list.count > maxCount {
                    Text("还有 \(list.count - maxCount) 节…")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
            }
        }
        .padding(10)
        .background(Color(.systemBackground))
    }
}

struct TodayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: XmuWidgetKind.today.rawValue, provider: XmuWidgetProvider(kind: .today)) { entry in
            TodayWidgetView(entry: entry)
        }
        .configurationDisplayName("今日课程")
        .description("显示今天的课程安排")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Two day

struct TwoDayWidgetView: View {
    let entry: XmuWidgetEntry

    var body: some View {
        let snap = entry.snapshot
        let week = snap.startDate.isEmpty ? 1 : WidgetDateHelper.currentWeek(startDate: snap.startDate)
        let day = WidgetDateHelper.dayOfWeek(entry.date)
        let today = WidgetStore.courses(on: day, week: week, from: snap)
        let tomorrowDay = day == 7 ? 1 : day + 1
        let tomorrowWeek = day == 7 ? week + 1 : week
        let tomorrow = WidgetStore.courses(on: tomorrowDay, week: tomorrowWeek, from: snap)

        HStack(alignment: .top, spacing: 10) {
            dayColumn(
                title: "今天 \(WidgetDateHelper.chineseDay(day))",
                list: today,
                slots: snap.timeSlots
            )
            Divider()
            dayColumn(
                title: "明天 \(WidgetDateHelper.chineseDay(tomorrowDay))",
                list: tomorrow,
                slots: snap.timeSlots
            )
        }
        .padding(10)
        .background(Color(.systemBackground))
    }

    private func dayColumn(title: String, list: [WidgetCourse], slots: [WidgetSlot]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            if list.isEmpty {
                Text("无课")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.top, 8)
            } else {
                ForEach(list.prefix(3)) { c in
                    CourseBlock(course: c, slots: slots, compact: true)
                }
                if list.count > 3 {
                    Text("+\(list.count - 3)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct TwoDayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: XmuWidgetKind.twoDay.rawValue, provider: XmuWidgetProvider(kind: .twoDay)) { entry in
            TwoDayWidgetView(entry: entry)
        }
        .configurationDisplayName("最近两天")
        .description("今天与明天的课程对照")
        .supportedFamilies([.systemMedium])
    }
}

// MARK: - Week grid

struct WeekGridWidgetView: View {
    let entry: XmuWidgetEntry
    private let days = [1, 2, 3, 4, 5, 6, 7]

    var body: some View {
        let snap = entry.snapshot
        let week = snap.startDate.isEmpty ? 1 : WidgetDateHelper.currentWeek(startDate: snap.startDate)
        let today = WidgetDateHelper.dayOfWeek(entry.date)
        let maxNode = min(max(snap.nodesPerDay, snap.timeSlots.count, 10), 12)

        VStack(alignment: .leading, spacing: 4) {
            WidgetHeader(title: "本周 · 第 \(week) 周", subtitle: snap.tableName)
            HStack(spacing: 2) {
                Color.clear.frame(width: 14)
                ForEach(days, id: \.self) { d in
                    Text(String(WidgetDateHelper.chineseDay(d).suffix(1)))
                        .font(.system(size: 8, weight: .semibold))
                        .foregroundStyle(d == today ? Color.primary : Color.secondary)
                        .frame(maxWidth: .infinity)
                }
            }
            ForEach(1...maxNode, id: \.self) { node in
                HStack(spacing: 2) {
                    Text("\(node)")
                        .font(.system(size: 7))
                        .foregroundStyle(.secondary)
                        .frame(width: 14, alignment: .leading)
                    ForEach(days, id: \.self) { d in
                        let cell = WidgetStore.courses(on: d, week: week, from: snap)
                            .first { $0.startNode <= node && $0.endNode >= node }
                        ZStack {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(cell.map { Color(hexWidget: $0.color).opacity(0.85) } ?? Color(.secondarySystemBackground))
                            if let cell, cell.startNode == node {
                                Text(cell.name)
                                    .font(.system(size: 6, weight: .bold))
                                    .lineLimit(2)
                                    .minimumScaleFactor(0.5)
                                    .padding(1)
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 10)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(10)
        .background(Color(.systemBackground))
    }
}

struct WeekGridWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: XmuWidgetKind.weekGrid.rawValue, provider: XmuWidgetProvider(kind: .weekGrid)) { entry in
            WeekGridWidgetView(entry: entry)
        }
        .configurationDisplayName("周课表")
        .description("本周课程网格一览")
        .supportedFamilies([.systemLarge])
    }
}

@main
struct XMUTimeTableWidgetBundle: WidgetBundle {
    var body: some Widget {
        TodayWidget()
        TwoDayWidget()
        WeekGridWidget()
    }
}
