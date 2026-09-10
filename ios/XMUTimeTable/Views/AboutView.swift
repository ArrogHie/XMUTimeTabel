import SwiftUI

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            List {
                Section {
                    VStack(spacing: 8) {
                        Image(systemName: "calendar")
                            .font(.system(size: 48))
                            .foregroundStyle(Color(hex: "#FF6750A4"))
                        Text("厦大课表")
                            .font(.title2.bold())
                        Text("iOS 越狱 / 免签分发版")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                }
                Section("说明") {
                    Text("本项目基于开源项目 Sleepy / 厦大课表 Android 版移植，遵循 GPL-3.0。")
                    Text("面向有能力越狱或使用 TrollStore 的用户，通过 deb / ipa 分发，不依赖 Apple 签名与 App Store。")
                }
                Section("功能") {
                    Text("· 多课表 · 周/网格/今日视图")
                    Text("· .sleepy 导入导出 · ICS 导出")
                    Text("· 厦大教务自动登录")
                    Text("· 本地课程提醒")
                }
                Section("开源") {
                    Text("上游：github.com/lingion/sleepy")
                        .font(.footnote)
                }
            }
            .navigationTitle("关于")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
    }
}
