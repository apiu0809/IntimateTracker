package com.mysecret.intimatetracker.data.db

import com.mysecret.intimatetracker.domain.model.Intensity

/** 内置字典：11 个类型 / 23 个地点 / 6 个姿势（与 PRD v1.1 §4 一致） */
object SeedData {

    data class SexTypeSeed(val id: String, val name: String, val emoji: String, val sort: Int)

    data class SimpleSeed(val id: String, val name: String, val sort: Int)

    data class PositionSeed(val id: String, val name: String, val intensity: Intensity, val sort: Int)

    val sexTypes: List<SexTypeSeed> = listOf(
        SexTypeSeed("st_001", "阴道性交", "", 1),
        SexTypeSeed("st_002", "口交", "", 2),
        SexTypeSeed("st_003", "手交", "", 3),
        SexTypeSeed("st_004", "自慰", "", 4),
        SexTypeSeed("st_005", "指交", "", 5),
        SexTypeSeed("st_006", "情趣用品", "", 6),
        SexTypeSeed("st_007", "肛交", "", 7),
        SexTypeSeed("st_008", "多人性爱", "", 8),
        SexTypeSeed("st_011", "BDSM", "", 9),
        SexTypeSeed("st_012", "乳交", "", 10),
    )

    val locations: List<SimpleSeed> = listOf(
        "卧室", "厨房", "淋浴间", "厕所", "客厅", "车库", "后院", "屋顶", "按摩浴缸",
        "泳池", "海滩", "家", "饭店", "酒吧", "电影院", "剧院", "学校", "博物馆",
        "车上", "飞机", "火车", "船上", "公共场所",
    ).mapIndexed { index, name -> SimpleSeed("loc_%03d".format(index + 1), name, index + 1) }

    /** 姿势不再内置，全部由用户自行添加。 */
    val positions: List<PositionSeed> = emptyList()
}

object Seeder {
    /**
     * 老版本升级清理：删除内置姿势与「主动 / 被动」类型，并把旧关系枚举迁移到新值。
     * 均为幂等操作，可重复执行。
     */
    private suspend fun cleanupLegacyBuiltins(db: AppDatabase, now: Long) {
        db.positionDao().softDeleteAllBuiltin(now)
        db.openHelper.writableDatabase.execSQL(
            "UPDATE sex_type SET deleted_at = ? WHERE is_builtin = 1 AND name IN ('主动', '被动') AND deleted_at IS NULL",
            arrayOf(now),
        )
        db.openHelper.writableDatabase.execSQL(
            "UPDATE partner SET relationship_type = 'CASUAL' WHERE relationship_type = 'SERIOUS'",
        )
        db.openHelper.writableDatabase.execSQL(
            "UPDATE partner SET relationship_type = 'SPOUSE' WHERE relationship_type = 'MARRIED'",
        )
    }

    /** 幂等：仅在对应表为空时写入内置数据。 */
    suspend fun seed(db: AppDatabase, now: Long = System.currentTimeMillis()) {
        cleanupLegacyBuiltins(db, now)
        if (db.sexTypeDao().countBuiltin() == 0) {
            SeedData.sexTypes.forEach { seed ->
                db.sexTypeDao().upsert(
                    SexTypeEntity(
                        id = seed.id,
                        name = seed.name,
                        emoji = seed.emoji,
                        isBuiltin = true,
                        sortOrder = seed.sort,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
        // 幂等补齐后加的内置类型：只插缺失 id，不覆盖用户的隐藏/排序
        val existingIds = db.sexTypeDao().all().map { it.id }.toSet()
        SeedData.sexTypes.filter { it.id !in existingIds }.forEach { seed ->
            db.sexTypeDao().upsert(
                SexTypeEntity(
                    id = seed.id,
                    name = seed.name,
                    emoji = seed.emoji,
                    isBuiltin = true,
                    sortOrder = seed.sort,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        if (db.locationDao().countBuiltin() == 0) {
            SeedData.locations.forEach { seed ->
                db.locationDao().upsert(
                    LocationEntity(
                        id = seed.id,
                        name = seed.name,
                        isBuiltin = true,
                        sortOrder = seed.sort,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
        if (db.positionDao().countBuiltin() == 0) {
            SeedData.positions.forEach { seed ->
                db.positionDao().upsert(
                    PositionEntity(
                        id = seed.id,
                        name = seed.name,
                        intensity = seed.intensity,
                        isBuiltin = true,
                        sortOrder = seed.sort,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }
}
