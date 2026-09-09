package com.lingion.sleepy.widget

import com.lingion.sleepy.data.entity.CourseEntity
import com.lingion.sleepy.util.DateUtils
import com.lingion.sleepy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * 「最近两天」小尺寸边界 — 纯 JVM 单测 + 布局源码守卫。
 *
 * 背景(用户 2026-09-09 报障): 小组件高度低于约 4 格时内部 ListView/课程卡错位、越界、裁剪;
 * 拖到 4 格及以上恢复正常。根因 = 高度小 → [WidgetBitmapRenderers.twoDayContentHeightDp]
 * 大于容器高度 → 走**可滚动条带**分支(壳图 + 48dp 切片 ListView); 而该分支的条带行
 * `widget_scroll_row.xml` 被改成 `scaleType=centerCrop`(静态整图分支是 fitXY)。
 * 条带是**一张长图横切的连续片段**, 一旦上报宽度与实际容器略有出入, centerCrop 会等比放大
 * 后裁掉每条带上下两端 → 文字被切、相邻条带错位。高度够大时改走静态整图分支, 故"正常"。
 *
 * 修复: 条带行回归 `fitXY` + 固定 48dp(= STRIP_DP), 与壳图同一缩放语义。
 */
class TwoDaySizeBoundaryTest {

    // ── 内容高度: 小尺寸下确实超过常见小容器, 从而进入可滚动分支 ──

    private fun course(name: String, startNode: Int) = CourseEntity(
        id = 0L, groupId = "g-$name", tableId = 1L, courseName = name,
        day = 2, startNode = startNode, step = 2, startWeek = 1, endWeek = 16,
        color = "#FF6750A4"
    )

    private fun twoDay(coursesPerDay: Int): TwoDayData = TwoDayData(
        days = listOf(
            DayData(
                LocalDate.of(2026, 9, 1), 2,
                (1..coursesPerDay).map { course("课$it", it * 2 - 1) },
                TimeTableUtils.DEFAULT_TIME_JSON
            ),
            DayData(
                LocalDate.of(2026, 9, 2), 3,
                (1..coursesPerDay).map { course("课$it", it * 2 - 1) },
                TimeTableUtils.DEFAULT_TIME_JSON
            )
        ),
        hasTable = true,
        semesterStatus = DateUtils.SemesterStatus.IN_RANGE
    )

    @Test
    fun `content height grows with course count`() {
        // pad(12) + 标签(22) + 列头(20) + n×(44 胶囊 + 8 间距) + 底部 pad(12)
        assertEquals(82f, WidgetBitmapRenderers.twoDayContentHeightDp(twoDay(0)), 0.01f)
        assertEquals(118f, WidgetBitmapRenderers.twoDayContentHeightDp(twoDay(1)), 0.01f)
        assertEquals(170f, WidgetBitmapRenderers.twoDayContentHeightDp(twoDay(2)), 0.01f)
        assertEquals(222f, WidgetBitmapRenderers.twoDayContentHeightDp(twoDay(3)), 0.01f)
    }

    @Test
    fun `small widget height falls into scrollable branch`() {
        // TwoDay minResizeHeight=140dp。每天 2 门课 → 内容 170dp > 140dp → 必然进入
        // 可滚动分支(壳图 + 48dp 切片 ListView) —— 这正是小尺寸渲染异常暴露的路径。
        val content = WidgetBitmapRenderers.twoDayContentHeightDp(twoDay(2))
        assertTrue("2 门课内容高 $content 应超过最小可拖高度 140dp", content > 140f)
    }

    @Test
    fun `roomy widget height stays on static branch`() {
        // 高度充足时内容装得下 → 静态整图分支(fitXY), 与用户观察"拖高后正常"一致
        val content = WidgetBitmapRenderers.twoDayContentHeightDp(twoDay(1))
        assertTrue("高度 220dp 应能装下 1 门课内容 $content", content <= 220f)
    }

    // ── 布局源码守卫: 条带行必须是 fitXY(高度保持), 且与壳图缩放语义一致 ──

