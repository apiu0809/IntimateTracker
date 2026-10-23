package com.mysecret.intimatetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

private const val NOT_DELETED = "deleted_at IS NULL"

@Dao
interface PartnerDao {
    @Query("SELECT * FROM partner WHERE deleted_at IS NULL AND is_archived = 0 ORDER BY sort_order, name")
    fun observeActive(): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partner WHERE deleted_at IS NULL AND is_archived = 1 ORDER BY sort_order, name")
    fun observeArchived(): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partner WHERE id = :id AND deleted_at IS NULL")
    suspend fun find(id: String): PartnerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(partner: PartnerEntity)

    @Query("UPDATE partner SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM partner WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE partner SET is_archived = :archived, updated_at = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, now: Long)

    @Query("SELECT COUNT(*) FROM partner WHERE deleted_at IS NULL")
    suspend fun countActive(): Int

    @Query("SELECT * FROM partner WHERE deleted_at IS NULL")
    suspend fun all(): List<PartnerEntity>

    @Query("SELECT * FROM partner")
    suspend fun allForSync(): List<PartnerEntity>

    @Query("SELECT * FROM partner WHERE id = :id")
    suspend fun findIncludingDeleted(id: String): PartnerEntity?
}

@Dao
interface SexTypeDao {
    @Query("SELECT * FROM sex_type WHERE deleted_at IS NULL ORDER BY is_builtin DESC, sort_order, name")
    fun observeAll(): Flow<List<SexTypeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SexTypeEntity)

    @Query("UPDATE sex_type SET is_hidden = :hidden, updated_at = :now WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, now: Long)

    @Query("UPDATE sex_type SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM sex_type WHERE deleted_at IS NULL")
    suspend fun count(): Int

    @Query("SELECT * FROM sex_type")
    suspend fun all(): List<SexTypeEntity>

    @Query("SELECT * FROM sex_type WHERE id = :id")
    suspend fun byId(id: String): SexTypeEntity?

    @Query("SELECT COUNT(*) FROM sex_type WHERE deleted_at IS NULL AND is_builtin = 1")
    suspend fun countBuiltin(): Int

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM sex_type")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE sex_type SET sort_order = :order, updated_at = :now WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int, now: Long)
}

@Dao
interface PositionDao {
    @Query("SELECT * FROM position WHERE deleted_at IS NULL ORDER BY is_builtin DESC, sort_order, name")
    fun observeAll(): Flow<List<PositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PositionEntity)

    @Query("UPDATE position SET is_hidden = :hidden, updated_at = :now WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, now: Long)

    @Query("UPDATE position SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE position SET deleted_at = :now, updated_at = :now WHERE is_builtin = 1 AND deleted_at IS NULL")
    suspend fun softDeleteAllBuiltin(now: Long)

    @Query("SELECT COUNT(*) FROM position WHERE deleted_at IS NULL")
    suspend fun count(): Int

    @Query("SELECT * FROM position")
    suspend fun all(): List<PositionEntity>

    @Query("SELECT * FROM position WHERE id = :id")
    suspend fun byId(id: String): PositionEntity?

    @Query("SELECT COUNT(*) FROM position WHERE deleted_at IS NULL AND is_builtin = 1")
    suspend fun countBuiltin(): Int

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM position")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE position SET sort_order = :order, updated_at = :now WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int, now: Long)
}

@Dao
interface ToyDao {
    @Query("SELECT * FROM toy WHERE deleted_at IS NULL ORDER BY is_builtin DESC, sort_order, name")
    fun observeAll(): Flow<List<ToyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ToyEntity)

