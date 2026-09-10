import SwiftUI

struct TodayView: View {
    @EnvironmentObject var app: AppState
    @State private var detail: Course?
    @State private var showEdit = false
    @State private var editing: Course?

    var body: some View {
        NavigationView {
            ScrollView {
                LazyVStack(spacing: 12) {
                    header
                    let list = app.todayCourses()
                    if list.isEmpty {
                        EmptyStateView(
                            icon: "calendar.badge.clock",
                            title: "今天没有课",
                            subtitle: "去课表页查看本周安排，或从教务导入课表"
                        )
                    } else {
                        ForEach(list) { c in
                            CourseRow(course: c, table: app.currentTable)
                                .onTapGesture { detail = c }
                                .contextMenu {
                                    Button("编辑") { editing = c; showEdit = true }
                                    Button("删除本组", role: .destructive) { app.deleteGroup(c) }
                                }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("今日")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        app.reloadTables()
                    } label: { Image(systemName: "arrow.clockwise") }
                }
            }
            .sheet(item: $detail) { c in
                CourseDetailSheet(course: c, table: app.currentTable)
            }
            .sheet(isPresented: $showEdit) {
                if let c = editing {
                    CourseEditView(course: c)
                }
            }
            .refreshable { app.reloadTables() }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(DateUtils.format(Date()) + " " + DateUtils.chineseDay(DateUtils.dayOfWeek()))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if let t = app.currentTable {
                Text(t.name)
                    .font(.title2.bold())
                Text("第 \(DateUtils.currentWeek(startDate: t.startDate)) 周 · 共 \(t.maxWeek) 周")
                    .font(.footnote)
                    .foregroundStyle(app.themeColor)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: AppTheme.corner))
    }
}

struct CourseRow: View {
    let course: Course
    let table: TimeTable?

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color(hex: course.color))
                .frame(width: 6)
                .opacity(0.9)
            VStack(alignment: .leading, spacing: 4) {
                Text(course.courseName)
                    .font(.headline)
                    .lineLimit(2)
                HStack(spacing: 8) {
                    Text(course.nodeRangeText())
                    if !course.room.isEmpty { Text(course.room) }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                if !course.teacher.isEmpty {
                    Text(course.teacher)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                if let t = table,
                   let time = TimeTableUtils.courseTimeString(
                    startNode: course.startNode, step: course.step, timeJson: t.timeJson,
                    ownTime: course.ownTime, startTime: course.startTime, endTime: course.endTime
                   ) {
                    Text(time)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
                weekTag
            }
        }
        .padding()
        .background(Color(hex: course.color).opacity(0.18), in: RoundedRectangle(cornerRadius: AppTheme.corner))
    }

    private var weekTag: some View {
        let text: String
        switch course.type {
        case 1: text = "单周"
        case 2: text = "双周"
        case 3: text = course.startWeek == course.endWeek ? "第\(course.startWeek)周" : "\(course.startWeek)-\(course.endWeek)周"
        default: text = "\(course.startWeek)-\(course.endWeek)周"
        }
        return Text(text)
            .font(.caption2)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Color(.tertiarySystemFill), in: Capsule())
            .foregroundStyle(.secondary)
    }
}
