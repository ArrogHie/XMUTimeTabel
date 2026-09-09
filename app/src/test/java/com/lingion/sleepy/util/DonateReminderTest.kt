package com.lingion.sleepy.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 赞赏提醒判定 — 纯 JVM 单测(无 Android 依赖)。
 * 规则: 使用满 5 天后提醒一次; "下次提醒"推迟; "再也不要显示"永久关闭。
 */
class DonateReminderTest {

    private val first = 20_000L  // 任意首用日 epochDay

    private fun show(
        today: Long,
        firstUseDay: Long = first,
        snoozeUntil: Long = -1L,
        dismissed: Boolean = false,
    ) = DonateReminder.shouldShow(firstUseDay, today, snoozeUntil, dismissed)

    @Test
    fun `does not show before five days`() {
        // 首日 = 第 1 天; 第 4 天仍不弹
        assertFalse("首日不弹", show(first))
        assertFalse("第 4 天不弹", show(first + 3))
    }

    @Test
    fun `shows on the fifth day`() {
        assertTrue("第 5 天应弹", show(first + 4))
    }

    @Test
    fun `stays shown after the fifth day`() {
        assertTrue("第 6 天仍弹", show(first + 5))
        assertTrue("第 100 天仍弹", show(first + 99))
    }

    @Test
    fun `never shows when dismissed forever`() {
        assertFalse(show(first + 100, dismissed = true))
    }

    @Test
    fun `never shows without a recorded first day`() {
        assertFalse("未记录首用日不弹", show(first + 100, firstUseDay = -1L))
        assertFalse("首用日 0 不弹", show(first + 100, firstUseDay = 0L))
    }

    @Test
    fun `snooze suppresses until its deadline then resumes`() {
        val today = first + 10
        val snooze = today + DonateReminder.SNOOZE_DAYS
        assertFalse("推迟期内不弹", show(today, snoozeUntil = snooze))
        assertFalse("到期前一天不弹", show(snooze - 1, snoozeUntil = snooze))
        assertTrue("到期当天恢复弹", show(snooze, snoozeUntil = snooze))
    }

    @Test
    fun `expired snooze does not block`() {
        assertTrue(show(first + 10, snoozeUntil = first + 5))
    }

    @Test
    fun `dismissed wins over everything`() {
        // 即使已满 5 天且推迟已过期, "再也不要显示" 仍然压制
        assertFalse(show(first + 100, snoozeUntil = first + 5, dismissed = true))
    }

    @Test
    fun `constants match the product requirement`() {
        assertTrue("用户要求使用五天后提醒", DonateReminder.MIN_USED_DAYS == 5L)
        assertTrue("推迟应为正数天", DonateReminder.SNOOZE_DAYS > 0L)
    }
}
