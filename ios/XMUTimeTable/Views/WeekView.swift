import SwiftUI

struct WeekView: View {
    @EnvironmentObject var app: AppState
    @State private var detail: Course?
    @State private var showAdd = false
    @State private var editing: Course?
    @State private var showEdit = false

    private let days = [1, 2, 3, 4, 5, 6, 7]

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                weekPicker
                modePicker
                if app.viewMode == .week {
                    weekList
                } else if app.viewMode == .grid {
                    GridView()
                } else {
                    TodayView()
                }
            }
            .navigationTitle(app.currentTable?.name ?? "课表")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Menu {
                        ForEach(app.tables) { t in
                            Button {
                                app.select(table: t)
                            } label: {
                                Label(t.name, systemImage: t.id == app.currentTable?.id ? "checkmark" : "tablecells")
                            }
                        }
                        Divider()
                        Button("管理课表") { }
                    } label: {
                        Image(systemName: "tablecells")
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showAdd = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(item: $detail) { c in
                CourseDetailSheet(course: c, table: app.currentTable)
            }
            .sheet(isPresented: $showAdd) {
                CourseEditView(course: Course(
                    courseName: "",
                    day: DateUtils.dayOfWeek(),
                    startNode: 1,
                    step: 2,
                    startWeek: 1,
                    endWeek: app.currentTable?.maxWeek ?? 16
                ), isNew: true)
            }
            .sheet(isPresented: $showEdit) {
                if let c = editing { CourseEditView(course: c) }
            }
        }
    }

    private var weekPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(1...(app.currentTable?.maxWeek ?? 20), id: \.self) { w in
                    WeekChip(title: "第\(w)周", selected: app.selectedWeek == w, accent: app.themeColor)
                        .onTapGesture { app.selectedWeek = w }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
        }
    }

    private var modePicker: some View {
        Picker("模式", selection: $app.viewMode) {
            ForEach(AppState.ViewMode.allCases) { m in
                Text(m.rawValue).tag(m)
            }
        }
        .pickerStyle(.segmented)
        .padding(.horizontal)
        .padding(.bottom, 8)
    }

    private var weekList: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                ForEach(days, id: \.self) { day in
                    let list = app.coursesOn(day: day)
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text(DateUtils.chineseDay(day))
                                .font(.headline)
                            if day == DateUtils.dayOfWeek() {
                                Text("今天")
                                    .font(.caption2)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(app.themeColor.opacity(0.2), in: Capsule())
                                    .foregroundStyle(app.themeColor)
                            }
                            Spacer()
                            Text("\(list.count) 节")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        if list.isEmpty {
                            Text("无课")
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                                .frame(maxWidth: .infinity, alignment: .center)
                                .padding(.vertical, 12)
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
                }
            }
            .padding()
        }
    }
}
