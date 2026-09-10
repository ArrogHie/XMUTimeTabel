import SwiftUI

struct ContentView: View {
    @EnvironmentObject var app: AppState

    var body: some View {
        TabView {
            TodayView()
                .tabItem { Label("今日", systemImage: "sun.max") }
            WeekView()
                .tabItem { Label("课表", systemImage: "calendar") }
            ImportExportView()
                .tabItem { Label("导入", systemImage: "square.and.arrow.down") }
            SettingsView()
                .tabItem { Label("我的", systemImage: "person.crop.circle") }
        }
        .tint(app.themeColor)
        .overlay(alignment: .top) {
            if let toast = app.toast {
                Text(toast)
                    .font(.footnote)
                    .padding(10)
                    .background(.ultraThinMaterial, in: Capsule())
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.easeInOut, value: app.toast)
    }
}
