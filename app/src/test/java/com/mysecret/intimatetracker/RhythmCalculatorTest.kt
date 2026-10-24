package com.mysecret.intimatetracker

import com.mysecret.intimatetracker.data.db.CalendarEntry
import com.mysecret.intimatetracker.data.repo.RhythmBand
import com.mysecret.intimatetracker.data.repo.RhythmCalculator
import com.mysecret.intimatetracker.domain.RhythmSettings
import com.mysecret.intimatetracker.ui.calendar.CalendarFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RhythmCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(daysAgo: Long, hour: Int = 20): Long =
        LocalDate.of(2026, 10, 22)
            .minusDays(daysAgo)
            .atTime(hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private val now = LocalDate.of(2026, 10, 22).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun entry(timestamp: Long, partner: String? = null, types: List<String> = emptyList()) =
        CalendarEntry(
            recordId = "r$timestamp",
            timestamp = timestamp,
            typeNames = types.joinToString("|"),
            partnerIds = partner,
        )

    @Test
    fun `days since last uses most recent day not today`() {
        val entries = listOf(entry(at(7)), entry(at(3)), entry(at(6)))

        val stats = RhythmCalculator.compute(entries, RhythmSettings(), now, zone)

        assertEquals(3, stats.daysSinceLast)
        assertEquals(LocalDate.of(2026, 10, 19), stats.lastDate)
    }

    @Test
    fun `same day record yields zero days`() {
        val entries = listOf(entry(at(0, hour = 1)))

        val stats = RhythmCalculator.compute(entries, RhythmSettings(), now, zone)

        assertEquals(0, stats.daysSinceLast)
    }

    @Test
    fun `longest gap and usual range from distinct days`() {
        // 10/15, 10/19, 10/21 → 间隔 4、2
        val entries = listOf(entry(at(7)), entry(at(3)), entry(at(1)))

        val stats = RhythmCalculator.compute(entries, RhythmSettings(), now, zone)

        assertEquals(4, stats.longestGapDays)
        assertEquals(3, stats.recordsInRange)
        assertEquals(3, stats.weekdayCounts.sum())
    }

    @Test
    fun `partner only filters solo entries`() {
        val entries = listOf(entry(at(5)), entry(at(2), partner = "p1"))
        val settings = RhythmSettings(partnerOnly = true)

        val stats = RhythmCalculator.compute(entries, settings, now, zone)

        assertEquals(2, stats.daysSinceLast)
    }

    @Test
    fun `exclude solo drops masturbation only days`() {
        val entries = listOf(
            entry(at(4), types = listOf("自慰")),
            entry(at(2), types = listOf("口交"), partner = "p1"),
        )
        val settings = RhythmSettings(excludeSolo = true)

        val stats = RhythmCalculator.compute(entries, settings, now, zone)

        assertEquals(2, stats.daysSinceLast)
    }

    @Test
    fun `range limits counted days`() {
        val entries = (0L..5L).map { entry(at(it)) }
        val settings = RhythmSettings(rangeDays = 3)

        val stats = RhythmCalculator.compute(entries, settings, now, zone)

        assertEquals(3, stats.recordsInRange)
        assertEquals(0, stats.daysSinceLast)
    }


    @Test
    fun `ring shows days below one week even in week mode`() {
        assertEquals("1", RhythmCalculator.displayNumber(1, weeklyUnit = true))
        assertEquals("天", RhythmCalculator.displayUnit(1, weeklyUnit = true))
        assertEquals("6", RhythmCalculator.displayNumber(6, weeklyUnit = true))
    }

    @Test
    fun `ring converts to weeks from seven days`() {
        assertEquals("1", RhythmCalculator.displayNumber(7, weeklyUnit = true))
        assertEquals("周", RhythmCalculator.displayUnit(7, weeklyUnit = true))
        assertEquals("2", RhythmCalculator.displayNumber(14, weeklyUnit = true))
        assertEquals("3", RhythmCalculator.displayNumber(3, weeklyUnit = false))
        assertEquals("天", RhythmCalculator.displayUnit(3, weeklyUnit = false))
    }

    @Test
    fun `ring shows dash without records`() {
        assertEquals("—", RhythmCalculator.displayNumber(null, weeklyUnit = true))
        assertNull(RhythmCalculator.displayUnit(null, weeklyUnit = true))
    }

    @Test
    fun `band follows colorize toggle`() {
        val off = RhythmSettings(colorize = false, neutralCapDays = 7, longGapDays = 14)
        assertEquals(RhythmBand.NORMAL, RhythmCalculator.band(off, 0))
        assertEquals(RhythmBand.NORMAL, RhythmCalculator.band(off, 99))
    }

    @Test
    fun `band neutral inside cap and normal after`() {
        val settings = RhythmSettings(neutralCapDays = 7, longGapDays = 14)
        assertEquals(RhythmBand.NEUTRAL, RhythmCalculator.band(settings, 7))
        assertEquals(RhythmBand.NORMAL, RhythmCalculator.band(settings, 8))
        assertEquals(RhythmBand.NORMAL, RhythmCalculator.band(settings, 14))
        assertEquals(RhythmBand.ALERT, RhythmCalculator.band(settings, 15))
    }

    @Test
    fun `band respects custom thresholds`() {
        val settings = RhythmSettings(neutralCapDays = 1, longGapDays = 2)
        assertEquals(RhythmBand.NEUTRAL, RhythmCalculator.band(settings, 1))
        assertEquals(RhythmBand.NORMAL, RhythmCalculator.band(settings, 2))
        assertEquals(RhythmBand.ALERT, RhythmCalculator.band(settings, 3))
    }

    @Test
    fun `band without records is neutral`() {
        assertEquals(RhythmBand.NEUTRAL, RhythmCalculator.band(RhythmSettings(), null))
    }

    @Test
    fun `arc is empty without records and tiny at zero days`() {
        assertEquals(0f, RhythmCalculator.arcFraction(null, 14))
        assertEquals(RhythmCalculator.MIN_ARC_FRACTION, RhythmCalculator.arcFraction(0, 14))
        assertEquals(RhythmCalculator.MIN_ARC_FRACTION, RhythmCalculator.arcFraction(1, 99))
    }

    @Test
    fun `arc fills up to the long gap threshold and then caps`() {
        assertEquals(0.5f, RhythmCalculator.arcFraction(7, 14))
        assertEquals(1f, RhythmCalculator.arcFraction(14, 14))
        assertEquals(1f, RhythmCalculator.arcFraction(17, 14))
        assertEquals(1f, RhythmCalculator.arcFraction(40, 14))
    }

    @Test
    fun `arc never divides by zero`() {
        assertEquals(1f, RhythmCalculator.arcFraction(3, 0))
        assertEquals(1f, RhythmCalculator.arcFraction(3, -5))
    }

    @Test
    fun `partner only and exclude solo combine`() {
        val entries = listOf(
            entry(at(6), types = listOf("自慰")),
            entry(at(4), types = listOf("口交"), partner = "p1"),
            entry(at(1), types = listOf("自慰")),
        )
        val settings = RhythmSettings(partnerOnly = true, excludeSolo = true)

        val stats = RhythmCalculator.compute(entries, settings, now, zone)

        assertEquals(4, stats.daysSinceLast)
        assertEquals(1, stats.recordsInRange)
    }

    @Test
    fun `range one disables usual range`() {
        val entries = listOf(entry(at(5)), entry(at(2), partner = "p1"))
        val stats = RhythmCalculator.compute(entries, RhythmSettings(rangeDays = 1), now, zone)

        assertNull(stats.usualRange)
        assertEquals(2, stats.daysSinceLast)
    }
    @Test
    fun `empty input yields null stats`() {
        val stats = RhythmCalculator.compute(emptyList(), RhythmSettings(), now, zone)

        assertNull(stats.daysSinceLast)
        assertNull(stats.lastDate)
    }


    @Test
    fun `partner title only when partner filter alone`() {
        assertEquals(true, RhythmCalculator.showsPartnerTitle(partnerOnly = true, filterActive = false))
        assertEquals(false, RhythmCalculator.showsPartnerTitle(partnerOnly = true, filterActive = true))
        assertEquals(false, RhythmCalculator.showsPartnerTitle(partnerOnly = false, filterActive = false))
        assertEquals(false, RhythmCalculator.showsPartnerTitle(partnerOnly = false, filterActive = true))
    }
    // ---- 日历筛选联动 ----

    @Test
    fun `filtering one partner narrows rhythm stats`() {
        val entries = listOf(
            entry(at(10), partner = "p1", types = listOf("口交")),
            entry(at(14), partner = "p1", types = listOf("口交")),
            entry(at(3), partner = "p2", types = listOf("阴道性交")),
            entry(at(7), types = listOf("自慰")),
        )
        val onlyP1 = CalendarFilter(partnerIds = setOf("p1"))

        val stats = RhythmCalculator.compute(entries.filter { onlyP1.matches(it) }, RhythmSettings(), now, zone)

        assertEquals(10, stats.daysSinceLast)
        assertEquals(LocalDate.of(2026, 10, 12), stats.lastDate)
        assertEquals(2, stats.recordsInRange)
    }

    @Test
    fun `filtering another partner yields its own last date`() {
        val entries = listOf(
            entry(at(10), partner = "p1"),
            entry(at(3), partner = "p2"),
            entry(at(7), types = listOf("自慰")),
        )
        val onlyP2 = CalendarFilter(partnerIds = setOf("p2"))
        val stats = RhythmCalculator.compute(entries.filter { onlyP2.matches(it) }, RhythmSettings(), now, zone)

        assertEquals(3, stats.daysSinceLast)
        assertEquals(LocalDate.of(2026, 10, 19), stats.lastDate)
    }

    @Test
    fun `solo filter only counts masturbation records`() {
        val entries = listOf(
            entry(at(10), partner = "p1"),
            entry(at(3), partner = "p2"),
            entry(at(7), types = listOf("自慰")),
            entry(at(20), types = listOf("自慰")),
        )
        val onlySolo = CalendarFilter(onlySolo = true)
        val stats = RhythmCalculator.compute(entries.filter { onlySolo.matches(it) }, RhythmSettings(), now, zone)

        assertEquals(7, stats.daysSinceLast)
        assertEquals(2, stats.recordsInRange)
    }

    @Test
    fun `no filter keeps all records`() {
        val entries = listOf(
            entry(at(10), partner = "p1"),
            entry(at(3), partner = "p2"),
            entry(at(7), types = listOf("自慰")),
        )
        val stats = RhythmCalculator.compute(entries, RhythmSettings(), now, zone)

        assertEquals(3, stats.daysSinceLast)
        assertEquals(3, stats.recordsInRange)
    }
}
