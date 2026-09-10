import SwiftUI

@main
struct XMUTimeTableApp: App {
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .onAppear {
                    NotificationService.shared.requestAuthorization { _ in
                        appState.rescheduleNotifications()
                    }
                }
        }
    }
}