    @Query("UPDATE toy SET is_hidden = :hidden, updated_at = :now WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, now: Long)

    @Query("UPDATE toy SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM toy WHERE deleted_at IS NULL")
    suspend fun count(): Int

    @Query("SELECT * FROM toy")
    suspend fun all(): List<ToyEntity>

    @Query("SELECT * FROM toy WHERE id = :id")
    suspend fun byId(id: String): ToyEntity?

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM toy")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE toy SET sort_order = :order, updated_at = :now WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int, now: Long)
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM location WHERE deleted_at IS NULL ORDER BY is_builtin DESC, sort_order, name")
    fun observeAll(): Flow<List<LocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LocationEntity)

    @Query("UPDATE location SET is_hidden = :hidden, updated_at = :now WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean, now: Long)

    @Query("UPDATE location SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM location WHERE deleted_at IS NULL")
    suspend fun count(): Int

    @Query("SELECT * FROM location")
    suspend fun all(): List<LocationEntity>

    @Query("SELECT * FROM location WHERE id = :id")
    suspend fun byId(id: String): LocationEntity?

    @Query("SELECT COUNT(*) FROM location WHERE deleted_at IS NULL AND is_builtin = 1")
    suspend fun countBuiltin(): Int

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM location")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE location SET sort_order = :order, updated_at = :now WHERE id = :id")
    suspend fun setSortOrder(id: String, order: Int, now: Long)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM record WHERE deleted_at IS NULL AND timestamp BETWEEN :from AND :to ORDER BY timestamp")
    fun observeRange(from: Long, to: Long): Flow<List<RecordEntity>>

