package com.lingion.sleepy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 确保数据变更广播覆盖全部 3 个保留的桌面小组件(今日 / 本周课程 / 最近两天)。
 * v1.0.52-xmu4: 同名小尺寸变体已精简, 无 WeekList/WeekView/Small 变体。
 */
class WidgetUpdaterWiringTest {
    @Test
    fun `refresh receiver list contains all kept widget providers`() {
        val receivers = WidgetUpdater.remoteViewsReceiverClasses
        val expected = listOf(
            TodayWidgetReceiver::class.java,
            WeekGridWidgetProvider::class.java,
            TwoDayWidgetReceiver::class.java
        )

        assertEquals("3 widget providers are registered", 3, receivers.size)
        expected.forEach { receiver ->
            assertTrue("missing ${receiver.simpleName}", receiver in receivers)
        }
    }

    @Test
    fun `next date refresh is at 00 01 in the current timezone`() {
        val zone = ZoneId.of("Asia/Shanghai")

        val beforeTarget = ZonedDateTime.of(
            LocalDate.of(2026, 9, 8), LocalTime.of(0, 0, 30), zone
        )
        assertEquals(
            ZonedDateTime.of(LocalDate.of(2026, 9, 8), LocalTime.of(0, 1), zone),
            WidgetUpdater.nextDateRefreshAt(beforeTarget)
        )

        val afterTarget = ZonedDateTime.of(
            LocalDate.of(2026, 9, 8), LocalTime.of(0, 1, 1), zone
        )
        assertEquals(
            ZonedDateTime.of(LocalDate.of(2026, 9, 9), LocalTime.of(0, 1), zone),
            WidgetUpdater.nextDateRefreshAt(afterTarget)
        )
    }
}
