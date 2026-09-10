import SwiftUI

struct CourseDetailSheet: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    let course: Course
    let table: TimeTable?
    @State private var showEdit = false

    var body: some View {
        NavigationView {
            List {
                Section("课程") {
                    HStack { Text("名称"); Spacer(); Text(course.courseName) }
                    if !course.teacher.isEmpty { HStack { Text("教师"); Spacer(); Text(course.teacher) } }
                    if !course.room.isEmpty { HStack { Text("教室"); Spacer(); Text(course.room) } }
                    if !course.note.isEmpty { HStack { Text("备注"); Spacer(); Text(course.note) } }
                }
                Section("时间") {
                    HStack { Text("星期"); Spacer(); Text(DateUtils.chineseDay(course.day)) }
                    HStack { Text("节次"); Spacer(); Text(course.nodeRangeText()) }
                    if let t = table,
                       let time = TimeTableUtils.courseTimeString(
                        startNode: course.startNode, step: course.step, timeJson: t.timeJson,
                        ownTime: course.ownTime, startTime: course.startTime, endTime: course.endTime
                       ) {
                        HStack { Text("时刻"); Spacer(); Text(time) }
                    }
                    HStack { Text("周次"); Spacer(); Text(weekText) }
                    if course.type == 1 { Text("单周上课").foregroundStyle(.secondary) }
                    if course.type == 2 { Text("双周上课").foregroundStyle(.secondary) }
                }
                Section {
                    Button("编辑课程") { showEdit = true }
                    Button("删除本组课程", role: .destructive) {
                        app.deleteGroup(course)
                        dismiss()
                    }
                }
            }
            .navigationTitle(course.courseName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("关闭") { dismiss() }
                }
            }
            .sheet(isPresented: $showEdit) {
                CourseEditView(course: course)
            }
        }
    }

    private var weekText: String {
        "\(course.startWeek)-\(course.endWeek) 周"
    }
}
