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
