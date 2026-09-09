package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * 桌面 TwoDay 小组件 — 同步 RemoteViews + Canvas (v1.0.29 起, 从 Glance 移植)。
 * 原因见 [TodayWidgetReceiver] 注释。
 *
 * 用户 2026-09-09 指令: 直接采用与「本周课程」相同的渲染方式 — **内部再开窗口渲染**。
 * 小组件本体 = ListView(内部视口), 内容长图按行带切片喂给各 Item, 容器不缩放不裁剪 →
 * 任意高度(含默认小尺寸)都不变形/不裁切/不出现壳图与条带错位。
 * (旧实现按"内容装得下"二选一: 静态整图 fitXY / 壳图+条带; 两套几何在小尺寸下对不齐。)
 *
 * Glance 版 TwoDayWidget 类已删除(决策 D5-11); loadDataSync 自 Glance companion 迁入本类。
 */
open class TwoDayWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // variantHint 死属性已删: 内部渲染窗口统一走全量排版, 不再有 SMALL 变体分支
    // (v1.0.52-xmu4 已精简小变体, 用户 2026-09-09 改窗口渲染后连分支都不存在)。

    private fun push(context: Context, awm: AppWidgetManager, id: Int) {
        // 用户 2026-09-09 指令: 直接采用与「本周课程」相同的渲染方式 — 内部再开窗口渲染。
        // 不再按"内容是否装得下"二选一(静态整图 / 壳图+条带); 那个分支正是小尺寸错位的根源:
        // 静态分支把整图 fitXY 压进容器, 可滚动分支的壳图按容器高渲染、条带按自然高渲染,
        // 两套几何不一致 → 高度越小越明显。现在统一 = ListView 内部视口, 容器不缩放不裁剪。
        RemoteViewsWidgetHelper.pushWindow(
            context, awm, id, TAG,
            layoutRes = com.lingion.sleepy.R.layout.widget_scroll_twoday,
            scopeExtra = ScrollStripService.StripFactory.SCOPE_TWODAY
        )
    }

    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.schedule(context)
        val pending = goAsync()
        ioScope.launch {
            try {
                for (id in ids) {
                    try { push(context, awm, id) }
                    catch (e: Throwable) { Log.e(TAG, "render failed $id", e) }
                }
            } finally { pending.finish() }
        }
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.onProviderDisabled(context)
        super.onDisabled(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, id: Int, newOptions: Bundle
    ) {
        val pending = goAsync()
        ioScope.launch {
            try { push(context, awm, id) }
            catch (e: Throwable) { Log.e(TAG, "optionsChanged render failed $id", e) }
            finally { pending.finish() }
        }
    }

    companion object {
        private const val TAG = "TwoDayRV"

        /**
         * 同步版数据加载 — 今天 + 明天课程。
         */
        fun loadDataSync(context: Context): TwoDayData {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.lingion.sleepy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.lingion.sleepy.util.AppPrefs.getThemeKey(context)
            return try {
                runBlocking {
                    val app = SleepyApp.get()
                    val repo = app.repository
                    val table = WidgetTableResolver.resolveCurrentTable()
                    if (table == null) {
                        TwoDayData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
                    } else {
                        val week = DateUtils.currentWeek(table.startDate, today)
                        val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, today)
                        val todayDow = today.dayOfWeek.value
                        val tomorrowDow = tomorrow.dayOfWeek.value
                        // 学期外不展示课程 — 与 App 今日页同语义
                        val todayCourses = if (status != DateUtils.SemesterStatus.IN_RANGE) emptyList() else
                            repo.getCoursesByDayOnce(table.id, todayDow)
                                .filter { it.inWeek(week) }.sortedBy { it.startNode }
                        val tomorrowCourses = if (status != DateUtils.SemesterStatus.IN_RANGE) emptyList() else
                            repo.getCoursesByDayOnce(table.id, tomorrowDow)
                                .filter { it.inWeek(week) }.sortedBy { it.startNode }
                        TwoDayData(
                            days = listOf(
                                DayData(date = today, dayOfWeek = todayDow, courses = todayCourses, timeJson = table.timeJson),
                                DayData(date = tomorrow, dayOfWeek = tomorrowDow, courses = tomorrowCourses, timeJson = table.timeJson)
                            ),
                            hasTable = true,
                            isDark = isDark,
                            themeKey = themeKey,
                            semesterStatus = status
                        )
                    }
                }
            } catch (_: Throwable) {
                TwoDayData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            }
        }
    }
}
