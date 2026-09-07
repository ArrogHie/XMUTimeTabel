package com.lingion.sleepy.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lingion.sleepy.R
import kotlin.math.ceil

/**
 * 可滚动小组件条带工厂 (v1.0.36 第二次实现, 2026-08-25)。
 *
 * 第一次实现(已回滚)重写了一套逐行渲染函数 → 视觉与主分支完全不像, 三个组件全废。
 * 本次原则: **零新渲染逻辑** — 完全调用主分支原渲染函数
 * (renderToday / renderTwoDay / renderWeekList), 以"内容全展开高度"画一张长图,
 * 再横切成等高条带喂 ListView:
 *   - 滚动位置 0 = 原渲染器输出像素, 与主分支静态 bitmap 同源同坐标, 不存在画得不像;
 *   - 条带无间隙拼接 (divider=0, 行高=条带高) → 视觉是连续长图, 滚动即平移。
 * 内容装得下时 Receiver 直接走原 renderAndPush 静态路径, 不进本服务。
 *
 * v1.0.52-xmu11: 「本周课程」(SCOPE_WEEKGRID) 不再 48dp 等宽横切 — 改为**行带切片**:
 * 每个 Item = 一个节次行带(表头一带 + 每节次一带), 边界落在节次间隙;
 * 且每行用独立布局(widget_scroll_weekgrid_row, wrap_content) + setMinimumHeight(位图像素高),
 * 让 ListView Item 测量高度 = 位图真实像素高 → 内容不再越界、相邻 Item 不重叠。
 */
class ScrollStripService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        StripFactory(applicationContext, intent)

    class StripFactory(
        private val context: Context,
        intent: Intent
    ) : RemoteViewsFactory {

        companion object {
            const val EXTRA_WIDGET_ID = "widget_id"
            const val EXTRA_SCOPE = "scope"
            const val SCOPE_TODAY = "today"
            const val SCOPE_TWODAY = "twoday"
            const val SCOPE_WEEKLIST = "weeklist"
            const val SCOPE_WEEKGRID = "weekgrid"

            /** 条带高度 dp — 行布局 widget_scroll_row.xml layout_height 必须同值 */
            const val STRIP_DP = 48
        }

        private val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        private val scope = intent.getStringExtra(EXTRA_SCOPE) ?: SCOPE_TODAY
        private var strips: List<Bitmap> = emptyList()
        // v1.0.52-xmu11: 本周课程行带切片(位图) — 每个 Item 一个行带, 高度取位图像素高
        private var weekGridRows: List<Bitmap> = emptyList()

        override fun onCreate() {}

        override fun onDestroy() {
            // 条带经 createBitmap(src,…) 与源图共享像素缓冲, 严禁 recycle
            //   (回收源图缓冲会连带撕碎全部条带), 交 GC 统一回收。
        }

        override fun onDataSetChanged() {
            val awm = AppWidgetManager.getInstance(context)
            val opts = awm.getAppWidgetOptions(widgetId)
            val (wDp, hDp) = RemoteViewsWidgetHelper.computeSizeDp(opts)
            val density = context.resources.displayMetrics.density
            val stripPx = (STRIP_DP * density).toInt()

            // 原渲染器 + 内容全展开高度 → 渲染高度向上取整到条带整数倍(末条带不缺角)
            val contentHdp: Float
            val full: Bitmap
            when (scope) {
                SCOPE_TODAY -> {
                    val d = TodayWidgetReceiver.loadDataSync(context)
                    contentHdp = WidgetBitmapRenderers.todayContentHeightDp(d)
                    val renderH = ceil(contentHdp / STRIP_DP) * STRIP_DP
                    full = WidgetBitmapRenderers.renderToday(context, d, wDp.toFloat(), renderH)
                }
                SCOPE_TWODAY -> {
                    val d = TwoDayWidgetReceiver.loadDataSync(context)
                    contentHdp = WidgetBitmapRenderers.twoDayContentHeightDp(d)
                    val renderH = ceil(contentHdp / STRIP_DP) * STRIP_DP
                    full = WidgetBitmapRenderers.renderTwoDay(context, d, wDp.toFloat(), renderH)
                }
                SCOPE_WEEKLIST -> {
                    // v1.0.52-xmu4: 周列表不再是独立小组件, 条带通道保留给周列表(从课表微件转列表形态)
                    val d = WeekListDataLoader.loadDataSync(context)
                    contentHdp = WidgetBitmapRenderers.weekListContentHeightDp(context, d)
                    val renderH = ceil(contentHdp / STRIP_DP) * STRIP_DP
                    full = WidgetBitmapRenderers.renderWeekList(context, d, wDp.toFloat(), renderH)
                }
                SCOPE_WEEKGRID -> {
                    // v1.0.52-xmu11: 行带切片 — 每节次一带, 边界落在节次间隙(文字/卡片不跨 Item)
                    val d = WeekGridWidgetProvider.loadWeekData(context)
                    val wPx = (wDp * density).toInt()
                    val bands = weekGridBands(context, d)
                    full = renderWeekGridBitmap(context, d, wPx, bands.fullHeightPx, forceNaturalRows = true)
                    weekGridRows = (0 until bands.sliceTopPx.size).map { i ->
                        Bitmap.createBitmap(full, 0, bands.sliceTopPx[i], full.width, bands.sliceHeightPx[i])
                    }
                    contentHdp = (bands.fullHeightPx / density).toFloat()
                    strips = emptyList()
                    android.util.Log.d("ScrollStrip",
                        "weekgrid id=$widgetId ${wDp}x${hDp}dp bands=${weekGridRows.size} " +
                            "heightsPx=${bands.sliceHeightPx.toList()}")
                }
                else -> return
            }

            // 非 WEEKGRID: 仍按 48dp 等宽横切(共享像素缓冲, 不复制)
            if (scope != SCOPE_WEEKGRID) {
                val count = full.height / stripPx
                strips = (0 until count).map { i ->
                    Bitmap.createBitmap(full, 0, i * stripPx, full.width, stripPx)
                }
                android.util.Log.d("ScrollStrip",
                    "scope=$scope id=$widgetId ${wDp}x${hDp}dp content=${contentHdp}dp render=${full.height / density}dp strips=$count")
            }
        }

        override fun getCount(): Int = if (scope == SCOPE_WEEKGRID) weekGridRows.size else strips.size

        override fun getViewAt(position: Int): RemoteViews {
            if (scope == SCOPE_WEEKGRID) {
                val bmp = weekGridRows[position]
                // 行高 = 位图像素高(wrap_content + 显式 min), 与内容逐像素一致; 杜绝测高不匹配重叠
                return RemoteViews(context.packageName, R.layout.widget_scroll_weekgrid_row).apply {
                    setImageViewBitmap(R.id.widget_row_bitmap, bmp)
                    setInt(R.id.widget_row_bitmap, "setMinimumWidth", bmp.width)
                    setInt(R.id.widget_row_bitmap, "setMinimumHeight", bmp.height)
                    setOnClickFillInIntent(R.id.widget_row_bitmap, Intent())
                }
            }
            return RemoteViews(context.packageName, R.layout.widget_scroll_row).apply {
                setImageViewBitmap(R.id.widget_row_bitmap, strips[position])
                // 空 Intent 合并进 ListView 的 PendingIntentTemplate (打开 app)
                setOnClickFillInIntent(R.id.widget_row_bitmap, Intent())
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = if (scope == SCOPE_WEEKGRID) 1 else 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}
