import SwiftUI

struct ImportExportView: View {
    @EnvironmentObject var app: AppState
    @State private var sleepyText = ""
    @State private var showImporter = false
    @State private var showExporter = false
    @State private var exportText = ""
    @State private var importMode: ImportMode = .append
    @State private var showJW = false
    @State private var showManage = false

    var body: some View {
        NavigationView {
            Form {
                Section("教务导入") {
                    Button {
                        showJW = true
                    } label: {
                        Label("厦门大学教务登录导入", systemImage: "person.badge.key")
                    }
                }
                Section("Sleepy 导入") {
                    Picker("导入方式", selection: $importMode) {
                        Text("追加到当前表").tag(ImportMode.append)
                        Text("替换当前表").tag(ImportMode.replace)
                        Text("新建课表").tag(ImportMode.newTable)
                    }
                    TextEditor(text: $sleepyText)
                        .frame(minHeight: 120)
                        .font(.footnote.monospaced())
                    Button("解析并导入") {
                        importSleepy()
                    }
                    .disabled(sleepyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    Button("从文件导入") { showImporter = true }
                }
                Section("导出") {
                    Button("导出 .sleepy 文本") {
                        exportText = app.exportSleepyFile()
                        showExporter = true
                    }
                    Button("导出 ICS 日历") {
                        exportText = app.exportICS()
                        showExporter = true
                    }
                    Button("分享当前课表") {
                        // handled by share sheet below via exportText
                        exportText = app.exportSleepyFile()
                        showExporter = true
                    }
                }
                Section("课表管理") {
                    Button("管理多课表") { showManage = true }
                }
            }
            .navigationTitle("导入导出")
            .fileImporter(
                isPresented: $showImporter,
                allowedContentTypes: [.plainText, .data, .json, .item],
                allowsMultipleSelection: false
            ) { result in
                if case .success(let urls) = result, let url = urls.first {
                    let access = url.startAccessingSecurityScopedResource()
                    defer { if access { url.stopAccessingSecurityScopedResource() } }
                    if let text = try? String(contentsOf: url, encoding: .utf8) {
                        sleepyText = text
                    }
                }
            }
            .sheet(isPresented: $showExporter) {
                ShareSheet(items: [exportText])
            }
            .sheet(isPresented: $showJW) {
                JWImportView()
            }
            .sheet(isPresented: $showManage) {
                ManageTablesView()
            }
        }
    }

    private func importSleepy() {
        do {
            try app.importSleepy(text: sleepyText, mode: importMode)
            sleepyText = ""
        } catch {
            app.showToast(error.localizedDescription)
        }
    }
}

/// UIKit share sheet wrapper
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
