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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
