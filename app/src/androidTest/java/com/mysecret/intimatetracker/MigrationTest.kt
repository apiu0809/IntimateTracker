package com.mysecret.intimatetracker

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据库迁移回归：v1 → v2 → v3。
 *
 * 校验点是「旧库能升上来」+「新列存在且默认值合理」，不重复测业务逻辑。
 * schema 由 KSP 导出到 `app/schemas`，测试通过 `androidTest` assets 读取。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate_2_to_3_adds_notes_to_sex_type_and_location() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO sex_type (id, name, emoji, is_builtin, is_hidden, sort_order, created_at, updated_at) " +
                    "VALUES ('st_test', '测试类型', '❤️', 0, 0, 99, 1000, 1000)",
            )
            db.execSQL(
                "INSERT INTO location (id, name, is_builtin, is_hidden, sort_order, created_at, updated_at) " +
                    "VALUES ('loc_test', '沙发', 0, 0, 99, 1000, 1000)",
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        db.query("SELECT name, notes FROM sex_type WHERE id = 'st_test'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("测试类型", cursor.getString(0))
            assertNull(cursor.getString(1))
        }
        db.query("SELECT name, notes FROM location WHERE id = 'loc_test'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("沙发", cursor.getString(0))
            assertNull(cursor.getString(1))
        }
    }

    @Test
    fun migrate_1_to_4_runs_every_step() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO photo (id, record_id, rel_path, created_at) " +
                    "VALUES ('ph_test', 'rec_test', 'images/2026/01/a.jpg', 1234)",
            )
            db.execSQL(
                "INSERT INTO location (id, name, is_builtin, is_hidden, sort_order, created_at, updated_at) " +
                    "VALUES ('loc_old', '老地点', 1, 0, 1, 1000, 1000)",
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            Migrations.MIGRATION_1_2,
            Migrations.MIGRATION_2_3,
            Migrations.MIGRATION_3_4,
        )

        db.query("SELECT updated_at FROM photo WHERE id = 'ph_test'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1234L, cursor.getLong(0))
        }
        db.query("SELECT notes FROM location WHERE id = 'loc_old'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNull(cursor.getString(0))
        }
    }

    /**
     * v4 的核心承诺：关联行自带名字快照，且不再对伴侣 / 字典挂级联外键。
     * 升级后物理删除父行，历史关联必须还在。
     */
    @Test
    fun migrate_3_to_4_backfills_labels_and_drops_parent_cascade() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO partner (id, name, gender, relationship_type, is_archived, sort_order, created_at, updated_at) " +
                    "VALUES ('p_old', '旧伴侣', 'FEMALE', 'LOVER', 0, 1, 1000, 1000)",
            )
            db.execSQL(
                "INSERT INTO sex_type (id, name, emoji, is_builtin, is_hidden, sort_order, created_at, updated_at) " +
                    "VALUES ('st_old', '旧类型', '❤️', 0, 0, 1, 1000, 1000)",
            )
            db.execSQL(
                "INSERT INTO record (id, timestamp, my_orgasm_count, partner_orgasm_count, is_solo, created_at, updated_at) " +
                    "VALUES ('r_old', 1000, 0, 0, 0, 1000, 1000)",
            )
            db.execSQL(
                "INSERT INTO record_partner_cross_ref (record_id, partner_id, partner_orgasm_count) " +
                    "VALUES ('r_old', 'p_old', 2)",
            )
            db.execSQL(
                "INSERT INTO record_sex_type_cross_ref (record_id, sex_type_id) VALUES ('r_old', 'st_old')",
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_3_4)

        db.query(
            "SELECT label, partner_orgasm_count FROM record_partner_cross_ref WHERE partner_id = 'p_old'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("旧伴侣", cursor.getString(0))
            assertEquals(2, cursor.getInt(1))
        }
        db.query("SELECT label FROM record_sex_type_cross_ref WHERE sex_type_id = 'st_old'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("旧类型", cursor.getString(0))
        }

        // 没有父表外键了：彻底删除伴侣 / 字典项不再级联掉历史关联
        db.execSQL("DELETE FROM partner WHERE id = 'p_old'")
        db.execSQL("DELETE FROM sex_type WHERE id = 'st_old'")
        db.query("SELECT COUNT(*) FROM record_partner_cross_ref").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("伴侣删除后关联必须留下", 1, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM record_sex_type_cross_ref").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("字典删除后关联必须留下", 1, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
