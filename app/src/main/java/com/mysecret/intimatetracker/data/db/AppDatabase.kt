package com.mysecret.intimatetracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PartnerEntity::class,
        SexTypeEntity::class,
        PositionEntity::class,
        ToyEntity::class,
        LocationEntity::class,
        RecordEntity::class,
        PhotoEntity::class,
        TemplateEntity::class,
        SettingEntity::class,
        SyncMetaEntity::class,
        TombstoneEntity::class,
        RecordPartnerCrossRef::class,
        RecordSexTypeCrossRef::class,
        RecordPositionCrossRef::class,
        RecordToyCrossRef::class,
        RecordLocationCrossRef::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun partnerDao(): PartnerDao
    abstract fun sexTypeDao(): SexTypeDao
    abstract fun positionDao(): PositionDao
    abstract fun toyDao(): ToyDao
    abstract fun locationDao(): LocationDao
    abstract fun recordDao(): RecordDao
    abstract fun settingsDao(): SettingsDao
    abstract fun photoDao(): PhotoDao
    abstract fun templateDao(): TemplateDao
    abstract fun statsDao(): StatsDao
    abstract fun tombstoneDao(): TombstoneDao

    companion object {
        const val NAME = "tracker.db"
    }
}
