package com.lingion.sleepy.data.jw

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.room.withTransaction
import com.lingion.sleepy.data.AppDatabase
import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.data.entity.TimeTableEntity
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 教务直连导入 ViewModel — 厦门大学专属版。
 *
 * 职责(相对原多学校版大幅收敛):
 *  1. 把抓到的课表 JSON(WebView XMU_FETCH_JS 或 [XmuAutoLoginClient] 产出)按
 *     [JwProtocol.TYPE_XMU] 协议解析成 [JwCourse]
 *  2. [JwCourse] → [CourseEntity], 建课表并落库
 *
 * 不再维护学校目录(schools.json)、URL/HTML 协议探测与多协议候选裁决。
 * 学校元数据收敛为 [XmuJw.XMU_SCHOOL]。
 */
class JwImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /**
     * 解析课表 JSON(source)。protocolType 仅接受 [JwProtocol.TYPE_XMU];
     * 其它/空类型返回空列表(调用方按"未解析到课程"处理)。
     */
    suspend fun parseHtml(source: String, protocolType: String): List<JwCourse> =
        withContext(Dispatchers.IO) {
            if (source.isBlank() || protocolType != JwProtocol.TYPE_XMU) return@withContext emptyList()
            val parser = try {
                JwParserRegistry.parserFor(protocolType, source)
            } catch (e: IllegalArgumentException) {
                return@withContext emptyList()
            }
            try {
                parser.generateCourseList()
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * 把 JwCourse 列表转成 sleepy 的 CourseEntity 列表
     */
    fun toCourseEntities(courses: List<JwCourse>, tableId: Long, defaultColor: String): List<CourseEntity> {
        return courses.map { jw ->
            val step = (jw.endNode - jw.startNode + 1).coerceAtLeast(1)
            CourseEntity(
                id = 0,
                groupId = "",
                tableId = tableId,
                courseName = jw.name.ifBlank { "未命名" },
                teacher = jw.teacher,
                room = jw.room,
                day = jw.day.coerceIn(1, 7),
                startNode = jw.startNode.coerceAtLeast(1),
                step = step,
                startWeek = jw.startWeek.coerceAtLeast(1),
                endWeek = jw.endWeek.coerceAtLeast(jw.startWeek),
                type = jw.type,
                color = defaultColor
            )
        }
    }

    /**
     * 创建新课表并落库。返回新 tableId。
     */
    suspend fun importAsNewTable(
        courses: List<JwCourse>,
        tableName: String,
        startDate: String? = null,
        timeJson: String = "",
        nodesPerDay: Int = 0
    ): Long = withContext(Dispatchers.IO) {
        if (courses.isEmpty()) throw IllegalArgumentException("课程列表为空，请确认已到达课表页面")

        val db = AppDatabase.get(getApplication())
        // 整个建表 + 落库包在单一事务里：中途失败回滚，避免留下空课表。
        val newId = db.withTransaction {
            val tableDao = db.timeTableDao()
            val courseDao = db.courseDao()

            val resolvedStartDate = startDate?.takeIf { it.isNotBlank() }
                ?.let { DateUtils.normalizeStartDate(it) }
                ?: computeCurrentSemesterStart()
            val maxNode = if (nodesPerDay > 0) nodesPerDay else courses.maxOf { maxOf(it.startNode, it.endNode) }
            val newTable = TimeTableEntity(
                id = 0,
                name = tableName.ifBlank { "导入的课表" },
                startDate = resolvedStartDate,
                timeJson = timeJson.ifBlank { TimeTableUtils.DEFAULT_TIME_JSON },
                nodesPerDay = maxNode,
                isDefault = true  // 导入的课表设为默认，widget 直接展示
            )
            val generatedId = tableDao.insert(newTable)
            tableDao.setDefault(generatedId)

            val defaultColor = "#FF6750A4"
            // 按课程名分 groupId（同名课程视为一组，便于编辑）
            val nameToGroup = mutableMapOf<String, String>()
            val entities = toCourseEntities(courses, generatedId, defaultColor).map { c ->
                val gid = nameToGroup.getOrPut(c.courseName) { java.util.UUID.randomUUID().toString() }
                c.copy(groupId = gid)
            }
            courseDao.insertAll(entities)
            generatedId
        }
        newId
    }

    /**
     * 默认学期开始日期：本学期第一周周一的 ISO 日期。
     * 如果当前是寒暑假（2月/8月），回退到上一学期。
     */
    private fun computeCurrentSemesterStart(): String {
        val today = LocalDate.now()
        val month = today.monthValue
        val semesterStartYear = if (month in 8..12) today.year else today.year - 1
        val semesterStartMonth = if (month in 8..12) 9 else 2
        val firstDay = LocalDate.of(semesterStartYear, semesterStartMonth, 1)
        return firstDay.with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
            .toString()
    }

    sealed class ImportState {
        object Idle : ImportState()
        data class Parsed(val courses: List<JwCourse>) : ImportState()
        data class Imported(val tableId: Long) : ImportState()
        data class Error(val message: String) : ImportState()
    }
}
