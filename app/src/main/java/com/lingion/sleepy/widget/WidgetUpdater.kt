package com.lingion.sleepy.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Widget 主动更新调度器：
 * — 数据变更时调用 [notifyDataChanged]
 * — 对全部已注册 receiver 广播 APPWIDGET_UPDATE(系统级刷新)
 *   v1.0.52-xmu4 起桌面小组件精简为 3 个: 今日课程 / 本周课程(网格) / 最近两天,
 *   全部同步 RemoteViews AppWidgetProvider → 秒刷, 不受 OPPO 冻结影响
 * — 每天本地时间 00:01 通过 AlarmManager 刷新一次，确保日期变化后即使 app 没有启动也会更新
 * — WorkManager 每 15 分钟兜底刷新
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val WORK_NAME = "sleepy_widget_update"
    private const val REPEAT_MINUTES = 15L
    internal const val ACTION_DATE_REFRESH = "com.lingion.sleepy.action.WIDGET_DATE_REFRESH"
    private const val DATE_REFRESH_REQUEST_CODE = 20_260_901
    private val DATE_REFRESH_TIME: LocalTime = LocalTime.of(0, 1)

    /** All widget providers receiving the synchronous refresh broadcast. */
    internal val remoteViewsReceiverClasses = listOf(
        TodayWidgetReceiver::class.java,
        WeekGridWidgetProvider::class.java,
        TwoDayWidgetReceiver::class.java
    )
    /**
     * 注册小组件刷新调度（幂等）。
     *
     * AlarmManager 负责每天 00:01 的日期切换刷新，WorkManager 作为系统限制或闹钟延迟时的兜底。
     * 没有已放置的小组件时不保留后台任务和闹钟。
     */
    fun schedule(context: Context) {
        val appContext = context.applicationContext
        if (!hasPlacedWidgets(appContext)) {
            cancelDateRefresh(appContext)
            WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
            return
        }

        scheduleNextDateRefresh(appContext)
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            REPEAT_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().build())
            .setInitialDelay(3, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * 重新设置下一次本地时间 00:01 的刷新闹钟。
     *
     * 闹钟采用一次性调度，由 [WidgetDateRefreshReceiver] 刷新后再设置下一天，
     * 避免固定 24 小时周期在夏令时或用户修改时区后逐渐偏离本地 00:01。
     */
    internal fun scheduleNextDateRefresh(context: Context) {
        val appContext = context.applicationContext
        if (!hasPlacedWidgets(appContext)) {
            cancelDateRefresh(appContext)
            return
        }

        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = dateRefreshPendingIntent(appContext)
        val nextRefresh = nextDateRefreshAt(ZonedDateTime.now())
        val triggerAtMillis = nextRefresh.toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // 没有“闹钟和提醒”特殊权限时仍然安排可在 Doze 中唤醒的非精确闹钟，
            // 同时由 WorkManager 兜底，避免为了小组件日期刷新强制用户授权。
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
            Log.w(TAG, "exact alarm permission unavailable; using inexact date refresh")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
        Log.d(TAG, "next date refresh at $nextRefresh")
    }

    /** 最近一个小组件被移除后，清理不再需要的后台调度。 */
    fun onProviderDisabled(context: Context) {
        val appContext = context.applicationContext
        if (!hasPlacedWidgets(appContext)) {
            cancelDateRefresh(appContext)
            WorkManager.getInstance(appContext).cancelUniqueWork(WORK_NAME)
        }
    }

    /** 可测试的本地时间计算：返回今天/明天最近的 00:01。 */
    internal fun nextDateRefreshAt(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(DATE_REFRESH_TIME).atZone(now.zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    internal fun hasPlacedWidgets(context: Context): Boolean {
        val awm = AppWidgetManager.getInstance(context)
        return remoteViewsReceiverClasses.any { receiver ->
            runCatching {
                awm.getAppWidgetIds(ComponentName(context, receiver)).isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun dateRefreshPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            DATE_REFRESH_REQUEST_CODE,
            Intent(context, WidgetDateRefreshReceiver::class.java).setAction(ACTION_DATE_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelDateRefresh(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = dateRefreshPendingIntent(context)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * 立即刷新所有已放置的小组件。
     *
     * - 全部 10 个 widget (RemoteViews): 同步广播 APPWIDGET_UPDATE → AppWidgetProvider.onUpdate
     *   同步调 awm.updateAppWidget(id, views) → 在 OPPO 冻结窗口前推送 → 秒刷,不受冻结影响。
     * - WorkManager 每 15 分钟兜底刷新 (schedule)。
     *
     * suspend: 调用方(MineScreen 按钮/主题切换/SleepyApp 等)在协程里 await。
     */
    suspend fun notifyDataChanged(context: Context) {
        withContext(Dispatchers.IO) {
            // 数据变化也顺手修复/续期日期闹钟，覆盖用户在旧版本中已经放置小组件的情况。
            scheduleNextDateRefresh(context)
            val awm = AppWidgetManager.getInstance(context)

            // ── 全部 10 个小组件 (RemoteViews): 同步广播,秒刷 ──

            // v1.0.29: Today/WeekList/TwoDay 已从 Glance 移植为同步 RemoteViews AppWidgetProvider,
            // 与 WeekGrid 同路径 — 普通 AppWidgetProvider.onUpdate 同步调 awm.updateAppWidget(id, views),
            // 在 OPPO 冻结窗口(5s)前就完成推送 → 永远可靠,不再卡 widget_loading。
            val remoteViewsReceivers = remoteViewsReceiverClasses
            for (receiver in remoteViewsReceivers) {
                try {
                    val component = ComponentName(context, receiver)
                    val ids = awm.getAppWidgetIds(component)
                    if (ids.isNotEmpty()) {
                        Log.d(TAG, "${receiver.simpleName} ids=${ids.toList()}, broadcasting UPDATE")
                        val intent = Intent("android.appwidget.action.APPWIDGET_UPDATE").apply {
                            this.component = component
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                        context.sendBroadcast(intent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "${receiver.simpleName} broadcast failed", e)
                }
            }
        }
    }
}

/** 每日日期切换刷新入口。AlarmManager 是一次性闹钟，刷新后重新安排下一天。 */
class WidgetDateRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetUpdater.ACTION_DATE_REFRESH) return

        val appContext = context.applicationContext
        if (!WidgetUpdater.hasPlacedWidgets(appContext)) {
            WidgetUpdater.onProviderDisabled(appContext)
            return
        }

        // 先续上下一天的闹钟，再做可能耗时的 DB/Bitmap 刷新，避免本次刷新异常导致调度链中断。
        WidgetUpdater.scheduleNextDateRefresh(appContext)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.notifyDataChanged(appContext)
            } catch (t: Throwable) {
                Log.e("WidgetDateRefresh", "date refresh failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