    private fun widgetRes(relative: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/res/$relative")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$relative not found above CWD=${File(".").absolutePath}")
    }

    @Test
    fun `strip row uses fitXY and matches strip height`() {
        val xml = widgetRes("layout/widget_scroll_row.xml").readText()
        assertTrue(
            "条带行必须用 fitXY: 条带是长图的连续切片, centerCrop 会在宽度出入时裁掉上下两端",
            xml.contains("android:scaleType=\"fitXY\"")
        )
        assertFalse(
            "条带行不得回退到 centerCrop(小尺寸错位根因)",
            xml.contains("android:scaleType=\"centerCrop\"")
        )
        assertTrue(
            "条带行高度必须 == ScrollStripService.STRIP_DP(48dp)",
            xml.contains("android:layout_height=\"48dp\"")
        )
    }

    // ── 内部渲染窗口架构守卫(用户 2026-09-09 指令: 同「本周课程」) ──

    @Test
    fun `twoday window layout has listview and no shell image`() {
        val xml = widgetRes("layout/widget_scroll_twoday.xml").readText()
        assertTrue("窗口容器必须含 ListView(内部视口)", xml.contains("@+id/widget_strip_list"))
        assertFalse(
            "窗口容器不得再有壳图 — 壳图按容器高渲染、条带按自然高渲染正是错位根源",
            xml.contains("@+id/widget_shell")
        )
    }

    @Test
    fun `twoday row is dynamic height and height preserving`() {
        val xml = widgetRes("layout/widget_scroll_twoday_row.xml").readText()
        assertTrue("行高必须 wrap_content(运行时 setMinimumHeight 位图像素高)", xml.contains("android:layout_height=\"wrap_content\""))
        assertTrue(
            "行必须用 fitXY: 切片是连续片段, 纵向必须按原始比例铺满",
            xml.contains("android:scaleType=\"fitXY\"")
        )
        assertFalse("行不得用 centerCrop(会纵向裁切切片)", xml.contains("android:scaleType=\"centerCrop\""))
    }

    @Test
    fun `twoday bands tile the content exactly`() {
        // 行带几何(纯函数): 各片首尾相接, 总高 = 片数 × 片高, 且覆盖自然内容高度
        val contentHdp = WidgetBitmapRenderers.twoDayContentHeightDp(twoDay(2))
        val bands = WidgetBitmapRenderers.twoDayBands(density = 1f, contentHdp = contentHdp, minHeightPx = 0)
        assertTrue("至少一片", bands.sliceTopPx.isNotEmpty())
        assertEquals("片数 = 顶部数组长度", bands.sliceTopPx.size, bands.sliceHeightPx.size)
        for (i in bands.sliceTopPx.indices) {
            assertEquals("第 $i 片起点 = i × 片高", i * bands.sliceHeightPx[i], bands.sliceTopPx[i])
        }
        assertEquals("总高 = 片数 × 片高", bands.fullHeightPx, bands.sliceHeightPx.sum())
        assertTrue(
            "长图总高须覆盖自然内容高度 $contentHdp",
            bands.fullHeightPx >= contentHdp.toInt()
        )
    }

    @Test
    fun `twoday bands pad up to container height`() {
        // 容器比内容高时, 长图向下补齐 → 窗口铺满容器, 不露底色断层
        val contentHdp = WidgetBitmapRenderers.twoDayContentHeightDp(twoDay(0))
        val bands = WidgetBitmapRenderers.twoDayBands(density = 1f, contentHdp = contentHdp, minHeightPx = 1000)
        assertTrue("长图总高应 >= 容器高 1000", bands.fullHeightPx >= 1000)
    }

    @Test
    fun `twoday bands scale with density`() {
        // 2 倍密度下片高翻倍, 片数减半(内容高度以 dp 计不变)
        val contentHdp = WidgetBitmapRenderers.twoDayContentHeightDp(twoDay(2))
        val mdpi = WidgetBitmapRenderers.twoDayBands(1f, contentHdp, 0)
        val xhdpi = WidgetBitmapRenderers.twoDayBands(2f, contentHdp, 0)
        assertEquals("2x 密度片高翻倍", mdpi.sliceHeightPx[0] * 2, xhdpi.sliceHeightPx[0])
        assertTrue("2x 密度总高 ≈ 2 倍", xhdpi.fullHeightPx >= mdpi.fullHeightPx * 2 - 2)
    }

    @Test
    fun `scrollable shell uses the same scaleType as strips`() {
        // Today 仍走壳图+条带; 壳图与条带缩放语义必须一致, 否则滚动位置 0 与壳图对不上
        val xml = widgetRes("layout/widget_scroll_today.xml").readText()
        assertTrue("widget_scroll_today 壳图应与条带同为 fitXY", xml.contains("android:scaleType=\"fitXY\""))
    }
}
