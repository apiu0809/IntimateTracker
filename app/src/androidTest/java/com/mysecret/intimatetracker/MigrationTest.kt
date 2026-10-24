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
    fun migrate_1_to_3_runs_every_step() {
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
            3,
            true,
            Migrations.MIGRATION_1_2,
            Migrations.MIGRATION_2_3,
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

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
