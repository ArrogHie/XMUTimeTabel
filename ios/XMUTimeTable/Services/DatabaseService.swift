import Foundation
import SQLite3

enum AppError: Error, LocalizedError {
    case message(String)
    var errorDescription: String? {
        switch self {
        case .message(let m): return m
        }
    }
}

/// SQLite 本地库 — 对齐 Android Room schema
final class DatabaseService {
    static let shared = DatabaseService()

    private var db: OpaquePointer?
    private let queue = DispatchQueue(label: "xmu.sqlite")

    private init() {
        open()
        migrate()
    }

    private func documentsURL() -> URL {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        return paths[0]
    }

    private var dbPath: String {
        documentsURL().appendingPathComponent("xmutimetable.sqlite").path
    }

    private func open() {
        if sqlite3_open(dbPath, &db) != SQLITE_OK {
            print("sqlite open failed: \(String(cString: sqlite3_errmsg(db)))")
        }
        sqlite3_exec(db, "PRAGMA foreign_keys = ON;", nil, nil, nil)
    }

    private func migrate() {
        exec("""
        CREATE TABLE IF NOT EXISTS time_tables (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          name TEXT NOT NULL,
          startDate TEXT NOT NULL,
          maxWeek INTEGER NOT NULL DEFAULT 20,
          nodesPerDay INTEGER NOT NULL DEFAULT 12,
          timeJson TEXT NOT NULL,
          color TEXT NOT NULL DEFAULT '#FF6750A4',
          isDefault INTEGER NOT NULL DEFAULT 0,
          smartConfigJson TEXT NOT NULL DEFAULT '',
          createdAt INTEGER NOT NULL
        );
        """)
        exec("""
        CREATE TABLE IF NOT EXISTS courses (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          groupId TEXT NOT NULL,
          tableId INTEGER NOT NULL,
          courseName TEXT NOT NULL,
          teacher TEXT NOT NULL DEFAULT '',
          room TEXT NOT NULL DEFAULT '',
          note TEXT NOT NULL DEFAULT '',
          day INTEGER NOT NULL,
          startNode INTEGER NOT NULL,
          step INTEGER NOT NULL,
          startWeek INTEGER NOT NULL,
          endWeek INTEGER NOT NULL,
          type INTEGER NOT NULL DEFAULT 0,
          color TEXT NOT NULL,
          ownTime INTEGER NOT NULL DEFAULT 0,
          startTime TEXT NOT NULL DEFAULT '',
          endTime TEXT NOT NULL DEFAULT '',
          credit REAL NOT NULL DEFAULT 0,
          level INTEGER NOT NULL DEFAULT 0,
          FOREIGN KEY(tableId) REFERENCES time_tables(id) ON DELETE CASCADE
        );
        """)
        exec("CREATE INDEX IF NOT EXISTS idx_courses_table ON courses(tableId);")
        exec("CREATE INDEX IF NOT EXISTS idx_courses_day ON courses(day);")

        // seed default table if empty
        if listTables().isEmpty {
            _ = createTable(TimeTable(
                name: "我的课表",
                startDate: DateUtils.todayMondayString(),
                maxWeek: 20,
                nodesPerDay: 12,
                timeJson: TimeTableUtils.defaultTimeJson,
                isDefault: true
            ))
        }
    }

    private func exec(_ sql: String) {
        var err: UnsafeMutablePointer<CChar>?
        sqlite3_exec(db, sql, nil, nil, &err)
        if let err {
            print("sqlite error: \(String(cString: err))")
            sqlite3_free(err)
        }
    }

    // MARK: - Tables

