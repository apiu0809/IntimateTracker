package com.mysecret.intimatetracker.data.repo

import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.CountItem
import com.mysecret.intimatetracker.data.db.IdLabel
import com.mysecret.intimatetracker.domain.withDeletedMarker
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class NamedCount(val name: String, val count: Int, val percent: Double)

data class MonthPoint(val label: String, val value: Int)

data class StatsSnapshot(
    val total: Int = 0,
    val partnerCount: Int = 0,
    val solo: Int = 0,
    val avgSatisfaction: Double = 0.0,
    val avgDuration: Int = 0,
    val totalDuration: Int = 0,
    val protectionRate: Double = 0.0,
    val myOrgasm: Int = 0,
    val partnerOrgasm: Int = 0,
    val avgMyOrgasm: Double = 0.0,
    val avgPartnerOrgasm: Double = 0.0,
    val types: List<NamedCount> = emptyList(),
    val locations: List<NamedCount> = emptyList(),
    val positions: List<NamedCount> = emptyList(),
    val toys: List<NamedCount> = emptyList(),
    val initiators: List<NamedCount> = emptyList(),
    /** 周一..周日 的计数（7 项） */
    val weekdays: List<Int> = List(7) { 0 },
    val filledTypes: Int = 0,
    val filledLocations: Int = 0,
    val filledPositions: Int = 0,
    val filledToys: Int = 0,
    val monthActivities: List<MonthPoint> = emptyList(),
    val monthDurations: List<MonthPoint> = emptyList(),
    val monthOrgasms: List<MonthPoint> = emptyList(),
    val partnerActivities: List<NamedCount> = emptyList(),
)


@Singleton
class StatsRepository @Inject constructor(
    private val db: AppDatabase,
) {

    suspend fun load(from: Long, to: Long, partnerId: String?): StatsSnapshot {
        val dao = db.statsDao()
        val total = dao.total(from, to, partnerId)
        val solo = dao.soloCount(from, to, partnerId)
        val nonSolo = (total - solo).coerceAtLeast(0)

        val duration = dao.durationStats(from, to, partnerId)
        val satisfaction = dao.satisfactionStats(from, to, partnerId)
        val orgasm = dao.orgasmStats(from, to, partnerId)
        val ratedCount = dao.protectionRisked(from, to, partnerId)
        val protectedCount = dao.protectionUsed(from, to, partnerId)

        // 统计走「历史真相」：已删除的字典项仍参与排行，名字后加「（已删除）」；
        // 已彻底删除（物理删行）的项用关联快照补名，排行与分母都不缩水。
        val typeNames = historyNames(
            db.sexTypeDao().all().associate { it.id to it.name.withDeletedMarker(it.deletedAt != null) },
            db.recordDao().sexTypeHistoryLabels(),
        )
        val locationNames = historyNames(
            db.locationDao().all().associate { it.id to it.name.withDeletedMarker(it.deletedAt != null) },
            db.recordDao().locationHistoryLabels(),
        )
        val positionNames = historyNames(
            db.positionDao().all().associate { it.id to it.name.withDeletedMarker(it.deletedAt != null) },
            db.recordDao().positionHistoryLabels(),
        )
        val toyNames = historyNames(
            db.toyDao().all().associate { it.id to it.name.withDeletedMarker(it.deletedAt != null) },
            db.recordDao().toyHistoryLabels(),
        )
        val partnerNames = db.partnerDao().all().associate { it.id to it.name }

        val withLocation = dao.recordsWithLocation(from, to, partnerId)
        val withPosition = dao.recordsWithPosition(from, to, partnerId)
        val withToy = dao.recordsWithToy(from, to, partnerId)

        val weekdayRows = dao.weekdayCounts(from, to, partnerId).associate { it.weekday to it.count }
        val weekdays = (0..6).map { index ->
            // UI 顺序：周一..周日；SQL weekday: 0=周日
            val sqlDay = if (index == 6) 0 else index + 1
            weekdayRows[sqlDay] ?: 0
        }

        return StatsSnapshot(
            total = total,
            partnerCount = dao.partnerCount(from, to, partnerId),
            solo = solo,
            avgSatisfaction = round1(satisfaction.total.orZero(), satisfaction.count.orZero()),
            avgDuration = (duration.total.orZero().toDouble() / duration.count.orZero().coerceAtLeast(1)).toInt(),
            totalDuration = duration.total.orZero(),
            protectionRate = if (ratedCount == 0) 0.0 else protectedCount * 100.0 / ratedCount,
            myOrgasm = orgasm.total.orZero(),
            partnerOrgasm = orgasm.count.orZero(),
            avgMyOrgasm = round1(orgasm.total.orZero(), total),
            avgPartnerOrgasm = round1(orgasm.count.orZero(), nonSolo),
            types = named(dao.typeCounts(from, to, partnerId), typeNames, total),
            locations = named(dao.locationCounts(from, to, partnerId), locationNames, withLocation),
            positions = named(dao.positionCounts(from, to, partnerId), positionNames, withPosition),
            toys = named(dao.toyCounts(from, to, partnerId), toyNames, withToy),
            initiators = named(
                dao.initiatorCounts(from, to, partnerId),
                mapOf("ME" to "我", "PARTNER" to "伴侣", "BOTH" to "双方"),
                dao.initiatorCounts(from, to, partnerId).sumOf { it.count },
            ),
            weekdays = weekdays,
            filledTypes = total,
            filledLocations = withLocation,
            filledPositions = withPosition,
            filledToys = withToy,
            monthActivities = dao.monthCounts(from, to, partnerId).map { MonthPoint(it.month, it.count) },
            monthDurations = dao.monthDurations(from, to, partnerId).map { MonthPoint(it.month, it.count) },
            monthOrgasms = dao.monthOrgasms(from, to, partnerId).map { MonthPoint(it.month, it.count) },
            partnerActivities = named(dao.observePartnerActivity(from, to, partnerId).first(), partnerNames, total),
        )
    }

    private fun named(rows: List<CountItem>, names: Map<String, String>, denominator: Int): List<NamedCount> =
        rows.mapNotNull { row ->
            val id = row.itemKey ?: return@mapNotNull null
            val name = names[id] ?: return@mapNotNull null
            NamedCount(
                name = name,
                count = row.count,
                percent = if (denominator <= 0) 0.0 else row.count * 100.0 / denominator,
            )
        }

    /**
     * 已彻底删除（物理删行）的字典项不在字典表里，用关联快照补名字并标「（已删除）」，
     * 保证统计排行不因「彻底删除」少一项、占比分母也不变。
     */
    private fun historyNames(current: Map<String, String>, history: List<IdLabel>): Map<String, String> {
        if (history.isEmpty()) return current
        val missing = history.mapNotNull { row ->
            if (row.id in current) return@mapNotNull null
            val name = row.label?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            row.id to name.withDeletedMarker(true)
        }
        return if (missing.isEmpty()) current else current + missing.toMap()
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun round1(value: Int, count: Int): Double {
        if (count <= 0) return 0.0
        return String.format(Locale.US, "%.1f", value.toDouble() / count).toDouble()
    }
}
