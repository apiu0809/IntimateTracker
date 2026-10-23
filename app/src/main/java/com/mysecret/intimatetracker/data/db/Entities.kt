package com.mysecret.intimatetracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mysecret.intimatetracker.domain.model.Gender
import com.mysecret.intimatetracker.domain.model.Initiator
import com.mysecret.intimatetracker.domain.model.Intensity
import com.mysecret.intimatetracker.domain.model.Protection
import com.mysecret.intimatetracker.domain.model.RelationshipType

// ---------------------------------------------------------------------------
// 3.1 主表
// ---------------------------------------------------------------------------

@Entity(tableName = "partner")
data class PartnerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val gender: Gender = Gender.UNKNOWN,
    @ColumnInfo(name = "relationship_type") val relationshipType: RelationshipType = RelationshipType.OTHER,
    @ColumnInfo(name = "avatar_path") val avatarPath: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(tableName = "sex_type")
data class SexTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String = "❤️",
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(tableName = "position")
data class PositionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val intensity: Intensity = Intensity.MEDIUM,
    val notes: String? = null,
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(tableName = "toy")
data class ToyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val notes: String? = null,
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(tableName = "location")
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

// ---------------------------------------------------------------------------
// 3.2 记录表
// ---------------------------------------------------------------------------

@Entity(
    tableName = "record",
    indices = [Index(value = ["timestamp"]), Index(value = ["updated_at"])],
)
data class RecordEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val icon: String? = null,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int? = null,
    val satisfaction: Int? = null,
    val initiator: Initiator? = null,
    @ColumnInfo(name = "protection_used") val protectionUsed: Protection? = null,
    @ColumnInfo(name = "my_orgasm_count") val myOrgasmCount: Int = 0,
    @ColumnInfo(name = "partner_orgasm_count") val partnerOrgasmCount: Int = 0,
    @ColumnInfo(name = "is_solo") val isSolo: Boolean = false,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

// ---------------------------------------------------------------------------
// 3.3 关联表
// ---------------------------------------------------------------------------

@Entity(
    tableName = "record_partner_cross_ref",
    primaryKeys = ["record_id", "partner_id"],
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["record_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PartnerEntity::class, parentColumns = ["id"], childColumns = ["partner_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("record_id"), Index("partner_id")],
)
data class RecordPartnerCrossRef(
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "partner_id") val partnerId: String,
    @ColumnInfo(name = "partner_orgasm_count") val partnerOrgasmCount: Int? = null,
)

@Entity(
    tableName = "record_sex_type_cross_ref",
    primaryKeys = ["record_id", "sex_type_id"],
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["record_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SexTypeEntity::class, parentColumns = ["id"], childColumns = ["sex_type_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("record_id"), Index("sex_type_id")],
)
data class RecordSexTypeCrossRef(
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "sex_type_id") val sexTypeId: String,
)

@Entity(
    tableName = "record_position_cross_ref",
    primaryKeys = ["record_id", "position_id"],
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["record_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PositionEntity::class, parentColumns = ["id"], childColumns = ["position_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("record_id"), Index("position_id")],
)
data class RecordPositionCrossRef(
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "position_id") val positionId: String,
)

@Entity(
    tableName = "record_toy_cross_ref",
    primaryKeys = ["record_id", "toy_id"],
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["record_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ToyEntity::class, parentColumns = ["id"], childColumns = ["toy_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("record_id"), Index("toy_id")],
)
data class RecordToyCrossRef(
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "toy_id") val toyId: String,
)

@Entity(
    tableName = "record_location_cross_ref",
    primaryKeys = ["record_id", "location_id"],
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["record_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LocationEntity::class, parentColumns = ["id"], childColumns = ["location_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("record_id"), Index("location_id")],
)
data class RecordLocationCrossRef(
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "location_id") val locationId: String,
)

// ---------------------------------------------------------------------------
// 3.4 照片 / 模板 / 设置 / 同步
// ---------------------------------------------------------------------------

@Entity(
    tableName = "photo",
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["record_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("record_id")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "rel_path") val relPath: String,
    val width: Int? = null,
    val height: Int? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(tableName = "template")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "remote_etag") val remoteEtag: String? = null,
    @ColumnInfo(name = "last_sync_at") val lastSyncAt: Long? = null,
    @ColumnInfo(name = "last_sync_result") val lastSyncResult: String? = null,
)

@Entity(tableName = "tombstone", primaryKeys = ["table_name", "row_id"])
data class TombstoneEntity(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "row_id") val rowId: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long,
)
