package com.mysecret.intimatetracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移集中登记处。
 *
 * 规则：
 * 1. 修改任何 Entity（加/删字段、改类型、加索引、加表）时，`AppDatabase.version` +1；
 * 2. 在 [ALL] 中追加一条显式 `Migration(old, new)`，禁止使用破坏性回退；
 * 3. 迁移后导出新的 schema JSON 到 `app/schemas/`（KSP 已配置），并提交入库；
 * 4. 每条迁移补一个 `MigrationTest`（见 docs/DB_MIGRATION.md）。
 */
object Migrations {

    /**
     * v2：photo 表新增 `updated_at`，用于跨设备同步的逐行 LWW。
     * 旧数据用 `deleted_at`（若已删除）或 `created_at` 回填。
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE photo ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE photo SET updated_at = COALESCE(deleted_at, created_at)")
        }
    }

    /**
     * v3：`sex_type` / `location` 新增 `notes`，用于字典管理里的「说明」。
     * 姿势与情趣用品本来就有 `notes`，四类字典自此字段对齐。
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sex_type ADD COLUMN notes TEXT")
            db.execSQL("ALTER TABLE location ADD COLUMN notes TEXT")
        }
    }

    /**
     * v4：5 张关联表增加 `label` 快照列，并去掉指向伴侣 / 字典的外键。
     *
     * 动机（历史真相）：记录和伴侣 / 字典的关联是「发生过的事实」，不能因为词典条目被
     * 彻底删除（物理删行 + 墓碑）而跟着 CASCADE 消失。加了 `label` 之后，
     * 关联行自己带着当时的名字，父行不存在时照样能展示，只是标「（已删除）」。
     *
     * 旧库回填：用父表当前的 `name` 填入 `label`（与升级前的展示完全一致）。
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            rebuildCrossRefTable(db, table = "record_partner_cross_ref", parentTable = "partner", parentIdColumn = "partner_id", extraColumn = "partner_orgasm_count")
            rebuildCrossRefTable(db, table = "record_sex_type_cross_ref", parentTable = "sex_type", parentIdColumn = "sex_type_id")
            rebuildCrossRefTable(db, table = "record_position_cross_ref", parentTable = "position", parentIdColumn = "position_id")
            rebuildCrossRefTable(db, table = "record_toy_cross_ref", parentTable = "toy", parentIdColumn = "toy_id")
            rebuildCrossRefTable(db, table = "record_location_cross_ref", parentTable = "location", parentIdColumn = "location_id")
        }
    }

    /**
     * 重建一张关联表：只保留指向 `record` 的外键（删记录才需要级联），
     * 新增 `label` 列并用父表名字回填，最后补回两条索引（与 Entity 声明一致）。
     */
    private fun rebuildCrossRefTable(
        db: SupportSQLiteDatabase,
        table: String,
        parentTable: String,
        parentIdColumn: String,
        extraColumn: String? = null,
    ) {
        val extraDef = if (extraColumn != null) ", $extraColumn INTEGER" else ""
        val extraSelect = if (extraColumn != null) ", $extraColumn" else ""
        val tmp = "${table}_v4"
        db.execSQL(
            "CREATE TABLE $tmp (" +
                "record_id TEXT NOT NULL, " +
                "$parentIdColumn TEXT NOT NULL" +
                extraDef +
                ", label TEXT, " +
                "PRIMARY KEY(record_id, $parentIdColumn), " +
                "FOREIGN KEY(record_id) REFERENCES record(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "INSERT INTO $tmp (record_id, $parentIdColumn$extraSelect, label) " +
                "SELECT record_id, $parentIdColumn$extraSelect, " +
                "(SELECT name FROM $parentTable WHERE id = $parentIdColumn) FROM $table",
        )
        db.execSQL("DROP TABLE $table")
        db.execSQL("ALTER TABLE $tmp RENAME TO $table")
        db.execSQL("CREATE INDEX index_${table}_record_id ON $table(record_id)")
        db.execSQL("CREATE INDEX index_${table}_$parentIdColumn ON $table($parentIdColumn)")
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
