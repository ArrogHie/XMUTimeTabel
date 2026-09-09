package com.lingion.sleepy.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.lingion.sleepy.R
import com.lingion.sleepy.SleepyApp
import com.lingion.sleepy.util.AppPrefs
import com.lingion.sleepy.util.CourseColorUtil
import com.lingion.sleepy.util.DateUtils
import kotlin.math.roundToInt

/**
 * 「本周课程」(周历网格)小组件的横向排版渲染 — v1.0.52-xmu6。
 *
 * 取代旧的"竖排文字 / 窄卡片"网格绘制:
 *  - 课程卡内文字一律**横向**、左上对齐: 课名逐行, 地点 / 老师各自按卡片宽换行;
 *  - 星期+日期表头高度收短(约为旧的 2/3);
 *  - 左侧时间列除节次号外同时显示**开始与结束**时间;
 *  - 行高按「每节课行高」设定, 内容超出小组件可视高度时由外层交给可滚动条带(ScrollStripService),
 *    本文件提供与渲染一致的自然内容高度 [weekGridGridNaturalHeightPx] 供滚动判定。
 */

/** 从 timeJson 解析每节 (开始, 结束) — 解析失败回退空。 */
private fun parsePeriodTimes(timeJson: String): List<Pair<String, String>> {
    return try {
        val arr = org.json.JSONArray(timeJson)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val s = o.optString("start", "")
            val e = o.optString("end", "")
            s to e
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/** 该周表实际需要画的节次数(课程最大到达节, 无课表则按作息节次数, 兜底 12)。 */
private fun gridNodeCount(data: WeekData): Int {
    val byCourse = data.days.flatMap { it.courses }
        .maxOfOrNull { it.startNode + it.step - 1 }
    if (byCourse != null) return byCourse.coerceAtLeast(1)
    val jsonCount = data.days.firstOrNull()?.timeJson?.let { parsePeriodTimes(it).size } ?: 0
    return jsonCount.coerceAtLeast(1)
}

/**
 * 网格形态的"自然内容高度"(px) — 与 [renderWeekGridBitmap] 中按每节课行高固定的行距一致。
 * 外层用它判断是否需要滚动, 以及给滚动条带渲染全高内容。
 */
internal fun weekGridGridNaturalHeightPx(context: Context, data: WeekData): Int {
    val density = context.resources.displayMetrics.density
    val rowScale = AppPrefs.getWidgetGridRowScale(context)
    val userGapH = AppPrefs.getWidgetGridGapH(context)
    val dp = { v: Float -> (v * density).roundToInt() }
    val maxNode = gridNodeCount(data)
    val naturalSlot = (40f * rowScale * density).roundToInt().coerceAtLeast(dp(16f))
    val gapH = dp(userGapH)
    val outerPad = dp(6f)
    val headH = dp(38f)
    // 布局: 表头从 outerPad 起; 主体第 1 行 y = outerPad+headH+gapH;
    // 末行底 = outerPad+headH + maxNode*(naturalSlot+gapH); 再补底部 padding
    return outerPad + headH + maxNode * (naturalSlot + gapH) + gapH + outerPad
}

/**
 * 「本周课程」网格行带几何(px) — v1.0.52-xmu11。
 *
 * 把整张课表长图切成若干个**行带**, 每个 ListView Item = 一个行带:
 *   - 行带 0: 表头(星期/日期)区(高度 = 顶部留白 + 表头 + 行首间隙)
 *   - 行带 1..maxNode: 每个节次一行, 高度 = rowPitch(含行末间隙)
 * 切片边界落在"节次与节次之间的间隙"上 → 文字/卡片永不横跨两个 Item;
 * 每个 Item 位图像素高与 ListView 行高一致(wrap_content + 像素高), 杜绝测高不匹配重叠。
 * 常量必须与 [renderWeekGridBitmap] 内几何逐字一致(同源公式, 防漂移)。
 */
internal class WeekGridBands(
    val sliceTopPx: IntArray,
    val sliceHeightPx: IntArray,
    val fullHeightPx: Int
)

internal fun weekGridBands(context: Context, data: WeekData): WeekGridBands {
    val density = context.resources.displayMetrics.density
    val rowScale = AppPrefs.getWidgetGridRowScale(context)
    val userGapH = AppPrefs.getWidgetGridGapH(context)
    val dp = { v: Float -> (v * density).roundToInt() }
    val outerPad = dp(6f)
    val headH = dp(38f)
    val gapH = dp(userGapH)
    val naturalSlot = (40f * rowScale * density).roundToInt().coerceAtLeast(dp(16f))
    val rowPitch = naturalSlot + gapH
    val maxNode = gridNodeCount(data)
    val bodyTop = outerPad + headH
    val top0 = bodyTop + gapH                     // 第 1 节次内容顶
    val fullHeight = top0 + maxNode * rowPitch    // 表头带 + maxNode 个节次行带
    val tops = IntArray(maxNode + 1)
    val heights = IntArray(maxNode + 1)
    tops[0] = 0
    heights[0] = top0
    for (i in 1..maxNode) {
        tops[i] = top0 + (i - 1) * rowPitch
        heights[i] = rowPitch
    }
    return WeekGridBands(tops, heights, fullHeight)
}

/**
 * 渲染「本周课程」网格(横向排版)。
 *
 * @param hPx 目标画布高度: 传小组件实际高度(可视壳图)或滚动全图高度。
 * @param forceNaturalRows true 时每节课固定用"自然行高"(40dp × 每节课行高), 供滚动条带
 *                         渲染全高内容 — 保证滚动图与壳图行距逐像素一致、且必定能滚到底。
 *                         false(默认)时若内容能完整放入画布则拉伸填满, 放不下则用自然行高(裁底)。
 */
internal fun renderWeekGridBitmap(
    context: Context,
    data: WeekData,
    wPx: Int,
    hPx: Int,
    forceNaturalRows: Boolean = false,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val isDark = data.isDark

    val scheme = resolveSchemePublic(context, data.themeKey, isDark)
    fun androidx.compose.ui.graphics.Color.toIntArgb(): Int =
        (0xFF shl 24) or ((this.red * 255).toInt() shl 16) or
            ((this.green * 255).toInt() shl 8) or (this.blue * 255).toInt()
    val bgSurface = scheme.surface.toIntArgb()
    val bgContainer = scheme.surfaceContainer.toIntArgb()
    val bgToday = scheme.primaryContainer.toIntArgb()
    val fgPrimary = scheme.primary.toIntArgb()
    val fgOnSurface = scheme.onSurface.toIntArgb()
    val fgOnSurfaceVar = scheme.onSurfaceVariant.toIntArgb()
    val gridLine = scheme.surfaceVariant.toIntArgb()

    val showTimeCol = AppPrefs.isWidgetShowTime(context)
    val userCorner = AppPrefs.getWidgetGridCorner(context)
    val userGapH = AppPrefs.getWidgetGridGapH(context)
    val userGapW = AppPrefs.getWidgetGridGapW(context)
    val fontScale = AppPrefs.getWidgetGridFontScale(context)
    val rowScale = AppPrefs.getWidgetGridRowScale(context)
    val colorless = AppPrefs.isWidgetColorless(context)

    val dp = { v: Float -> (v * density).roundToInt() }
    val outerPad = dp(6f)
    val headH = dp(38f)                          // 表头较旧版 56dp 收短约 1/3
    val timeW = if (showTimeCol) dp(52f) else dp(0f)  // 时间列: 节次号+开始+结束, 需略宽
    val gapH = dp(userGapH)
    val gapW = dp(userGapW)
    val cardCorner = dp(userCorner).toFloat()

    val timeJson = data.days.firstOrNull()?.timeJson ?: ""
    val allPeriods = parsePeriodTimes(timeJson)
    val maxNode = gridNodeCount(data)
    val periods = allPeriods.take(maxNode)

    val sortedDays = data.visibleDays.sorted()
    val dayCount = sortedDays.size.coerceIn(1, 7)
    val todayDow = DateUtils.todayDayOfWeek()

    // ── 空 / 学期外状态(与旧实现同文案) ──
    val empty = !data.hasTable || data.days.isEmpty() || data.days.all { it.courses.isEmpty() }

    val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = bgContainer
    c.drawRoundRect(RectF(0f, 0f, wPx.toFloat(), hPx.toFloat()), dp(18f).toFloat(), dp(18f).toFloat(), p)

    if (empty) {
        val ctx = SleepyApp.get()
        p.textAlign = Paint.Align.CENTER
        p.color = fgOnSurface
        p.textSize = dp(15f).toFloat()
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
            val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                R.string.semester_not_started else R.string.semester_ended
            c.drawText(ctx.getString(statusRes), wPx / 2f, hPx / 2f - dp(8f), p)
            p.textSize = dp(11f).toFloat()
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            p.color = fgOnSurfaceVar
            c.drawText(ctx.getString(R.string.today_semester_out_hint), wPx / 2f, hPx / 2f + dp(12f), p)
        } else {
            c.drawText(ctx.getString(R.string.widget_create_schedule), wPx / 2f, hPx / 2f - dp(8f), p)
            p.textSize = dp(11f).toFloat()
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            p.color = fgOnSurfaceVar
            c.drawText(ctx.getString(R.string.widget_open_sleepy), wPx / 2f, hPx / 2f + dp(12f), p)
        }
        return bmp
    }

    val bodyW = wPx - outerPad * 2
    val bodyH = (hPx - outerPad * 2 - headH).coerceAtLeast(dp(16f))
    val bottomEdge = (hPx - outerPad).toFloat()
    val totalGapW = gapW * (dayCount + 1)
    val dayW = ((bodyW - timeW - totalGapW) / dayCount).toFloat().coerceAtLeast(dp(16f).toFloat())
    val totalGapH = gapH * (maxNode + 1)
    // 自然行高 = 每节课行高(40dp × rowScale, 随「每节课行高」滑杆)。
    // 能完整放入画布: 拉伸填满(fitSlot≥naturalSlot); 放不下: 用自然行高(下缘裁给滚动条带);
    // forceNaturalRows(滚动全图): 恒用自然行高, 保证与壳图行距一致、内容可完整滚到底。
    val naturalSlot = (40f * rowScale * density).roundToInt().coerceAtLeast(dp(16f)).toFloat()
    val fitSlot = ((bodyH - totalGapH) / maxNode).toFloat().coerceAtLeast(dp(3f).toFloat())
    val slotH = if (forceNaturalRows) naturalSlot else maxOf(naturalSlot, fitSlot)
    val rowPitch = slotH + gapH

    // 字号随 fontScale; 时间列/卡片用
    val fs = { base: Float -> (base * fontScale).coerceAtMost(dp(14f).toFloat()) }

    val x = outerPad.toFloat()
    var y = outerPad.toFloat()

    // ── 表头(星期 + 日期; 小字只显示日期, 不再显示课数 — 与 App 网格表头同规则) ──
    p.textAlign = Paint.Align.CENTER
    if (timeW > 0) {
        p.color = bgSurface
        c.drawRoundRect(RectF(x, y, x + timeW, y + headH), dp(12f).toFloat(), dp(12f).toFloat(), p)
        if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START) {
            val ctx = SleepyApp.get()
            p.color = fgOnSurfaceVar
            p.textSize = fs((headH * 0.22f).coerceAtMost(dp(8f).toFloat()).coerceAtLeast(dp(5f).toFloat()))
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            c.drawText(ctx.getString(R.string.semester_not_started), x + timeW / 2f, y + headH * 0.62f, p)
        }
    }
    for ((idx, dow) in sortedDays.withIndex()) {
        val cellX = x + timeW + gapW + idx * (dayW + gapW)
        val isToday = dow == todayDow
        val dayData = data.days.firstOrNull { it.dayOfWeek == dow }
        val dateStr = if (data.showDate && dayData != null) DateUtils.shortDate(dayData.date) else null
        p.color = if (isToday) bgToday else bgSurface
        c.drawRoundRect(RectF(cellX, y, cellX + dayW, y + headH), dp(12f).toFloat(), dp(12f).toFloat(), p)

        val dayName = DateUtils.localizedDay(dow, SleepyApp.get())
        p.color = if (isToday) fgPrimary else fgOnSurface
        p.textSize = (headH * 0.30f).coerceAtMost(dp(13f).toFloat() * fontScale).coerceAtLeast(dp(9f).toFloat())
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val cx = cellX + dayW / 2f
        c.drawText(dayName, cx, y + headH * 0.36f, p)
        p.textSize = (headH * 0.20f).coerceAtMost(dp(9f).toFloat()).coerceAtLeast(dp(6f).toFloat())
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.color = fgOnSurfaceVar
        if (dateStr != null) {
            c.drawText(dateStr, cx, y + headH * 0.72f, p)
        }
    }

    // ── 主体 ──
    y = (outerPad + headH).toFloat()
    val bodyTop = y

    // 每行浅分隔线(便于横向阅读)
    p.color = gridLine
    p.alpha = 90
    p.strokeWidth = 1f
    val rowLineY = { i: Int -> bodyTop + gapH + i * rowPitch }
    for (i in 0..maxNode) {
        val ry = rowLineY(i)
        if (ry > bottomEdge) break
        val lineX0 = x + timeW + gapW
        c.drawLine(lineX0, ry, x + bodyW - gapW, ry, p)
    }
    p.alpha = 255

    // ── 时间列: 节次号 + 开始 + 结束(单元格内垂直居中的三行块; 字号随单元格高, 行距≥字高) ──
    if (timeW > 0) {
        p.textAlign = Paint.Align.CENTER
        val timeCX = x + timeW / 2f
        for (i in 1..maxNode) {
            val rowY = bodyTop + gapH + (i - 1) * rowPitch
            if (rowY > bottomEdge) break
            val per = periods.getOrNull(i - 1)
            val startT = per?.first?.takeIf { it.isNotBlank() }
            val endT = per?.second?.takeIf { it.isNotBlank() }

            // 字号(随 fontScale 与单元格高度, 但设下限防过小): 节次号略大, 时间小一档
            val numSize = fs((slotH * 0.30f).coerceAtMost(dp(13f).toFloat()).coerceAtLeast(dp(8f).toFloat()))
            val tSize = fs((slotH * 0.19f).coerceAtMost(dp(9f).toFloat()).coerceAtLeast(dp(5f).toFloat()))
            val tGap = (tSize * 1.5f).coerceAtLeast(tSize + dp(1f).toFloat())  // 行距: 至少高出 1dp, 不粘连

            // 块内容总高(基线系) — 严格保证 ≤ slotH, 否则缩小时间字号(宁可小也不越格)
            val rows = 1 + (if (startT != null) 1 else 0) + (if (endT != null) 1 else 0)
            var blockH = numSize * 1.1f + (rows - 1) * tGap
            var effNum = numSize
            var effT = tSize
            var effGap = tGap
            if (blockH > slotH && rows > 1) {
                // 收缩到放得下: 时间行占比缩小, 节次号保持
                effGap = ((slotH - numSize * 1.1f) / (rows - 1)).coerceAtLeast(tSize + dp(0.5f).toFloat())
                effT = tSize
                blockH = numSize * 1.1f + (rows - 1) * effGap
            }
            if (blockH > slotH) {
                // 极端窄行: 节次号也收缩
                val scale = (slotH / blockH).coerceAtMost(1f)
                effNum = numSize * scale
                effT = tSize * scale
                effGap = tGap * scale
            }

            // 整块在单元格内垂直居中; 文字用基线, 块顶偏移 = (slotH - blockH)/2 + 首行上半
            val topPad = (slotH - (effNum * 1.1f + (rows - 1) * effGap)) / 2f
            var ty = rowY + topPad + effNum * 0.92f

            // 先裁剪: 文字绝不越出本行格(兜底, 防任何极端设置下的串行重叠)
            val cellClip = RectF(x, rowY, x + timeW, rowY + slotH)
            c.save()
            c.clipRect(cellClip)
            p.color = fgOnSurface
            p.textSize = effNum
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            c.drawText("$i", timeCX, ty, p)
            ty += effNum * 0.5f + effGap * 0.5f
            if (startT != null || endT != null) {
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                p.color = fgOnSurfaceVar
                if (startT != null) {
                    p.textSize = effT
                    c.drawText(startT, timeCX, ty, p)
                    ty += effGap
                }
                if (endT != null) {
                    p.textSize = effT
                    c.drawText(endT, timeCX, ty, p)
                }
            }
            c.restore()
        }
    }

    // ── 课程卡: 横向文字, 左上对齐, 逐行换行; 字号固定(不随行高膨胀, 避免文字跨卡溢出) ──
    val cardPadX = dp(4f)
    val cardPadY = dp(4f)
    // 固定字号: 名称 12dp, 信息 10dp(随 fontScale), 与行高/卡片高无关 → 任意「每节课行高」下
    // 文字行高恒定, 行距恒定, 不重叠; 卡片够高就多显示几行, 不够就少显示(其余裁掉)。
    val nameSize = (dp(12f).toFloat() * fontScale).coerceIn(dp(9f).toFloat(), dp(14f).toFloat())
    val metaSize = (nameSize * 0.85f).coerceAtLeast(dp(8f).toFloat())
    val nameLh = nameSize * 1.25f
    val metaLh = metaSize * 1.35f

    val mp = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 按可用宽度逐字换行(拉丁超宽单词也逐字拆, 保证不越出卡片)。 */
    fun wrapTo(text: String, paint: Paint, maxW: Float): List<String> {
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

    for ((idx, dow) in sortedDays.withIndex()) {
        val colX = x + timeW + gapW + idx * (dayW + gapW)
        val dayData = data.days.firstOrNull { it.dayOfWeek == dow } ?: continue
        if (dayData.courses.isEmpty()) continue

        val isToday = dow == todayDow
        if (isToday) {
            p.color = bgToday
            p.alpha = 36
            c.drawRect(RectF(colX, bodyTop, colX + dayW, (hPx - outerPad).toFloat()), p)
            p.alpha = 255
        }

        val lanes = com.lingion.sleepy.util.ConflictLayoutEngine.gridDayLanes(dayData.courses)
        for (laneRect in lanes) {
            val course = laneRect.course
            val startIdx = (course.startNode - 1).coerceAtLeast(0)
            val step = course.step.coerceAtLeast(1).coerceAtMost(maxNode - startIdx)
            val cardTop = bodyTop + gapH + startIdx * rowPitch
            if (cardTop >= (hPx - outerPad).toFloat()) continue
            val cardH = rowPitch * step - gapH
            val laneX = colX + dayW * laneRect.laneStartFraction
            val laneW = dayW * laneRect.laneWidthFraction
            val right = minOf(laneX + laneW, (wPx - outerPad).toFloat())
            val cardRect = RectF(laneX, cardTop, right, cardTop + cardH)
            if (cardRect.width() <= 0) continue

            // 整卡绘制+文字都裁剪在卡内: 任何文字/描边都不越过本卡边界 → 卡与卡绝不互相污染
            c.save()
            c.clipRect(cardRect.left, cardRect.top, cardRect.right, cardRect.bottom)
            val base = CourseColorUtil.pickCourseColorInt(course, isDark, gridLine, colorless)
            p.style = Paint.Style.FILL
            p.color = base
            p.alpha = 210
            c.drawRoundRect(cardRect, cardCorner, cardCorner, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = dp(0.5f).toFloat()
            p.color = base
            p.alpha = 80
            c.drawRoundRect(cardRect, cardCorner, cardCorner, p)
            p.style = Paint.Style.FILL
            p.alpha = 255

            val textColor = if (CourseColorUtil.luminance(base) < 0.5f) Color.WHITE else 0xFF1D1B20.toInt()
            val availW = (cardRect.width() - cardPadX * 2).coerceAtLeast(1f)
            // 卡片内可用行数: 以"信息最小 1 行"为下限, 课名最多占 6 行
            val textTop = cardRect.top + cardPadY
            val textBottom = cardRect.bottom - cardPadY
            val availH = (textBottom - textTop).coerceAtLeast(0f)
            // 课名可占行数 = 剩余高度 - 至少 1 行信息
            val metaMinLines = if (course.room.isNotBlank() || course.teacher.isNotBlank()) 1 else 0
            val nameMaxLines = ((availH - metaMinLines * metaLh) / nameLh).toInt().coerceIn(1, 6)
            val tx = cardRect.left + cardPadX

            mp.textSize = nameSize
            mp.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val nameLines = wrapTo(course.courseName, mp, availW).take(nameMaxLines)
            var usedH = nameLines.size * nameLh

            // 信息字段: 地点 / 老师 — 余下高度逐行换行(不画省略号, 严格在卡内)
            val fields = buildList {
                if (course.room.isNotBlank()) add(course.room)
                if (course.teacher.isNotBlank()) add(course.teacher)
            }
            mp.textSize = metaSize
            mp.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            var metaBudget = (availH - usedH).coerceAtLeast(0f)
            val metaLines = ArrayList<String>()
            for (f in fields) {
                if (metaBudget < metaLh) break
                var budgetLines = (metaBudget / metaLh).toInt().coerceAtLeast(0)
                if (budgetLines == 0) break
                val wrapped = wrapTo(f, mp, availW)
                var added = 0
                for (line in wrapped) {
                    if (added >= budgetLines) break
                    metaLines.add(line); added++
                }
                val consumed = added * metaLh
                metaBudget -= consumed
                usedH += consumed
            }

            var ty = textTop + nameLh * 0.9f
            p.textAlign = Paint.Align.LEFT
            p.color = textColor
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = nameSize
            for (line in nameLines) {
                c.drawText(line, tx, ty, p)
                ty += nameLh
            }
            if (metaLines.isNotEmpty()) {
                ty += dp(1f).toFloat()
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                p.textSize = metaSize
                p.color = textColor
                p.alpha = 235
                for (line in metaLines) {
                    if (ty > textBottom - metaLh * 0.2f) break
                    c.drawText(line, tx, ty + metaLh * 0.88f, p)
                    ty += metaLh
                }
                p.alpha = 255
            }
            c.restore()
        }
    }

    return bmp
}
