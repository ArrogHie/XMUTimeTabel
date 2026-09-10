import SwiftUI

enum AppTheme {
    static let corner: CGFloat = 12
}

struct CourseColorView: View {
    let hex: String
    let bodyColor: Color

    init(hex: String) {
        self.hex = hex
        let c = Color(hex: hex)
        self.bodyColor = c.opacity(0.22)
    }

    var body: some View {
        Color(hex: hex).opacity(0.25)
    }
}

struct WeekChip: View {
    let title: String
    let selected: Bool
    let accent: Color
    var body: some View {
        Text(title)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(selected ? accent : Color(.secondarySystemBackground), in: Capsule())
            .foregroundStyle(selected ? .white : .primary)
    }
}

struct EmptyStateView: View {
    let icon: String
    let title: String
    let subtitle: String
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 42))
                .foregroundStyle(.secondary)
            Text(title).font(.headline)
            Text(subtitle)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }
}
