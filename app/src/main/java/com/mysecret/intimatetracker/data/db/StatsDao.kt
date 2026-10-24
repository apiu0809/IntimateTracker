package com.mysecret.intimatetracker.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CountItem(val itemKey: String?, val count: Int)

data class WeekdayCount(val weekday: Int, val count: Int)

data class MonthCount(val month: String, val count: Int)

data class SumRow(val total: Int?, val count: Int?)

/**
 * 统计查询。所有查询共用同一套过滤条件：
 * - :from / :to 时间范围（含端点）
 * - :partnerId 非空时只统计与该伴侣关联的记录
 */
@Dao
interface StatsDao {

    @Query(
        """
        SELECT COUNT(*) FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        """,
    )
    suspend fun total(from: Long, to: Long, partnerId: String?): Int

    @Query(
        """
        SELECT COUNT(*) FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to AND r.is_solo = 1
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        """,
    )
    suspend fun soloCount(from: Long, to: Long, partnerId: String?): Int

    @Query(
        """
        SELECT COUNT(DISTINCT p.partner_id) FROM record r
        JOIN record_partner_cross_ref p ON p.record_id = r.id
        JOIN partner pt ON pt.id = p.partner_id AND pt.deleted_at IS NULL AND pt.is_archived = 0
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR p.partner_id = :partnerId)
        """,
    )
    suspend fun partnerCount(from: Long, to: Long, partnerId: String?): Int

    @Query(
        """
        SELECT SUM(r.duration_minutes) AS total, COUNT(r.duration_minutes) AS count FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        """,
    )
    suspend fun durationStats(from: Long, to: Long, partnerId: String?): SumRow

    @Query(
        """
        SELECT SUM(r.satisfaction) AS total, COUNT(r.satisfaction) AS count FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        """,
    )
    suspend fun satisfactionStats(from: Long, to: Long, partnerId: String?): SumRow

    @Query(
        """
        SELECT SUM(r.my_orgasm_count) AS total, SUM(r.partner_orgasm_count) AS count FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        """,
    )
    suspend fun orgasmStats(from: Long, to: Long, partnerId: String?): SumRow

    @Query(
        """
        SELECT COUNT(*) FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND r.is_solo = 0 AND r.protection_used IS NOT NULL AND r.protection_used != 'UNKNOWN'
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        """,
    )
    suspend fun protectionRisked(from: Long, to: Long, partnerId: String?): Int

    @Query(
        """
        SELECT COUNT(*) FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND r.is_solo = 0 AND r.protection_used IS NOT NULL AND r.protection_used != 'UNKNOWN'
          AND r.protection_used = 'YES'
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        """,
    )
    suspend fun protectionUsed(from: Long, to: Long, partnerId: String?): Int

    @Query(
        """
        SELECT x.sex_type_id AS itemKey, COUNT(*) AS count FROM record r
        JOIN record_sex_type_cross_ref x ON x.record_id = r.id
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        GROUP BY x.sex_type_id ORDER BY count DESC
        """,
    )
    suspend fun typeCounts(from: Long, to: Long, partnerId: String?): List<CountItem>

    @Query(
        """
        SELECT x.location_id AS itemKey, COUNT(*) AS count FROM record r
        JOIN record_location_cross_ref x ON x.record_id = r.id
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        GROUP BY x.location_id ORDER BY count DESC
        """,
    )
    suspend fun locationCounts(from: Long, to: Long, partnerId: String?): List<CountItem>

    @Query(
        """
        SELECT x.position_id AS itemKey, COUNT(*) AS count FROM record r
        JOIN record_position_cross_ref x ON x.record_id = r.id
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        GROUP BY x.position_id ORDER BY count DESC
        """,
    )
    suspend fun positionCounts(from: Long, to: Long, partnerId: String?): List<CountItem>

    @Query(
        """
        SELECT x.toy_id AS itemKey, COUNT(*) AS count FROM record r
        JOIN record_toy_cross_ref x ON x.record_id = r.id
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        GROUP BY x.toy_id ORDER BY count DESC
        """,
    )
    suspend fun toyCounts(from: Long, to: Long, partnerId: String?): List<CountItem>

    @Query(
        """
        SELECT r.initiator AS itemKey, COUNT(*) AS count FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to AND r.initiator IS NOT NULL
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        GROUP BY r.initiator
        """,
    )
    suspend fun initiatorCounts(from: Long, to: Long, partnerId: String?): List<CountItem>

    /** weekday: 0=周日 … 6=周六（与 SQLite strftime %w 一致） */
    @Query(
        """
        SELECT CAST(strftime('%w', r.timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS weekday, COUNT(*) AS count
        FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        GROUP BY weekday
        """,
    )
    suspend fun weekdayCounts(from: Long, to: Long, partnerId: String?): List<WeekdayCount>

    @Query(
        """
        SELECT strftime('%Y-%m', r.timestamp / 1000, 'unixepoch', 'localtime') AS month, COUNT(*) AS count
        FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        GROUP BY month ORDER BY month
        """,
    )
    suspend fun monthCounts(from: Long, to: Long, partnerId: String?): List<MonthCount>

    @Query(
        """
        SELECT strftime('%Y-%m', r.timestamp / 1000, 'unixepoch', 'localtime') AS month, SUM(r.duration_minutes) AS count
        FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        GROUP BY month ORDER BY month
        """,
    )
    suspend fun monthDurations(from: Long, to: Long, partnerId: String?): List<MonthCount>

    @Query(
        """
        SELECT strftime('%Y-%m', r.timestamp / 1000, 'unixepoch', 'localtime') AS month, SUM(r.my_orgasm_count) AS count
        FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        GROUP BY month ORDER BY month
        """,
    )
    suspend fun monthOrgasms(from: Long, to: Long, partnerId: String?): List<MonthCount>

    @Query(
        """
        SELECT p.partner_id AS itemKey, COUNT(*) AS count FROM record r
        JOIN record_partner_cross_ref p ON p.record_id = r.id
        JOIN partner pt ON pt.id = p.partner_id AND pt.deleted_at IS NULL AND pt.is_archived = 0
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR p.partner_id = :partnerId)
        GROUP BY p.partner_id ORDER BY count DESC
        """,
    )
    fun observePartnerActivity(from: Long, to: Long, partnerId: String?): Flow<List<CountItem>>

    @Query(
        """
        SELECT COUNT(DISTINCT r.id) FROM record r
        JOIN record_location_cross_ref x ON x.record_id = r.id
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        """
    )
    suspend fun recordsWithLocation(from: Long, to: Long, partnerId: String?): Int

    @Query(
        """
        SELECT COUNT(DISTINCT r.id) FROM record r
        JOIN record_position_cross_ref x ON x.record_id = r.id
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        """
    )
    suspend fun recordsWithPosition(from: Long, to: Long, partnerId: String?): Int

    @Query(
        """
        SELECT COUNT(DISTINCT r.id) FROM record r
        JOIN record_toy_cross_ref x ON x.record_id = r.id
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
          AND (:partnerId IS NULL OR EXISTS (SELECT 1 FROM record_partner_cross_ref p WHERE p.record_id = r.id AND p.partner_id = :partnerId))
        """
    )
    suspend fun recordsWithToy(from: Long, to: Long, partnerId: String?): Int
}