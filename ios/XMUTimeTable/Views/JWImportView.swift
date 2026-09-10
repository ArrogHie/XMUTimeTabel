import SwiftUI

struct JWImportView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var account = ""
    @State private var password = ""
    @State private var loading = false
    @State private var errorText: String?
    @State private var previewCourses: [Course] = []
    @State private var previewTimeJson = ""
    @State private var previewMaxWeek = 20
    @State private var semester = ""
    @State private var showPreview = false

    var body: some View {
        NavigationView {
            Form {
                Section("账号") {
                    TextField("学号", text: $account)
                        .keyboardType(.asciiCapable)
                        .textContentType(.username)
                    SecureField("密码", text: $password)
                        .textContentType(.password)
                }
                Section {
                    Button {
                        login()
                    } label: {
                        if loading {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Text("登录并抓取课表")
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(account.isEmpty || password.isEmpty || loading)
                }
                if let errorText {
                    Section("错误") {
                        Text(errorText)
                            .foregroundStyle(.red)
                            .font(.footnote)
                    }
                }
                Section("说明") {
                    Text("登录信息仅用于向厦门大学教务系统拉取课表，密码不会写入本地磁盘。厦大统一身份认证走账号密码自动登录，无需验证码。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("教务导入")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .sheet(isPresented: $showPreview) {
                NavigationView {
                    List {
                        Section("学期 \(semester)") {
                            HStack { Text("课程数"); Spacer(); Text("\(previewCourses.count)") }
                            HStack { Text("最大周"); Spacer(); Text("\(previewMaxWeek)") }
                        }
                        Section("课程预览") {
                            ForEach(previewCourses.prefix(30)) { c in
                                VStack(alignment: .leading) {
                                    Text(c.courseName).font(.headline)
                                    Text("\(DateUtils.chineseDay(c.day)) \(c.nodeRangeText()) \(c.room)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        Section {
                            Button("导入为新课表") { importAsNew() }
                            Button("追加到当前课表") { importAppend() }
                        }
                    }
                    .navigationTitle("确认导入")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("返回") { showPreview = false }
                        }
                    }
                }
            }
        }
    }

    private func login() {
        loading = true
        errorText = nil
        let client = XmuAutoLoginClient(account: account, password: password)
        client.fetchSchedule { result in
            loading = false
            switch result {
            case .failure(let err):
                errorText = err.localizedDescription
            case .success(let fetch):
                guard let parsed = XmuJw.parseCourseJson(
                    fetch.courseJson,
                    tableId: app.currentTable?.id ?? 0,
                    timeJson: app.currentTable?.timeJson ?? TimeTableUtils.defaultTimeJson,
                    maxWeek: app.currentTable?.maxWeek ?? 20
                ) else {
                    errorText = "课表接口返回无法解析"
                    return
                }
                previewCourses = parsed.courses
                previewMaxWeek = parsed.maxWeek
                semester = fetch.semesterCode
                var tj = parsed.timeJson
                if fetch.periods.isEmpty == false {
                    let nodes = fetch.periods.map {
                        TimeTableUtils.NodeTime(node: $0.0, start: $0.1, end: $0.2)
                    }
                    tj = TimeTableUtils.buildJson(nodes)
                } else {
                    // xmu fallback periods
                    let nodes = XmuJw.fallbackPeriod.map {
                        TimeTableUtils.NodeTime(node: $0.0, start: $0.1, end: $0.2)
                    }
                    tj = TimeTableUtils.buildJson(nodes)
                }
                previewTimeJson = tj
                showPreview = true
                password = ""
            }
        }
    }

    private func importAsNew() {
        let id = DatabaseService.shared.createTable(TimeTable(
            name: "厦大 \(semester)",
            startDate: DateUtils.todayMondayString(),
            maxWeek: max(previewMaxWeek, 16),
            nodesPerDay: TimeTableUtils.parseNodes(previewTimeJson).count,
            timeJson: previewTimeJson
        ))
        for c in previewCourses {
            var cc = c
            cc.tableId = id
            DatabaseService.shared.insertCourse(cc)
        }
        app.reloadTables()
        if let t = DatabaseService.shared.table(id: id) {
            app.select(table: t)
        }
        showPreview = false
        app.showToast("已导入 \(previewCourses.count) 节课")
        dismiss()
    }

    private func importAppend() {
        guard var t = app.currentTable else { return }
        let reach = previewCourses.map { $0.startNode + $0.step - 1 }.max() ?? 0
        t.timeJson = TimeTableUtils.mergeMostComplete(current: t.timeJson, incoming: previewTimeJson, requiredNodeCount: reach)
        t.maxWeek = max(t.maxWeek, previewMaxWeek)
        DatabaseService.shared.updateTable(t)
        for c in previewCourses {
            var cc = c
            cc.tableId = t.id
            DatabaseService.shared.insertCourse(cc)
        }
        app.reloadTables()
        showPreview = false
        app.showToast("已追加 \(previewCourses.count) 节课")
        dismiss()
    }
}
