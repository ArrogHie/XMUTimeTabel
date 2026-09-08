package com.lingion.sleepy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.lingion.sleepy.MainActivity
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.CourseColorUtil
import com.lingion.sleepy.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * v19: WeekGrid widget — RemoteViews + Bitmap + Canvas
 *
 * 为什么不用 Glance: Glance 1.1.0 转 RemoteViews 时 LinearLayout 丢 Period 11+ child
 * Canvas 在 Bitmap 上画, 不受 LinearLayout child 数量限制, Period 1~9999 全显示
 *
 * 视觉复刻 CourseTableView: 圆角卡片 + gap + today 高亮 + 课程名居中
 */
class WeekGridWidgetProvider : AppWidgetProvider() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * ANR 修复: onUpdate/onAppWidgetOptionsChanged 在主线程回调,
     * 原实现 renderWidget 内含 runBlocking(DB) + Canvas 重活 → 主线程阻塞 → ANR。
     * 改用 goAsync() 获取 PendingResult, 在后台线程做完 DB 加载 + Bitmap 渲染后 finish。
     * 系统广播 ANR 阈值(前台~10s/后台~60s)由 goAsync 续命, 实际工作在 Dispatchers.Default。
     */
    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.schedule(context)
        val pending = goAsync()
        ioScope.launch {
            try {
                for (id in ids) {
                    try { renderWidget(context, awm, id) }
                    catch (e: Throwable) { Log.e(TAG, "render failed $id", e) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.onProviderDisabled(context)
        super.onDisabled(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, id: Int, newOptions: android.os.Bundle
    ) {
        val pending = goAsync()
        ioScope.launch {
            try { renderWidget(context, awm, id) }
            catch (e: Throwable) { Log.e(TAG, "optionsChanged render failed $id", e) }
            finally { pending.finish() }
        }
    }

    private fun renderWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
        var data = loadWeekData(context)
        val opts = awm.getAppWidgetOptions(widgetId)
        val density = context.resources.displayMetrics.density

        // FIX(字扁+巨大+黑边): 必须用「实际当前尺寸」而不是 MAX resize 边界。
        // 之前读 OPTION_APPWIDGET_MAX_WIDTH/HEIGHT = 616×634dp (这是 widget 能拖到的最大尺寸, 不是当前尺寸!)
        //   实际 widget 在桌面只占 ~376×651dp (窄高, ratio 0.58)。
        //   用 616×634 (ratio 0.97) 画 bitmap → fitCenter 等比缩小塞进 0.58 容器 → 上下大片留白;
        //   用 fitXY 则强行拉伸 → 字扁。根因 = bitmap 宽高比 ≠ 容器宽高比。
        // 正解 (API31+): OPTION_APPWIDGET_SIZES 返回当前真实 SizeF(dp 列表), 取最大那个 = 容器真实尺寸,
        //   bitmap 宽高比 == 容器宽高比 → 无拉伸无黑边。
        // 兼容 (API<31 回退): MIN_W × MAX_H 近似默认窄高尺寸。
        val optMaxW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val optMaxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val optMinW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val optMinH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        var wDp = 0
        var hDp = 0
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // 类型化重载 getParcelableArrayList(key, Class) 是 API 33 新增,
            //   API 31/32 调用会 NoSuchMethodError → 守卫必须用 TIRAMISU 而非 S
            val sizes = opts.getParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES, android.util.SizeF::class.java)
            sizes?.maxByOrNull { it.width * it.height }?.let { s -> wDp = s.width.toInt(); hDp = s.height.toInt() }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // API 31/32: OPTION_APPWIDGET_SIZES 已存在但只有无类型重载(开发期过时警告, 运行时安全)
            @Suppress("DEPRECATION", "UncheckedCast")
            val legacy = opts.getParcelableArrayList<android.util.SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            legacy?.maxByOrNull { it.width * it.height }?.let { s -> wDp = s.width.toInt(); hDp = s.height.toInt() }
        }
        if (wDp <= 0 || hDp <= 0) {
            // 回退: MIN_W (最窄) × MAX_H (最高) ≈ 默认放置后的窄高容器
            wDp = optMinW.takeIf { it > 0 } ?: 360
            hDp = optMaxH.takeIf { it > 0 } ?: 600
        }
        val w = (wDp * density).toInt().coerceAtLeast((180 * density).toInt())
        val h = (hDp * density).toInt().coerceAtLeast((250 * density).toInt())
        Log.d(TAG, "renderWidget: opts MAX=${optMaxW}x${optMaxH}dp MIN=${optMinW}x${optMinH}dp " +
            "SIZES_wDp=${wDp}x${hDp}dp → bitmap=${w}x${h}px ratio=%.2f (density=$density)".format(w.toFloat()/h))

        // v1.0.52-xmu9: 「本周课程」网格形态 = 小组件本体即"内部渲染窗口":
        // 不向桌面推任何整张位图(外层拉伸会变形、高度也受限), 而是整个小组件放一个 ListView,
        // 每条承载课表长图(固定最大高度)的切片 — 内部视口无限制地上下滚动, 滚动范围 = 固定上限,
        // 必定能滑到底。行高/数据变化只要刷新即可见, 不影响滚动总长。
        // 「宽行卡片」形态仍为静态位图(centerCrop 容器, 不拉伸)。
        // 空课表 / 学期外: 用静态状态图(条带窗口无内容可滚)。
        val layoutMode = AppPrefs.getWidgetGridLayout(context)
        val hasContent = data.hasTable && data.days.isNotEmpty() && data.days.any { it.courses.isNotEmpty() }
        if (layoutMode != "rows" && hasContent) {
            pushWeekGridWindow(context, awm, widgetId)
            return
        }

        val bmp = renderBitmap(context, data, w, h)
        // v1.0.52-xmu3: 容器带右上角设置齿轮 — 主图打开 app, 齿轮打开通用设置
        val views = RemoteViews(context.packageName, R.layout.widget_bitmap_gear_container)
        views.setImageViewBitmap(R.id.widget_bitmap, bmp)
        views.setOnClickPendingIntent(R.id.widget_bitmap, mainActivityPi(context, widgetId, settings = false))
        views.setOnClickPendingIntent(R.id.widget_gear, mainActivityPi(context, widgetId, settings = true))
        awm.updateAppWidget(widgetId, views)
        // Bitmap 回收: RemoteViews.setImageViewBitmap 会拷贝 bitmap 到 binder 事务,
        // 本进程持有的原 bitmap 不再需要, 立即回收避免 ~7.8MB 大图累积占内存。
        bmp.recycle()
    }

    /**
     * 网格形态: 小组件 = 内部渲染窗口(可滚动 ListView)。
     * 数据/渲染全交给 ScrollStripService(WEEKGRID 作用域): 它把固定最大高度的课表长图
     * 切成 48dp 条带; ListView 是唯一的视口, 上下滑动完整, 无外层尺寸/拉伸限制。
     */
    private fun pushWeekGridWindow(context: Context, awm: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_scroll_weekgrid)
        val svc = Intent(context, ScrollStripService::class.java)
        svc.putExtra(ScrollStripService.StripFactory.EXTRA_WIDGET_ID, widgetId)
        svc.putExtra(ScrollStripService.StripFactory.EXTRA_SCOPE, ScrollStripService.StripFactory.SCOPE_WEEKGRID)
        svc.data = android.net.Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
        views.setRemoteAdapter(R.id.widget_strip_list, svc)
        views.setPendingIntentTemplate(R.id.widget_strip_list, mainActivityPi(context, widgetId, settings = false))
        views.setOnClickPendingIntent(R.id.widget_gear, mainActivityPi(context, widgetId, settings = true))
        awm.updateAppWidget(widgetId, views)
        awm.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_strip_list)
    }

    companion object {
        private const val TAG = "WeekGridV19"

        /** 主图点击(开 app)与齿轮点击(开通用设置)的 requestCode 区分 — PendingIntent 相等性按 code+intent, 需错开 */
        private const val SETTINGS_PI_OFFSET = 100_000

        private fun mainActivityPi(
            context: Context,
            widgetId: Int,
            settings: Boolean
        ): PendingIntent = PendingIntent.getActivity(
            context,
            if (settings) widgetId + SETTINGS_PI_OFFSET else widgetId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (settings) putExtra(MainActivity.EXTRA_OPEN_GENERAL_SETTINGS, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun renderBitmap(context: Context, data: WeekData, wPx: Int, hPx: Int): Bitmap {
            // v1.0.52-xmu6: 网格形态(默认) → 横向排版(文字左对齐/换行/时间起止/表头收短);
            // 「宽行卡片」形态(rows)仍走 renderRows。
            if (AppPrefs.getWidgetGridLayout(context) != "rows") {
                return renderWeekGridBitmap(context, data, wPx, hPx)
            }
            val density = context.resources.displayMetrics.density
            val isDark = data.isDark
            val scheme = resolveSchemePublic(context, data.themeKey, isDark)
            fun androidx.compose.ui.graphics.Color.toIntArgb(): Int =
                (0xFF shl 24) or ((this.red * 255).toInt() shl 16) or
                    ((this.green * 255).toInt() shl 8) or (this.blue * 255).toInt()
            val bgSurface       = scheme.surface.toIntArgb()
            val bgContainer     = scheme.surfaceContainer.toIntArgb()
            val bgToday         = scheme.primaryContainer.toIntArgb()
            val fgPrimary       = scheme.primary.toIntArgb()
            val fgOnSurface     = scheme.onSurface.toIntArgb()
            val fgOnSurfaceVar  = scheme.onSurfaceVariant.toIntArgb()
            val gridLine        = scheme.surfaceVariant.toIntArgb()
            val colorless       = AppPrefs.isWidgetColorless(context)
            val showTimeCol     = AppPrefs.isWidgetShowTime(context)
            val fontScale       = AppPrefs.getWidgetGridFontScale(context)
            return renderRows(
                context, data, wPx, hPx, density,
                bgContainer = bgContainer, bgSurface = bgSurface,
                bgToday = bgToday, fgPrimary = fgPrimary,
                fgOnSurface = fgOnSurface, fgOnSurfaceVar = fgOnSurfaceVar,
                gridLine = gridLine, isDark = isDark,
                showTime = showTimeCol,
                corner = AppPrefs.getWidgetRowsCorner(context),
                cardGap = AppPrefs.getWidgetRowsCardGap(context),
                minCardHeightDp = AppPrefs.getWidgetRowsCardHeight(context),
                fontScale = fontScale,
                courseColorless = colorless,
            )
        }

        private fun renderRows(
            context: Context,
            data: WeekData,
            wPx: Int,
            hPx: Int,
            density: Float,
            bgContainer: Int,
            bgSurface: Int,
            bgToday: Int,
            fgPrimary: Int,
            fgOnSurface: Int,
            fgOnSurfaceVar: Int,
            gridLine: Int,
            isDark: Boolean,
            showTime: Boolean,
            corner: Float,
            cardGap: Float,
            minCardHeightDp: Float,
            fontScale: Float,
            courseColorless: Boolean,
        ): Bitmap {
            val dpf = { v: Float -> v * density }
            val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            p.color = bgContainer
            c.drawRoundRect(RectF(0f, 0f, wPx.toFloat(), hPx.toFloat()), dpf(18f), dpf(18f), p)

            val empty = !data.hasTable || data.days.isEmpty() || data.days.all { it.courses.isEmpty() }
            if (empty) {
                val ctx = SleepyApp.get()
                p.textAlign = Paint.Align.CENTER
                p.color = fgOnSurface
                p.textSize = dpf(15f)
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
                    val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                        R.string.semester_not_started else R.string.semester_ended
                    c.drawText(ctx.getString(statusRes), wPx / 2f, hPx / 2f - dpf(8f), p)
                    p.textSize = dpf(11f)
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.color = fgOnSurfaceVar
                    c.drawText(ctx.getString(R.string.today_semester_out_hint),
                        wPx / 2f, hPx / 2f + dpf(12f), p)
                } else {
                    c.drawText(ctx.getString(R.string.widget_create_schedule),
                        wPx / 2f, hPx / 2f - dpf(8f), p)
                    p.textSize = dpf(11f)
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.color = fgOnSurfaceVar
                    c.drawText(ctx.getString(R.string.widget_open_sleepy),
                        wPx / 2f, hPx / 2f + dpf(12f), p)
                }
                return bmp
            }

            val outer = dpf(8f)
            val sidePad = dpf(12f)
            val bodyX = outer + sidePad
            val bodyW = wPx - (outer + sidePad) * 2f
            val minCardH = minCardHeightDp * density      // 用户可调: 每张宽行卡片的最小高度
            val todayDow = LocalDate.now().dayOfWeek.value

            /** 贪心按字符换行(中文友好; 拉丁词超宽则截断单词)。 */
            fun wrapLines(text: String, paint: Paint, maxW: Float): List<String> {
                if (text.isEmpty()) return emptyList()
                if (paint.measureText(text) <= maxW) return listOf(text)
                val out = ArrayList<String>()
                val sb = StringBuilder()
                var w = 0f
                for (ch in text) {
                    val cw = paint.measureText(ch.toString())
                    if (w + cw > maxW && sb.isNotEmpty()) {
                        out.add(sb.toString()); sb.setLength(0); w = 0f
                    }
                    sb.append(ch); w += cw
                }
                if (sb.isNotEmpty()) out.add(sb.toString())
                return out
            }

            var y = outer + dpf(6f)
            val bottomLimit = hPx - outer - dpf(2f)

            // 统一字号: 名称 15dp, 信息 12dp(随 fontScale 缩放, 与网格"字号密度"联动)
            val nameSize = (dpf(15f) * fontScale).coerceIn(dpf(12f), dpf(19f))
            val metaSize = (nameSize * 0.82f).coerceAtLeast(dpf(9f))
            val nameLh = nameSize * 1.25f
            val metaLh = metaSize * 1.28f
            val cardPadV = dpf(6f)
            val timeJson = data.days.firstOrNull()?.timeJson ?: ""
            val showDate = data.showDate

            for (dow in data.visibleDays.sorted()) {
                val dayData = data.days.firstOrNull { it.dayOfWeek == dow } ?: continue
                val courses = dayData.courses.sortedBy { it.startNode }
                if (courses.isEmpty()) continue

                // ── 天标签: 周一(9/7) 样式小胶囊 ──
                val chipText = DateUtils.localizedDay(dow, context) +
                    (if (showDate) "  " + DateUtils.shortDate(dayData.date) else "")
                p.textSize = dpf(11f) * fontScale
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                p.textAlign = Paint.Align.CENTER
                val chipW = p.measureText(chipText) + dpf(20f)
                val chipH = dpf(22f)
                val isToday = dow == todayDow
                if (y + chipH > bottomLimit) break
                p.color = if (isToday) bgToday else bgSurface
                c.drawRoundRect(RectF(bodyX, y, bodyX + chipW, y + chipH), dpf(11f), dpf(11f), p)
                p.color = if (isToday) fgPrimary else fgOnSurface
                c.drawText(chipText, bodyX + chipW / 2f, y + chipH / 2f + p.textSize * 0.36f, p)
                y += chipH + dpf(4f) * fontScale

                for (course in courses) {
                    // 先量出名字可换行行数与信息行
                    val namePaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    namePaint.textSize = nameSize
                    namePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    val nameLines = wrapLines(course.courseName, namePaint, bodyW - cardPadV * 2f).take(4)

                    // v1.0.52-xmu4: 信息行换行铺满(不再单行省略号) — 每字段最多 2 行
                    val metaTexts = ArrayList<String>()
                    if (showTime) {
                        val t = com.lingion.sleepy.util.TimeTableUtils
                            .courseTimeString(course.startNode, course.step, timeJson)
                        if (t != null) metaTexts.add(t)
                    }
                    if (course.room.isNotBlank()) metaTexts.add(course.room)
                    if (course.teacher.isNotBlank()) metaTexts.add(course.teacher)

                    val contentW = bodyW - cardPadV * 2f
                    val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    metaPaint.textSize = metaSize
                    metaPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    val metaLines = ArrayList<String>()
                    for (t in metaTexts) {
                        val lines = wrapLines(t, metaPaint, contentW).take(2)
                        metaLines.addAll(lines)
                    }
                    val metaPx = metaLines.size * metaLh

                    val nameH = nameLines.size * nameLh
                    val contentH = cardPadV * 2f + nameH + (if (metaLines.isEmpty()) 0f else dpf(2f)) + metaPx
                    val cardH = maxOf(contentH, minCardH)   // 用户可调最小高度, 容纳更多信息
                    if (y + cardH > bottomLimit) break

                    // 卡片底色
                    val base = CourseColorUtil.pickCourseColorInt(
                        course, isDark, gridLine, courseColorless
                    )
                    p.style = Paint.Style.FILL
                    p.color = base
                    c.drawRoundRect(RectF(bodyX, y, bodyX + bodyW, y + cardH), dpf(corner), dpf(corner), p)
                    // 细描边
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = dpf(0.5f)
                    p.color = base
                    p.alpha = 90
                    c.drawRoundRect(RectF(bodyX, y, bodyX + bodyW, y + cardH), dpf(corner), dpf(corner), p)
                    p.style = Paint.Style.FILL
                    p.alpha = 255

                    val textColor = if (isDarkOn(base)) Color.WHITE else 0xFF1D1B20.toInt()
                    // v1.0.52-xmu4: 左对齐文本(课名居上, 信息逐行紧随; 有空间不再用省略号截断)
                    val tx = bodyX + cardPadV
                    var ty = y + cardPadV + nameLh * 0.82f
                    p.textAlign = Paint.Align.LEFT

                    // 课名(可换行, 居上)
                    p.color = textColor
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    p.textSize = nameSize
                    for (line in nameLines) {
                        c.drawText(line, tx, ty, p)
                        ty += nameLh
                    }

                    // 信息行(逐行下方, 左对齐)
                    if (metaLines.isNotEmpty()) {
                        ty += dpf(3f)
                        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        p.textSize = metaSize
                        p.color = textColor
                        p.alpha = 235
                        for (line in metaLines) {
                            c.drawText(line, tx, ty + metaLh * 0.78f, p)
                            ty += metaLh
                        }
                        p.alpha = 255
                    }
                    y += cardH + dpf(cardGap) * fontScale
                }
                y += dpf(8f) * fontScale
            }
            return bmp
        }

        private fun isDarkOn(color: Int): Boolean {
            val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
            return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 < 0.55
        }

        fun loadWeekData(context: Context): WeekData {
            val today = LocalDate.now()
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = AppPrefs.getThemeKey(context)
            val showDate = AppPrefs.isShowDate(context)
            // v1.0.51-xmu2: 小组件可见天 = 主课表可见天再按"隐藏周六日"过滤(与主课表独立)
            val visibleDays = AppPrefs.getWidgetVisibleDays(context)
            return try {
                // Triple<Table?, Status, List<Pair<dow, courses>>>
                val loaded = kotlinx.coroutines.runBlocking {
                    val app = SleepyApp.get()
                    val repo = app.repository
                    // 选表逻辑统一走 WidgetTableResolver（默认表优先），避免与 App 选中表不同步
                    val t = WidgetTableResolver.resolveCurrentTable()
                    val status = if (t != null)
                        DateUtils.semesterStatus(t.startDate, t.maxWeek, today)
                    else DateUtils.SemesterStatus.IN_RANGE
                    val map = if (t != null) {
                        val week = DateUtils.currentWeek(t.startDate, today)
                        (1..7).map { dow ->
                            // 学期前: 第 1 周课照常显示(预习); 学期后: 课程清空, renderer 画状态行
                            val courses = if (status == DateUtils.SemesterStatus.AFTER_END) emptyList() else
                                repo.getCoursesByDayOnce(t.id, dow)
                                    .filter { it.inWeek(week) }.sortedBy { it.startNode }
                            dow to courses
                        }
                    } else emptyList()
                    Triple(t, status, map)
                }
                val (t, status, daysPerCourse) = loaded
                if (t == null) {
                    WeekData(days = emptyList(), hasTable = false, isDark = isDark,
                        themeKey = themeKey,
                        showDate = showDate, visibleDays = visibleDays)
                } else {
                    val days = daysPerCourse.map { (dow, courses) ->
                        val date = DateUtils.dateOfWeekDay(today, dow)
                        DayData(date = date, dayOfWeek = dow, courses = courses, timeJson = t.timeJson)
                    }
                    WeekData(days = days, hasTable = true, isDark = isDark,
                        themeKey = themeKey,
                        showDate = showDate, visibleDays = visibleDays,
                        semesterStatus = status)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "loadWeekData failed", e)
                WeekData(days = emptyList(), hasTable = false, isDark = isDark,
                    themeKey = themeKey,
                    showDate = showDate, visibleDays = visibleDays)
            }
        }
    }
}
