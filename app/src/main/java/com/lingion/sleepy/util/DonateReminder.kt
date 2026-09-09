package com.lingion.sleepy.util

/**
 * 赞赏提醒判定 — 纯函数, 无 Android 依赖, 可纯 JVM 单测。
 *
 * 规则(用户 2026-09-09 指令): 使用满 [MIN_USED_DAYS] 天后提醒一次 ——
 * 如果觉得好用, 去 GitHub 点 Star 或扫赞赏码请作者喝瓶可乐。
 * 弹窗四个动作: 跳 GitHub / 跳赞赏页 / 下次提醒 / 再也不要显示。
 *
 * 时间全部用 epochDay(Long) 表达, 由调用方注入"今天", 避免 LocalDate.now() 进逻辑
 * (仓库既有惯例, 见 WeekList 紧凑档单测)。
 */
object DonateReminder {

    /** 使用满多少天后才提醒(用户指令: 五天之后)。 */
    const val MIN_USED_DAYS = 5L

    /** "下次提醒" 推迟天数。 */
    const val SNOOZE_DAYS = 30L

    /**
     * 是否应弹出赞赏提醒。
     *
     * @param firstUseDay 首次使用日(epochDay); ≤0 表示尚未记录 → 不弹
     * @param today       今天(epochDay)
     * @param snoozeUntil "下次提醒" 的截止日(epochDay); ≤0 表示未推迟
     * @param dismissed   用户是否已选"再也不要显示"
     */
    fun shouldShow(
        firstUseDay: Long,
        today: Long,
        snoozeUntil: Long,
        dismissed: Boolean,
    ): Boolean {
        if (dismissed) return false
        if (firstUseDay <= 0L) return false
        // 已使用天数: 首日当天 = 第 1 天, 所以满 5 天即 today - firstUseDay >= 4
        if (today - firstUseDay < MIN_USED_DAYS - 1) return false
        if (snoozeUntil > 0L && today < snoozeUntil) return false
        return true
    }
}
