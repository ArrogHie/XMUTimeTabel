import SwiftUI

struct ManageTablesView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var newName = ""

    var body: some View {
        NavigationView {
            Form {
                Section("当前课表") {
                    ForEach(app.tables) { t in
                        Button {
                            app.select(table: t)
                        } label: {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(t.name).foregroundStyle(.primary)
                                    Text("\(t.startDate) · \(t.maxWeek) 周 · \(t.nodesPerDay) 节")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                if t.id == app.currentTable?.id {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(app.themeColor)
                                }
                            }
                        }
                    }
                    .onDelete { indexSet in
                        // only allow deleting non-last
                        for i in indexSet {
                            let t = app.tables[i]
                            if app.tables.count > 1 {
                                DatabaseService.shared.deleteTable(id: t.id)
                            }
                        }
                        app.reloadTables()
                    }
                }
                Section("新建课表") {
                    HStack {
                        TextField("课表名称", text: $newName)
                        Button("添加") {
                            app.addTable(name: newName)
                            newName = ""
                        }
                        .disabled(newName.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
                if var t = app.currentTable {
                    Section("编辑当前表") {
                        TextField("名称", text: Binding(
                            get: { t.name },
                            set: { t.name = $0 }
                        ))
                        Stepper("最大周：\(t.maxWeek)", value: Binding(
                            get: { t.maxWeek },
                            set: { t.maxWeek = $0 }
                        ), in: 1...30)
                        Stepper("每天节数：\(t.nodesPerDay)", value: Binding(
                            get: { t.nodesPerDay },
                            set: { t.nodesPerDay = $0 }
                        ), in: 1...20)
                        DatePicker(
                            "开学周一",
                            selection: Binding(
                                get: { DateUtils.parse(t.startDate) ?? Date() },
                                set: { t.startDate = DateUtils.format(DateUtils.mondayOf($0)) }
                            ),
                            displayedComponents: .date
                        )
                        Button("保存修改") {
                            DatabaseService.shared.updateTable(t)
                            app.reloadTables()
                            app.showToast("课表已更新")
                        }
                    }
                }
            }
            .navigationTitle("课表管理")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }
}
