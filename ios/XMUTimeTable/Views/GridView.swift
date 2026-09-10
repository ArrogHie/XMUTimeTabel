import SwiftUI

struct GridView: View {
    @EnvironmentObject var app: AppState
    @State private var detail: Course?

    private let days = [1, 2, 3, 4, 5, 6, 7]

    var body: some View {
        GeometryReader { geo in
            let slots = app.currentTable.map { TimeTableUtils.timeSlots(for: $0.timeJson) } ?? []
            let maxNode = max(slots.count, app.currentTable?.nodesPerDay ?? 12, 1)
            let labelW: CGFloat = 44
            let dayW = max(40, (geo.size.width - labelW - 8) / 7)
            let rowH: CGFloat = 42

            ScrollView([.vertical, .horizontal]) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 2) {
                        Color.clear.frame(width: labelW, height: 28)
                        ForEach(days, id: \.self) { d in
                            Text(DateUtils.chineseDay(d).replacingOccurrences(of: "周", with: ""))
                                .font(.caption2)
                                .frame(width: dayW, height: 28)
                                .background(d == DateUtils.dayOfWeek() ? app.themeColor.opacity(0.2) : Color(.secondarySystemBackground))
                        }
                    }
                    ForEach(1...maxNode, id: \.self) { node in
                        HStack(spacing: 2) {
                            VStack(spacing: 0) {
                                Text("\(node)")
                                    .font(.caption2.bold())
                                if let slot = slots.first(where: { $0.node == node }) {
                                    Text(slot.start)
                                        .font(.system(size: 8))
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .frame(width: labelW, height: rowH)
                            .background(Color(.secondarySystemBackground))

                            ForEach(days, id: \.self) { d in
                                let courses = app.coursesOn(day: d).filter { $0.startNode <= node && $0.endNode >= node }
                                ZStack {
                                    Color(.systemBackground)
                                        .overlay(Rectangle().stroke(Color(.separator).opacity(0.3), lineWidth: 0.5))
                                    ForEach(courses) { c in
                                        RoundedRectangle(cornerRadius: 4)
                                            .fill(Color(hex: c.color).opacity(0.85))
                                            .overlay(
                                                VStack(spacing: 1) {
                                                    Text(c.courseName)
                                                        .font(.system(size: 8, weight: .semibold))
                                                        .lineLimit(2)
                                                        .minimumScaleFactor(0.6)
                                                    if c.startNode == node, !c.room.isEmpty {
                                                        Text(c.room)
                                                            .font(.system(size: 7))
                                                            .lineLimit(1)
                                                    }
                                                }
                                                .padding(2)
                                                .foregroundStyle(.primary)
                                            )
                                            .padding(1)
                                            .onTapGesture { detail = c }
                                    }
                                }
                                .frame(width: dayW, height: rowH)
                            }
                        }
                    }
                }
                .padding(4)
            }
        }
        .sheet(item: $detail) { c in
            CourseDetailSheet(course: c, table: app.currentTable)
        }
    }
}
