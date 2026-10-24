package com.mysecret.intimatetracker.data.repo

import com.mysecret.intimatetracker.data.db.CalendarEntry
import com.mysecret.intimatetracker.domain.RhythmSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 节奏卡计算（纯函数，便于单测）：
 * 按设置过滤记录 → 取最近 rangeDays 个「有记录的日子」→ 算距上次天数 / 最长间隔 / 通常间隔 / 星期分布。
 */

/** 圆环配色档位：中性 / 正常（主题色）/ 长间隔警示。 */
enum class RhythmBand { NEUTRAL, NORMAL, ALERT }
object RhythmCalculator {


    /**
     * 圆环大字：周模式只在满 7 天时用周数，否则仍显示天数，避免「1 天」被算成 0 周。
     */


    /**
     * 标题是否显示「与伴侣」：只有单独开启「只算与伴侣」时显示；
     * 与日历筛选叠加时改回通用标题，避免语义重复和布局拥挤。
     */
    fun showsPartnerTitle(partnerOnly: Boolean, filterActive: Boolean): Boolean =
        partnerOnly && !filterActive
    /** 依据设置计算圆环配色档位（纯函数，便于测试）。 */
    fun band(settings: RhythmSettings, daysSinceLast: Int?): RhythmBand {
        if (daysSinceLast == null) return RhythmBand.NEUTRAL
        if (!settings.colorize) return RhythmBand.NORMAL
        return when {
            daysSinceLast <= settings.neutralCapDays -> RhythmBand.NEUTRAL
            daysSinceLast <= settings.longGapDays -> RhythmBand.NORMAL
            else -> RhythmBand.ALERT
        }
    }

    /** 0 天时仍画一小段，表示「刚刚」；没有记录（null）才是一段都不画。 */
    const val MIN_ARC_FRACTION = 0.06f

    /**
     * 圆环弧长比例（0..1）：以 [RhythmSettings.longGapDays] 为满格。
     *
     * 满格的端点刻意选长间隔阈值而不是通常间隔 —— 弧满 = 正好该注意了，跟 [RhythmBand.ALERT]
     * 的边界重合，于是「弧满」和「变红」同时发生，不用两套阈值去解释。超过阈值后封顶在 1，
     * 不再绕第二圈。
     */
    fun arcFraction(daysSinceLast: Int?, longGapDays: Int): Float {
        if (daysSinceLast == null) return 0f
        if (longGapDays <= 0) return 1f
        return (daysSinceLast.toFloat() / longGapDays).coerceIn(MIN_ARC_FRACTION, 1f)
    }

    fun displayNumber(daysSinceLast: Int?, weeklyUnit: Boolean): String {
        if (daysSinceLast == null) return "—"
        return if (weeklyUnit && daysSinceLast >= 7) (daysSinceLast / 7).toString() else daysSinceLast.toString()
    }

    fun displayUnit(daysSinceLast: Int?, weeklyUnit: Boolean): String? {
        if (daysSinceLast == null) return null
        return if (weeklyUnit && daysSinceLast >= 7) "周" else "天"
    }
    fun compute(
        entries: List<CalendarEntry>,
        settings: RhythmSettings = RhythmSettings(),
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): RhythmStats {
        val filtered = entries.filter { entry ->
            (!settings.partnerOnly || entry.partnerIdList.isNotEmpty()) &&
                (!settings.excludeSolo || entry.typeList.any { it != "自慰" })
        }
        if (filtered.isEmpty()) return RhythmStats(null, null, null, null)

        val recordsPerDay: Map<LocalDate, List<CalendarEntry>> = filtered.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }
        val allDays = recordsPerDay.keys.sorted()
        val days = allDays.takeLast(settings.rangeDays.coerceAtLeast(1))
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val daysSince = ChronoUnit.DAYS.between(days.last(), today).toInt().coerceAtLeast(0)
        val gaps = days.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
        val longest = gaps.maxOrNull()
        val usual = if (gaps.size >= 2) {
            val sorted = gaps.sorted()
            val median = sorted[sorted.size / 2]
            (median - 1).coerceAtLeast(0)..(median + 1)
        } else {
            null
        }
        val weekday = IntArray(7)
        days.forEach { day -> weekday[day.dayOfWeek.value - 1]++ }

        return RhythmStats(
            daysSinceLast = daysSince,
            lastDate = days.last(),
            longestGapDays = longest,
            usualRange = usual,
            weekdayCounts = weekday.toList(),
            recordsInRange = days.size,
        )
    }
}
