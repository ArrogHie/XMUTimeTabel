import SwiftUI

struct CourseEditView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss

    @State var course: Course
    var isNew = false

    private let palette = SleepyFormat.palette

    var body: some View {
        NavigationView {
            Form {
                Section("基本信息") {
                    TextField("课程名称", text: $course.courseName)
                    TextField("教师", text: $course.teacher)
                    TextField("教室", text: $course.room)
                    TextField("备注", text: $course.note)
                }
                Section("时间") {
                    Picker("星期", selection: $course.day) {
                        ForEach(1...7, id: \.self) { d in
                            Text(DateUtils.chineseDay(d)).tag(d)
                        }
                    }
                    Stepper("开始节次：\(course.startNode)", value: $course.startNode, in: 1...30)
                    Stepper("持续节数：\(course.step)", value: $course.step, in: 1...12)
                    Stepper("起始周：\(course.startWeek)", value: $course.startWeek, in: 1...30)
                    Stepper("结束周：\(course.endWeek)", value: $course.endWeek, in: course.startWeek...60)
                    Picker("周次类型", selection: $course.type) {
                        Text("每周").tag(0)
                        Text("单周").tag(1)
                        Text("双周").tag(2)
                        Text("指定区间").tag(3)
                    }
                }
                Section("颜色") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(palette.keys.sorted(), id: \.self) { idx in
                                Circle()
                                    .fill(Color(hex: palette[idx]!))
                                    .frame(width: 32, height: 32)
                                    .overlay(
                                        Circle().stroke(course.color.uppercased() == palette[idx]!.uppercased() ? Color.primary : Color.clear, lineWidth: 2)
                                    )
                                    .onTapGesture { course.color = palette[idx]! }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .navigationTitle(isNew ? "添加课程" : "编辑课程")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") { save() }
                        .disabled(course.courseName.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private func save() {
        course.courseName = course.courseName.trimmingCharacters(in: .whitespaces)
        course.endWeek = max(course.endWeek, course.startWeek)
        if isNew {
            app.addCourse(course)
        } else {
            app.updateCourse(course)
        }
        dismiss()
    }
}
