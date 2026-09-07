package com.lingion.sleepy.widget

import android.content.Context
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * 周(7 天)课程数据加载 — 「本周课程」网格小组件与可滚动周列表条带共用。
 *
 * v1.0.52-xmu4: 独立的 WeekList/WeekView 桌面小组件已删除, 周数据装配自原
 * WeekListWidgetReceiver 收敛到此处(纯加载, 不依赖任何 AppWidgetProvider), 供
 * 「本周课程」网格小组件与可滚动周列表条带(ScrollStripService)共用。
 */
object WeekListDataLoader {

    /** 同步加载 — 7 日列课程, 与 [WeekGridWidgetProvider.loadWeekData] 同结构(含学期状态)。 */
    fun loadDataSync(context: Context): WeekData {
        val today = LocalDate.now()
        val isSystemDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
        val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
        val showDate = com.lingion.sleepy.util.AppPrefs.isShowDate(context)
        val visibleDays = com.lingion.sleepy.util.AppPrefs.getVisibleDays(context)
        return try {
            runBlocking {
                val app = SleepyApp.get()
                val repo = app.repository
                val table = WidgetTableResolver.resolveCurrentTable()
                if (table == null) {
                    WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey,
                        showDate = showDate, visibleDays = visibleDays)
                } else {
                    val week = DateUtils.currentWeek(table.startDate, today)
                    val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, today)
                    // 学期前: 第 1 周课照常显示(预习); 学期后: 课程清空, renderer 画状态行
                    val days = (1..7).map { dayOfWeek ->
                        val date = DateUtils.dateOfWeekDay(today, dayOfWeek)
                        val all = repo.getCoursesByDayOnce(table.id, dayOfWeek)
                        val visible = if (status == DateUtils.SemesterStatus.AFTER_END) emptyList() else
                            all.filter { it.inWeek(week) }.sortedBy { it.startNode }
                        DayData(date = date, dayOfWeek = dayOfWeek, courses = visible, timeJson = table.timeJson)
                    }
                    WeekData(days = days, hasTable = true, isDark = isDark, themeKey = themeKey,
                        showDate = showDate, visibleDays = visibleDays, semesterStatus = status)
                }
            }
        } catch (_: Throwable) {
            WeekData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey,
                showDate = showDate, visibleDays = visibleDays)
        }
    }
}