    @Query("SELECT * FROM record WHERE id = :id AND deleted_at IS NULL")
    suspend fun find(id: String): RecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: RecordEntity)

    @Query("SELECT MAX(timestamp) FROM record WHERE deleted_at IS NULL")
    suspend fun lastTimestamp(): Long?

    @Query("SELECT * FROM record")
    suspend fun allForSync(): List<RecordEntity>

    @Query("SELECT * FROM record WHERE id = :id")
    suspend fun findIncludingDeleted(id: String): RecordEntity?

    /** 本地数据版本：所有可同步表的最大 updated_at，用于判断本地是否有新改动。 */
    @Query(
        "SELECT MAX(v) FROM (" +
            "SELECT MAX(updated_at) AS v FROM record " +
            "UNION ALL SELECT MAX(updated_at) FROM partner " +
            "UNION ALL SELECT MAX(updated_at) FROM sex_type " +
            "UNION ALL SELECT MAX(updated_at) FROM position " +
            "UNION ALL SELECT MAX(updated_at) FROM toy " +
            "UNION ALL SELECT MAX(updated_at) FROM location " +
            "UNION ALL SELECT MAX(updated_at) FROM photo)"
    )
    suspend fun dataVersion(): Long?

    @Query("SELECT COUNT(*) FROM record WHERE deleted_at IS NULL")
    suspend fun countActive(): Int

    @Query("UPDATE record SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkPartner(ref: RecordPartnerCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkSexType(ref: RecordSexTypeCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkPosition(ref: RecordPositionCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkToy(ref: RecordToyCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkLocation(ref: RecordLocationCrossRef)

    @Query("SELECT COUNT(*) FROM record_partner_cross_ref WHERE record_id = :recordId")
    suspend fun partnerLinks(recordId: String): Int

    @Query("SELECT COUNT(*) FROM record_partner_cross_ref WHERE partner_id = :partnerId")
    suspend fun partnerLinkCount(partnerId: String): Int

    @Query("SELECT timestamp FROM record WHERE deleted_at IS NULL ORDER BY timestamp")
    suspend fun allTimestamps(): List<Long>

    @Query(
        """
        SELECT r.*,
          (SELECT GROUP_CONCAT(s.name, '|') FROM record_sex_type_cross_ref x JOIN sex_type s ON s.id = x.sex_type_id WHERE x.record_id = r.id AND s.deleted_at IS NULL) AS typeNames,
          (SELECT GROUP_CONCAT(s.emoji, '|') FROM record_sex_type_cross_ref x JOIN sex_type s ON s.id = x.sex_type_id WHERE x.record_id = r.id AND s.deleted_at IS NULL) AS typeEmojis,
          (SELECT GROUP_CONCAT(l.name, '|') FROM record_location_cross_ref x JOIN location l ON l.id = x.location_id WHERE x.record_id = r.id AND l.deleted_at IS NULL) AS locationNames,
          (SELECT GROUP_CONCAT(p.name, '|') FROM record_position_cross_ref x JOIN position p ON p.id = x.position_id WHERE x.record_id = r.id AND p.deleted_at IS NULL) AS positionNames,
          (SELECT COUNT(*) FROM record_toy_cross_ref t WHERE t.record_id = r.id) AS toyCount,
          (SELECT GROUP_CONCAT(p.name, '|') FROM record_partner_cross_ref x JOIN partner p ON p.id = x.partner_id WHERE x.record_id = r.id AND p.deleted_at IS NULL) AS partnerNames,
          (SELECT p.avatar_path FROM record_partner_cross_ref x JOIN partner p ON p.id = x.partner_id WHERE x.record_id = r.id AND p.deleted_at IS NULL ORDER BY p.sort_order LIMIT 1) AS partnerAvatarPath,
          (SELECT GROUP_CONCAT(p.partner_id, '|') FROM record_partner_cross_ref p WHERE p.record_id = r.id) AS partnerIds
        FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
        ORDER BY r.timestamp DESC
        """
    )
    fun observeListBetween(from: Long, to: Long): Flow<List<RecordListRow>>

    @Query(
        """
        SELECT r.id AS recordId, r.timestamp AS timestamp, r.icon AS icon,
          r.satisfaction AS satisfaction, r.protection_used AS protection,
          (SELECT GROUP_CONCAT(s.name, '|') FROM record_sex_type_cross_ref x JOIN sex_type s ON s.id = x.sex_type_id WHERE x.record_id = r.id AND s.deleted_at IS NULL) AS typeNames,
          (SELECT COUNT(*) FROM record_toy_cross_ref t WHERE t.record_id = r.id) AS toyCount,
          (SELECT GROUP_CONCAT(p.name, '|') FROM record_partner_cross_ref x JOIN partner p ON p.id = x.partner_id WHERE x.record_id = r.id AND p.deleted_at IS NULL) AS partnerNames,
          (SELECT p.avatar_path FROM record_partner_cross_ref x JOIN partner p ON p.id = x.partner_id WHERE x.record_id = r.id AND p.deleted_at IS NULL ORDER BY p.sort_order LIMIT 1) AS partnerAvatarPath,
          (SELECT GROUP_CONCAT(p.partner_id, '|') FROM record_partner_cross_ref p WHERE p.record_id = r.id) AS partnerIds
        FROM record r
        WHERE r.deleted_at IS NULL AND r.timestamp BETWEEN :from AND :to
        ORDER BY r.timestamp
        """
    )
    fun observeCalendar(from: Long, to: Long): Flow<List<CalendarEntry>>

    @Query("DELETE FROM record_partner_cross_ref WHERE record_id = :recordId")
    suspend fun clearPartners(recordId: String)

    @Query("SELECT partner_id FROM record_partner_cross_ref WHERE record_id = :recordId")
    suspend fun partnerIds(recordId: String): List<String>

    @Query("SELECT sex_type_id FROM record_sex_type_cross_ref WHERE record_id = :recordId")
    suspend fun sexTypeIds(recordId: String): List<String>

    @Query("SELECT position_id FROM record_position_cross_ref WHERE record_id = :recordId")
    suspend fun positionIds(recordId: String): List<String>

    @Query("SELECT toy_id FROM record_toy_cross_ref WHERE record_id = :recordId")
    suspend fun toyIds(recordId: String): List<String>

    @Query("SELECT location_id FROM record_location_cross_ref WHERE record_id = :recordId")
    suspend fun locationIds(recordId: String): List<String>

    @Query("DELETE FROM record_sex_type_cross_ref WHERE record_id = :recordId")
    suspend fun clearSexTypes(recordId: String)

    @Query("DELETE FROM record_position_cross_ref WHERE record_id = :recordId")
    suspend fun clearPositions(recordId: String)

    @Query("DELETE FROM record_toy_cross_ref WHERE record_id = :recordId")
    suspend fun clearToys(recordId: String)

    @Query("DELETE FROM record_location_cross_ref WHERE record_id = :recordId")
    suspend fun clearLocations(recordId: String)
}

