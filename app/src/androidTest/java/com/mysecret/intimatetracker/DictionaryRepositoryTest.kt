package com.mysecret.intimatetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.LocationEntity
import com.mysecret.intimatetracker.data.db.RecordEntity
import com.mysecret.intimatetracker.data.db.RecordSexTypeCrossRef
import com.mysecret.intimatetracker.data.db.Seeder
import com.mysecret.intimatetracker.data.repo.DictionaryItem
import com.mysecret.intimatetracker.data.repo.DictionaryKind
import com.mysecret.intimatetracker.data.repo.DictionaryRepository
import com.mysecret.intimatetracker.domain.model.Intensity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 字典管理（姿势 / 地点 / 性爱类型 / 情趣用品）的仓库层回归。
 * 覆盖：新增写备注与强度、编辑只改内容（且不丢记录关联）、软删 / 恢复 / 彻底删除、
 * 墓碑与种子（彻底删除的内置项不复活）、平铺排序重写 1..N。
 */
@RunWith(AndroidJUnit4::class)
class DictionaryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: DictionaryRepository
    private var syncCalls = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncCalls = 0
        repository = DictionaryRepository(db) { syncCalls++ }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun add_position_keeps_name_note_intensity_and_triggers_sync() = runBlocking {
        repository.add(DictionaryKind.POSITION, "  背身女上位  ", "  偏深  ", Intensity.VERY_LOW)

        val item = repository.observe(DictionaryKind.POSITION).first().single()
        assertEquals("背身女上位", item.name)
        assertEquals("偏深", item.note)
        assertEquals(Intensity.VERY_LOW, item.intensity)
        assertEquals(1, syncCalls)
    }

    @Test
    fun add_toy_stores_description_and_blank_note_becomes_null() = runBlocking {
        repository.add(DictionaryKind.TOY, "跳蛋", "入门款")
        repository.add(DictionaryKind.TOY, "润滑液", "   ")

        val toys = repository.observe(DictionaryKind.TOY).first()
        assertEquals(listOf("跳蛋", "润滑液"), toys.map { it.name })
        assertEquals("入门款", toys[0].note)
        assertNull(toys[1].note)
    }

    @Test
    fun add_location_and_sex_type_keep_notes() = runBlocking {
        repository.add(DictionaryKind.LOCATION, "沙发", "客厅角落")
        repository.add(DictionaryKind.SEX_TYPE, "素股", "不插入")

        assertEquals("客厅角落", repository.observe(DictionaryKind.LOCATION).first().single().note)
        assertEquals("不插入", repository.observe(DictionaryKind.SEX_TYPE).first().single().note)
    }

    @Test
    fun update_position_changes_content_but_keeps_sort_order_and_flags() = runBlocking {
        repository.add(DictionaryKind.POSITION, "传教士", "经典", Intensity.LOW)
        val before = repository.observe(DictionaryKind.POSITION).first().single()

        repository.update(DictionaryKind.POSITION, before.id, "传教士改", "改良", Intensity.HIGH)

        val after = repository.observe(DictionaryKind.POSITION).first().single()
        assertEquals("传教士改", after.name)
        assertEquals("改良", after.note)
        assertEquals(Intensity.HIGH, after.intensity)
        assertEquals(
            db.positionDao().byId(before.id)?.sortOrder,
            db.positionDao().byId(after.id)?.sortOrder,
        )
        assertNull(db.positionDao().byId(after.id)?.deletedAt)
    }

    @Test
    fun update_is_ignored_for_blank_name() = runBlocking {
        repository.add(DictionaryKind.POSITION, "侧入", null, Intensity.MEDIUM)
        val before = repository.observe(DictionaryKind.POSITION).first().single()

        repository.update(DictionaryKind.POSITION, before.id, "   ", "备注", Intensity.HIGH)

        assertEquals("侧入", repository.observe(DictionaryKind.POSITION).first().single().name)
    }

    @Test
    fun delete_soft_deletes_and_drops_item_from_list() = runBlocking {
        repository.add(DictionaryKind.POSITION, "站立后入", null, Intensity.HIGH)
        val item = repository.observe(DictionaryKind.POSITION).first().single()

        repository.delete(DictionaryKind.POSITION, item.id)

        assertTrue(repository.observe(DictionaryKind.POSITION).first().isEmpty())
        assertEquals(0, db.positionDao().count())
    }

    @Test
    fun observe_including_deleted_keeps_deleted_items_for_edit_form() = runBlocking {
        repository.add(DictionaryKind.TOY, "跳蛋", "入门款")
        val item = repository.observe(DictionaryKind.TOY).first().single()
        repository.delete(DictionaryKind.TOY, item.id)

        assertTrue("管理页 / 新建记录仍只看未删项", repository.observe(DictionaryKind.TOY).first().isEmpty())
        val all = repository.observeIncludingDeleted(DictionaryKind.TOY).first()
        assertEquals(1, all.size)
        assertEquals("跳蛋", all.single().name)
        assertEquals("入门款", all.single().note)
        assertTrue("编辑记录时能识别这是已删项", all.single().deleted)
    }

    @Test
    fun find_by_name_includes_deleted_but_ignores_self() = runBlocking {
        repository.add(DictionaryKind.LOCATION, "沙发", "客厅")
        val item = repository.observe(DictionaryKind.LOCATION).first().single()

        assertEquals(item.id, repository.findByName(DictionaryKind.LOCATION, " 沙发 ")?.id)
        assertNull("编辑自己时不算重名", repository.findByName(DictionaryKind.LOCATION, "沙发", excludeId = item.id))

        repository.delete(DictionaryKind.LOCATION, item.id)
        val deleted = repository.findByName(DictionaryKind.LOCATION, "沙发")
        assertEquals("已删除项同样算重名，界面据此提示恢复", item.id, deleted?.id)
        assertTrue(deleted!!.deleted)
    }

    @Test
    fun purge_removes_row_and_writes_tombstone() = runBlocking {
        repository.add(DictionaryKind.TOY, "跳蛋", "入门款")
        val item = repository.observe(DictionaryKind.TOY).first().single()
        repository.delete(DictionaryKind.TOY, item.id)

        repository.purge(DictionaryKind.TOY, item.id)

        assertTrue("物理删除后连全量表里也没有", db.toyDao().all().isEmpty())
        assertTrue(repository.observeIncludingDeleted(DictionaryKind.TOY).first().isEmpty())
        val tomb = db.tombstoneDao().all().single()
        assertEquals("toy", tomb.tableName)
        assertEquals(item.id, tomb.rowId)
    }

    @Test
    fun restore_makes_deleted_item_visible_again() = runBlocking {
        repository.add(DictionaryKind.POSITION, "侧入", "临时", Intensity.LOW)
        val item = repository.observe(DictionaryKind.POSITION).first().single()
        repository.delete(DictionaryKind.POSITION, item.id)

        assertTrue(repository.observe(DictionaryKind.POSITION).first().isEmpty())
        assertEquals(1, repository.observeDeleted(DictionaryKind.POSITION).first().size)

        repository.restore(DictionaryKind.POSITION, item.id)

        val restored = repository.observe(DictionaryKind.POSITION).first().single()
        assertEquals("侧入", restored.name)
        assertEquals("临时", restored.note)
        assertTrue("恢复后不再出现在已删除区", repository.observeDeleted(DictionaryKind.POSITION).first().isEmpty())
    }

    @Test
    fun reorder_rewrites_flat_sort_order() = runBlocking {
        listOf("甲", "乙", "丙").forEach { repository.add(DictionaryKind.POSITION, it, null) }
        val items = repository.observe(DictionaryKind.POSITION).first()
        val reversed = items.reversed().map(DictionaryItem::id)

        repository.reorder(DictionaryKind.POSITION, reversed)

        assertEquals(listOf("丙", "乙", "甲"), repository.observe(DictionaryKind.POSITION).first().map { it.name })
        assertEquals(listOf(1, 2, 3), reversed.map { db.positionDao().byId(it)?.sortOrder })
    }

    @Test
    fun builtin_location_can_be_edited_and_deleted_like_any_other() = runBlocking {
        db.locationDao().upsert(
            LocationEntity(id = "loc_builtin", name = "卧室", isBuiltin = true, sortOrder = 1, createdAt = 1, updatedAt = 1),
        )
        repository.add(DictionaryKind.LOCATION, "自家沙发", "新买的")
        assertEquals(
            listOf("卧室", "自家沙发"),
            repository.observe(DictionaryKind.LOCATION).first().map { it.name },
        )

        repository.update(DictionaryKind.LOCATION, "loc_builtin", "卧室（改）", "带窗帘")
        val edited = repository.observe(DictionaryKind.LOCATION).first().first { it.id == "loc_builtin" }
        assertEquals("卧室（改）", edited.name)
        assertEquals("带窗帘", edited.note)

        repository.delete(DictionaryKind.LOCATION, "loc_builtin")
        assertEquals(
            listOf("自家沙发"),
            repository.observe(DictionaryKind.LOCATION).first().map { it.name },
        )
    }

    @Test
    fun update_toy_changes_name_and_description() = runBlocking {
        repository.add(DictionaryKind.TOY, "跳蛋", "入门款")
        val item = repository.observe(DictionaryKind.TOY).first().single()

        repository.update(DictionaryKind.TOY, item.id, "跳蛋 Pro", "可遥控", Intensity.MEDIUM)

        val after = repository.observe(DictionaryKind.TOY).first().single()
        assertEquals("跳蛋 Pro", after.name)
        assertEquals("可遥控", after.note)
    }

    @Test
    fun deleted_builtin_locations_are_not_reseeded() = runBlocking {
        Seeder.seed(db)
        val seeded = repository.observe(DictionaryKind.LOCATION).first()
        assertEquals(23, seeded.size)

        seeded.forEach { repository.delete(DictionaryKind.LOCATION, it.id) }
        assertTrue(repository.observe(DictionaryKind.LOCATION).first().isEmpty())

        Seeder.seed(db)

        assertTrue(
            "用户删掉的内置项不应被重新种回来",
            repository.observe(DictionaryKind.LOCATION).first().isEmpty(),
        )
    }

    @Test
    fun purged_builtin_sex_type_is_not_reseeded() = runBlocking {
        Seeder.seed(db)
        repository.delete(DictionaryKind.SEX_TYPE, "st_012")
        repository.purge(DictionaryKind.SEX_TYPE, "st_012")

        Seeder.seed(db)

        assertTrue(
            "彻底删除的内置类型不应被种子复活（否则墓碑会让多端永久分叉）",
            repository.observe(DictionaryKind.SEX_TYPE).first().none { it.id == "st_012" },
        )
    }

    @Test
    fun purged_all_locations_are_not_reseeded() = runBlocking {
        Seeder.seed(db)
        val seeded = repository.observe(DictionaryKind.LOCATION).first()
        assertEquals(23, seeded.size)
        seeded.forEach {
            repository.delete(DictionaryKind.LOCATION, it.id)
            repository.purge(DictionaryKind.LOCATION, it.id)
        }

        Seeder.seed(db)

        assertTrue(
            "整表被彻底删除后也不应重种",
            repository.observe(DictionaryKind.LOCATION).first().isEmpty(),
        )
    }

    @Test
    fun restore_appends_to_end_and_applies_new_values() = runBlocking {
        listOf("甲", "乙", "丙").forEach { repository.add(DictionaryKind.POSITION, it, null) }
        val target = repository.observe(DictionaryKind.POSITION).first().first { it.name == "甲" }
        repository.delete(DictionaryKind.POSITION, target.id)

        repository.restoreAndUpdate(
            DictionaryKind.POSITION,
            target.id,
            "甲（改）",
            "恢复时的新备注",
            Intensity.HIGH,
        )

        val items = repository.observe(DictionaryKind.POSITION).first()
        assertEquals("恢复项追加到末尾", listOf("乙", "丙", "甲（改）"), items.map { it.name })
        assertEquals("恢复时填的新备注要保留", "恢复时的新备注", items.last().note)
        assertEquals(Intensity.HIGH, items.last().intensity)
        assertTrue(
            "恢复后不再出现在已删除区",
            repository.observeDeleted(DictionaryKind.POSITION).first().isEmpty(),
        )
    }

    @Test
    fun update_and_restore_keep_the_record_links() = runBlocking {
        repository.add(DictionaryKind.SEX_TYPE, "素股", null)
        val item = repository.observe(DictionaryKind.SEX_TYPE).first().single()
        val now = 1_700_000_000_000L
        db.recordDao().upsert(RecordEntity(id = "r1", timestamp = now, createdAt = now, updatedAt = now))
        db.recordDao().linkSexType(RecordSexTypeCrossRef(recordId = "r1", sexTypeId = item.id))

        repository.update(DictionaryKind.SEX_TYPE, item.id, "素股改", "新备注")
        assertEquals("改名不能把记录关联级联删掉", listOf(item.id), db.recordDao().sexTypeIds("r1"))

        repository.delete(DictionaryKind.SEX_TYPE, item.id)
        repository.restore(DictionaryKind.SEX_TYPE, item.id)
        assertEquals("恢复后记录关联仍在", listOf(item.id), db.recordDao().sexTypeIds("r1"))
    }
}
