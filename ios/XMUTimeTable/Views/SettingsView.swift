import SwiftUI
import UIKit

struct SettingsView: View {
    @EnvironmentObject var app: AppState
    @State private var showReminder = false
    @State private var showAbout = false
    @State private var showManage = false
    @State private var showJW = false

    var body: some View {
        NavigationView {
            Form {
                Section("课表") {
                    Button {
                        showManage = true
                    } label: {
                        Label("课表管理", systemImage: "tablecells")
                    }
                    Button {
                        showJW = true
                    } label: {
                        Label("教务直连导入", systemImage: "person.badge.key")
                    }
                }
                Section("提醒") {
                    Button {
                        showReminder = true
                    } label: {
                        Label("课程提醒设置", systemImage: "bell.badge")
                    }
                }
                Section("外观") {
                    HStack {
                        Text("主题色")
                        Spacer()
                        Circle()
                            .fill(app.themeColor)
                            .frame(width: 22, height: 22)
                    }
                    if let t = app.currentTable {
                        ColorPicker("课表主题色", selection: Binding(
                            get: { Color(hex: t.color) },
                            set: { newColor in
                                var table = t
                                table.color = newColor.toHex()
                                DatabaseService.shared.updateTable(table)
                                app.reloadTables()
                            }
                        ))
                    }
                }
                Section("关于") {
                    Button {
                        showAbout = true
                    } label: {
                        Label("关于厦大课表 iOS", systemImage: "info.circle")
                    }
                    HStack { Text("版本"); Spacer(); Text("1.0.0") }
                    HStack { Text("分发"); Spacer(); Text("越狱 deb / TrollStore ipa") }
                }
            }
            .navigationTitle("我的")
            .sheet(isPresented: $showReminder) { ReminderView() }
            .sheet(isPresented: $showAbout) { AboutView() }
            .sheet(isPresented: $showManage) { ManageTablesView() }
            .sheet(isPresented: $showJW) { JWImportView() }
        }
    }
}

extension Color {
    func toHex() -> String {
        let ui = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "#FF%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255))
    }
}