data class RecordListRow(
    @androidx.room.Embedded val record: RecordEntity,
    val typeNames: String? = null,
    val typeEmojis: String? = null,
    val locationNames: String? = null,
    val positionNames: String? = null,
    val toyCount: Int = 0,
    val partnerIds: String? = null,
    val partnerNames: String? = null,
    val partnerAvatarPath: String? = null,
) {
    val partnerList: List<String> get() = partnerNames?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
    val typeList: List<String> get() = typeNames?.split('|')?.filter { it.isNotBlank() } ?: emptyList()

    val partnerIdList: List<String> get() = partnerIds?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
    val locationList: List<String> get() = locationNames?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
    val positionList: List<String> get() = positionNames?.split('|')?.filter { it.isNotBlank() } ?: emptyList()

    val icons: List<String>
        get() = com.mysecret.intimatetracker.domain.RecordIcons.derive(
            customIcon = record.icon,
            typeNames = typeList,
            hasToy = toyCount > 0,
            protection = record.protectionUsed,
            satisfaction = record.satisfaction,
        )

    val emoji: String get() = icons.firstOrNull().orEmpty()
}

data class CalendarEntry(
    val recordId: String,
    val timestamp: Long,
    val icon: String? = null,
    val satisfaction: Int? = null,
    val protection: com.mysecret.intimatetracker.domain.model.Protection? = null,
    val typeNames: String? = null,
    val toyCount: Int = 0,
    val partnerIds: String? = null,
    val partnerNames: String? = null,
    val partnerAvatarPath: String? = null,
) {
    val partnerList: List<String> get() = partnerNames?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
    val typeList: List<String> get() = typeNames?.split('|')?.filter { it.isNotBlank() } ?: emptyList()

    val partnerIdList: List<String> get() = partnerIds?.split('|')?.filter { it.isNotBlank() } ?: emptyList()

    val icons: List<String>
        get() = com.mysecret.intimatetracker.domain.RecordIcons.derive(
            customIcon = icon,
            typeNames = typeList,
            hasToy = toyCount > 0,
            protection = protection,
            satisfaction = satisfaction,
        )
}

@Dao
interface SettingsDao {
    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun delete(key: String)

    @Query("SELECT value FROM settings WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>
}

data class PhotoWithRecord(
    @androidx.room.Embedded val photo: PhotoEntity,
    val recordTimestamp: Long,
)

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Query("SELECT * FROM photo WHERE record_id = :recordId AND deleted_at IS NULL ORDER BY created_at")
    suspend fun byRecord(recordId: String): List<PhotoEntity>

    @Query("SELECT * FROM photo WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query(
        "SELECT p.*, r.timestamp AS recordTimestamp FROM photo p " +
            "JOIN record r ON r.id = p.record_id " +
            "WHERE p.deleted_at IS NULL AND r.deleted_at IS NULL " +
            "ORDER BY r.timestamp DESC, p.created_at",
    )
    fun observeAllWithRecord(): Flow<List<PhotoWithRecord>>

    @Query("UPDATE photo SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM photo")
    suspend fun allForSync(): List<PhotoEntity>

    @Query("SELECT * FROM photo WHERE id = :id")
    suspend fun byId(id: String): PhotoEntity?
}
@Dao
interface TemplateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: TemplateEntity)

    @Query("SELECT * FROM template WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("UPDATE template SET deleted_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}