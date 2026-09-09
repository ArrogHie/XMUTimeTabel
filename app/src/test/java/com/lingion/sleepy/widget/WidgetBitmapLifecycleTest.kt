package com.lingion.sleepy.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 源码级守卫: 渲染路径里调用 `awm.updateAppWidget(...)` 后不能再紧跟 `Bitmap.recycle()`。
 *
 * 背景(「最近两天」默认尺寸渲染错误的根因):
 * - `RemoteViews.setImageViewBitmap` 把 Bitmap 放进 `RemoteViews.mBitmapCache`, 经 binder
 *   交给系统 AppWidgetService; 系统进程常以 ashmem 共享持有 native pixel。
 * - 若本进程在 `updateAppWidget` 后立即 `recycle()`, 启动器(异步)渲染时拿到的是
 *   recycled bitmap → `setImageBitmap` 抛 "trying to use a recycled bitmap" →
 *   `RemoteViews.apply()` 失败 → AppWidgetHostView 回落到"无法加载微件"错误视图。
 * - 症状随尺寸/分支放大: 内容超出容器时走可滚动分支(pushScrollable), 启动器还要绑定
 *   RemoteViewsService 拉条带, 渲染更慢 → 壳图被回收的窗口更大 → 默认尺寸下必现;
 *   把小组件拖高一格后内容装得下, 改走静态分支, 才看起来"正常"。
 * - 修复: [RemoteViewsWidgetHelper.renderAndPush] / [RemoteViewsWidgetHelper.pushScrollable]
 *   / [WeekGridWidgetProvider] 三处显式删除 `recycle()`; 旧 bitmap 随下一轮 onUpdate 推送
 *   新 RemoteViews 时由 mBitmapCache 自然回收。
 *
 * 仓库无 Robolectric(Bitmap 像素管线无法在纯 JVM 跑), 采用源码静态检查守住契约。
 */
class WidgetBitmapLifecycleTest {

    private fun widgetSource(name: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/com/lingion/sleepy/widget/$name")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$name not found above CWD=${File(".").absolutePath}")
    }

    /**
     * 对每个 `updateAppWidget(` 调用点向后扫描(至方法结束或 60 行上限), 区间内不得出现
     * 可执行的 `.recycle()`。注释行(// 之前)跳过, 避免 "不能 bmp.recycle()!" 守卫注释误报。
     */
    private fun assertNoRecycleAfterUpdateAppWidget(file: File, friendlyName: String) {
        val lines = file.readLines()
        val violations = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].contains("updateAppWidget(")) {
                var braceDepth = 0
                var seenBrace = false
                for (j in (i + 1) until minOf(i + 60, lines.size)) {
                    val line = lines[j]
                    val codePart = line.substringBefore("//").trim()
                    if (codePart.contains(".recycle()")) {
                        violations += "$friendlyName:${j + 1}: ${line.trim()}"
                        break
                    }
                    for (c in line) {
                        if (c == '{') { braceDepth++; seenBrace = true }
                        else if (c == '}') braceDepth--
                    }
                    if (seenBrace && braceDepth == 0) break
                }
            }
            i++
        }
        assertTrue(
            "$friendlyName: recycle() found after updateAppWidget → $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `RemoteViewsWidgetHelper does not recycle after updateAppWidget`() {
        assertNoRecycleAfterUpdateAppWidget(
            widgetSource("RemoteViewsWidgetHelper.kt"),
            "RemoteViewsWidgetHelper"
        )
    }

    @Test
    fun `WeekGridWidgetProvider does not recycle after updateAppWidget`() {
        assertNoRecycleAfterUpdateAppWidget(
            widgetSource("WeekGridWidgetProvider.kt"),
            "WeekGridWidgetProvider"
        )
    }

    /** 守卫注释必须在场, 防止后人误读旧注释后把 recycle 加回去。 */
    @Test
    fun `recycle guard comments are preserved in render sources`() {
        val rvh = widgetSource("RemoteViewsWidgetHelper.kt").readText()
        val wgp = widgetSource("WeekGridWidgetProvider.kt").readText()
        assertTrue(
            "RemoteViewsWidgetHelper.kt 缺失 recycle 守卫注释",
            rvh.contains("不能 bmp.recycle()") || rvh.contains("不能 recycle")
        )
        assertTrue(
            "WeekGridWidgetProvider.kt 缺失 recycle 守卫注释",
            wgp.contains("Bitmap 回收已删除")
        )
        assertFalse(
            "RemoteViewsWidgetHelper.kt 还残留旧的 binder-copy 误导注释",
            rvh.contains("拷贝 bitmap 到 binder 事务")
        )
        assertFalse(
            "WeekGridWidgetProvider.kt 还残留旧的 binder-copy 误导注释",
            wgp.contains("拷贝 bitmap 到 binder 事务")
        )
    }
}