    func listTables() -> [TimeTable] {
        queue.sync {
            var result: [TimeTable] = []
            var stmt: OpaquePointer?
            let sql = "SELECT id,name,startDate,maxWeek,nodesPerDay,timeJson,color,isDefault,smartConfigJson,createdAt FROM time_tables ORDER BY isDefault DESC, id ASC;"
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                while sqlite3_step(stmt) == SQLITE_ROW {
                    result.append(readTable(stmt))
                }
            }
            sqlite3_finalize(stmt)
            return result
        }
    }

    func defaultTable() -> TimeTable? {
        listTables().first(where: { $0.isDefault }) ?? listTables().first
    }

    func table(id: Int64) -> TimeTable? {
        listTables().first(where: { $0.id == id })
    }

    @discardableResult
    func createTable(_ t: TimeTable) -> Int64 {
        queue.sync {
            var stmt: OpaquePointer?
            let sql = """
            INSERT INTO time_tables(name,startDate,maxWeek,nodesPerDay,timeJson,color,isDefault,smartConfigJson,createdAt)
            VALUES(?,?,?,?,?,?,?,?,?);
            """
            sqlite3_prepare_v2(db, sql, -1, &stmt, nil)
            bindText(stmt, 1, t.name)
            bindText(stmt, 2, t.startDate)
            sqlite3_bind_int64(stmt, 3, Int64(t.maxWeek))
            sqlite3_bind_int64(stmt, 4, Int64(t.nodesPerDay))
            bindText(stmt, 5, t.timeJson)
            bindText(stmt, 6, t.color)
            sqlite3_bind_int(stmt, 7, t.isDefault ? 1 : 0)
            bindText(stmt, 8, t.smartConfigJson)
            sqlite3_bind_int64(stmt, 9, t.createdAt)
            sqlite3_step(stmt)
            sqlite3_finalize(stmt)
            return sqlite3_last_insert_rowid(db)
        }
    }

    func updateTable(_ t: TimeTable) {
        queue.sync {
            var stmt: OpaquePointer?
            let sql = """
            UPDATE time_tables SET name=?, startDate=?, maxWeek=?, nodesPerDay=?, timeJson=?, color=?, isDefault=?, smartConfigJson=?
            WHERE id=?;
            """
            sqlite3_prepare_v2(db, sql, -1, &stmt, nil)
            bindText(stmt, 1, t.name)
            bindText(stmt, 2, t.startDate)
            sqlite3_bind_int64(stmt, 3, Int64(t.maxWeek))
            sqlite3_bind_int64(stmt, 4, Int64(t.nodesPerDay))
            bindText(stmt, 5, t.timeJson)
            bindText(stmt, 6, t.color)
            sqlite3_bind_int(stmt, 7, t.isDefault ? 1 : 0)
            bindText(stmt, 8, t.smartConfigJson)
            sqlite3_bind_int64(stmt, 9, t.id)
            sqlite3_step(stmt)
            sqlite3_finalize(stmt)
            if t.isDefault {
                exec("UPDATE time_tables SET isDefault=0 WHERE id != \(t.id);")
            }
        }
    }

    func deleteTable(id: Int64) {
        queue.sync {
            exec("DELETE FROM courses WHERE tableId=\(id);")
            exec("DELETE FROM time_tables WHERE id=\(id);")
        }
    }

    // MARK: - Courses

    func courses(tableId: Int64) -> [Course] {
        queue.sync {
            var result: [Course] = []
            var stmt: OpaquePointer?
            let sql = """
            SELECT id,groupId,tableId,courseName,teacher,room,note,day,startNode,step,startWeek,endWeek,type,color,ownTime,startTime,endTime,credit,level
            FROM courses WHERE tableId=? ORDER BY day,startNode;
            """
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                sqlite3_bind_int64(stmt, 1, tableId)
                while sqlite3_step(stmt) == SQLITE_ROW {
                    result.append(readCourse(stmt))
                }
            }
            sqlite3_finalize(stmt)
            return result
        }
    }

    func allCourses() -> [Course] {
        queue.sync {
            var result: [Course] = []
            var stmt: OpaquePointer?
            let sql = """
            SELECT id,groupId,tableId,courseName,teacher,room,note,day,startNode,step,startWeek,endWeek,type,color,ownTime,startTime,endTime,credit,level
            FROM courses ORDER BY tableId,day,startNode;
            """
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                while sqlite3_step(stmt) == SQLITE_ROW {
                    result.append(readCourse(stmt))
                }
            }
            sqlite3_finalize(stmt)
            return result
        }
    }

    @discardableResult
    func insertCourse(_ c: Course) -> Int64 {
        queue.sync {
            var stmt: OpaquePointer?
            let sql = """
            INSERT INTO courses(groupId,tableId,courseName,teacher,room,note,day,startNode,step,startWeek,endWeek,type,color,ownTime,startTime,endTime,credit,level)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);
            """
            sqlite3_prepare_v2(db, sql, -1, &stmt, nil)
            bindText(stmt, 1, c.groupId.isEmpty ? UUID().uuidString : c.groupId)
            sqlite3_bind_int64(stmt, 2, c.tableId)
            bindText(stmt, 3, c.courseName)
            bindText(stmt, 4, c.teacher)
            bindText(stmt, 5, c.room)
            bindText(stmt, 6, c.note)
            sqlite3_bind_int64(stmt, 7, Int64(c.day))
            sqlite3_bind_int64(stmt, 8, Int64(c.startNode))
            sqlite3_bind_int64(stmt, 9, Int64(c.step))
            sqlite3_bind_int64(stmt, 10, Int64(c.startWeek))
            sqlite3_bind_int64(stmt, 11, Int64(c.endWeek))
            sqlite3_bind_int64(stmt, 12, Int64(c.type))
            bindText(stmt, 13, c.color)
            sqlite3_bind_int(stmt, 14, c.ownTime ? 1 : 0)
            bindText(stmt, 15, c.startTime)
            bindText(stmt, 16, c.endTime)
            sqlite3_bind_double(stmt, 17, Double(c.credit))
            sqlite3_bind_int64(stmt, 18, Int64(c.level))
            sqlite3_step(stmt)
            sqlite3_finalize(stmt)
            return sqlite3_last_insert_rowid(db)
        }
    }

    func updateCourse(_ c: Course) {
        queue.sync {
            var stmt: OpaquePointer?
            let sql = """
            UPDATE courses SET groupId=?, courseName=?, teacher=?, room=?, note=?, day=?, startNode=?, step=?, startWeek=?, endWeek=?, type=?, color=?, ownTime=?, startTime=?, endTime=?, credit=?, level=?
            WHERE id=?;
            """
            sqlite3_prepare_v2(db, sql, -1, &stmt, nil)
            bindText(stmt, 1, c.groupId)
            bindText(stmt, 2, c.courseName)
            bindText(stmt, 3, c.teacher)
            bindText(stmt, 4, c.room)
            bindText(stmt, 5, c.note)
            sqlite3_bind_int64(stmt, 6, Int64(c.day))
            sqlite3_bind_int64(stmt, 7, Int64(c.startNode))
            sqlite3_bind_int64(stmt, 8, Int64(c.step))
            sqlite3_bind_int64(stmt, 9, Int64(c.startWeek))
            sqlite3_bind_int64(stmt, 10, Int64(c.endWeek))
            sqlite3_bind_int64(stmt, 11, Int64(c.type))
            bindText(stmt, 12, c.color)
            sqlite3_bind_int(stmt, 13, c.ownTime ? 1 : 0)
            bindText(stmt, 14, c.startTime)
            bindText(stmt, 15, c.endTime)
            sqlite3_bind_double(stmt, 16, Double(c.credit))
            sqlite3_bind_int64(stmt, 17, Int64(c.level))
            sqlite3_bind_int64(stmt, 18, c.id)
            sqlite3_step(stmt)
            sqlite3_finalize(stmt)
        }
    }

    func deleteCourse(id: Int64) {
        queue.sync {
            exec("DELETE FROM courses WHERE id=\(id);")
        }
    }

    func deleteGroup(groupId: String, tableId: Int64) {
        queue.sync {
            var stmt: OpaquePointer?
            sqlite3_prepare_v2(db, "DELETE FROM courses WHERE groupId=? AND tableId=?;", -1, &stmt, nil)
            bindText(stmt, 1, groupId)
            sqlite3_bind_int64(stmt, 2, tableId)
            sqlite3_step(stmt)
            sqlite3_finalize(stmt)
        }
    }

    func replaceCourses(tableId: Int64, courses: [Course]) {
        queue.sync {
            exec("DELETE FROM courses WHERE tableId=\(tableId);")
        }
        for c in courses {
            var cc = c
            cc.tableId = tableId
            insertCourse(cc)
        }
    }

    // MARK: - Readers

    private func readTable(_ stmt: OpaquePointer?) -> TimeTable {
        TimeTable(
            id: sqlite3_column_int64(stmt, 0),
            name: columnText(stmt, 1),
            startDate: columnText(stmt, 2),
            maxWeek: Int(sqlite3_column_int64(stmt, 3)),
            nodesPerDay: Int(sqlite3_column_int64(stmt, 4)),
            timeJson: columnText(stmt, 5),
            color: columnText(stmt, 6),
            isDefault: sqlite3_column_int(stmt, 7) == 1,
            smartConfigJson: columnText(stmt, 8),
            createdAt: sqlite3_column_int64(stmt, 9)
        )
    }

    private func readCourse(_ stmt: OpaquePointer?) -> Course {
        Course(
            id: sqlite3_column_int64(stmt, 0),
            groupId: columnText(stmt, 1),
            tableId: sqlite3_column_int64(stmt, 2),
            courseName: columnText(stmt, 3),
            teacher: columnText(stmt, 4),
            room: columnText(stmt, 5),
            note: columnText(stmt, 6),
            day: Int(sqlite3_column_int64(stmt, 7)),
            startNode: Int(sqlite3_column_int64(stmt, 8)),
            step: Int(sqlite3_column_int64(stmt, 9)),
            startWeek: Int(sqlite3_column_int64(stmt, 10)),
            endWeek: Int(sqlite3_column_int64(stmt, 11)),
            type: Int(sqlite3_column_int64(stmt, 12)),
            color: columnText(stmt, 13),
            ownTime: sqlite3_column_int(stmt, 14) == 1,
            startTime: columnText(stmt, 15),
            endTime: columnText(stmt, 16),
            credit: Float(sqlite3_column_double(stmt, 17)),
            level: Int(sqlite3_column_int64(stmt, 18))
        )
    }

    private func columnText(_ stmt: OpaquePointer?, _ i: Int32) -> String {
        guard let c = sqlite3_column_text(stmt, i) else { return "" }
        return String(cString: c)
    }

    private func bindText(_ stmt: OpaquePointer?, _ i: Int32, _ s: String) {
        sqlite3_bind_text(stmt, i, s, -1, unsafeBitCast(-1, to: sqlite3_destructor_type.self))
    }
}
