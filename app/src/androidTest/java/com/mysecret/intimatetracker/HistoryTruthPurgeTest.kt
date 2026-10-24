package com.mysecret.intimatetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.LocationEntity
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.db.PositionEntity
import com.mysecret.intimatetracker.data.db.SexTypeEntity
import com.mysecret.intimatetracker.data.db.ToyEntity
import com.mysecret.intimatetracker.data.repo.DictionaryKind
import com.mysecret.intimatetracker.data.repo.DictionaryRepository
import com.mysecret.intimatetracker.data.repo.PartnerRepository
import com.mysecret.intimatetracker.data.repo.RecordDraft
import com.mysecret.intimatetracker.data.repo.RecordRepository
import com.mysecret.intimatetracker.data.repo.StatsRepository
import com.mysecret.intimatetracker.data.sync.SyncTrigger
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
 * 「彻底删除」也必须守住历史真相：
 *
 * 伴侣 / 字典项被物理删除（行没了、墓碑写了）之后，
 * - 记录与它的关联不能消失（关联表只有指向 record 的外键）；
 * - 名字用关联快照还原，并标「（已删除）」；
 * - 统计排行、分母、玩具数都不缩水；
 * - 再编辑这条记录时，关联照常回写。
 */
@RunWith(AndroidJUnit4::class)
class HistoryTruthPurgeTest {

    private lateinit var db: AppDatabase
    private lateinit var partners: PartnerRepository
    private lateinit var dictionary: DictionaryRepository
    private lateinit var records: RecordRepository
    private lateinit var stats: StatsRepository

    private val base = 1_700_000_000_000L
    private lateinit var recordId: String

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        partners = PartnerRepository(db.partnerDao(), db, SyncTrigger { })
        dictionary = DictionaryRepository(db, SyncTrigger { })
        records = RecordRepository(db, SyncTrigger { })
        stats = StatsRepository(db)

        db.partnerDao().upsert(PartnerEntity(id = "p1", name = "伴侣一", createdAt = base, updatedAt = base))
        db.sexTypeDao().upsert(SexTypeEntity(id = "st_test", name = "口交", createdAt = base, updatedAt = base))
        db.positionDao().upsert(PositionEntity(id = "pos_test", name = "侧入", createdAt = base, updatedAt = base))
        db.locationDao().upsert(LocationEntity(id = "loc_test", name = "沙发", createdAt = base, updatedAt = base))
        db.toyDao().upsert(ToyEntity(id = "toy_test", name = "跳蛋", createdAt = base, updatedAt = base))

        recordId = records.save(
            RecordDraft(
                timestamp = base,
                partnerIds = listOf("p1"),
                sexTypeIds = listOf("st_test"),
                positionIds = listOf("pos_test"),
                locationIds = listOf("loc_test"),
                toyIds = listOf("toy_test"),
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row() = runBlocking { records.observeBetween(0L, Long.MAX_VALUE).first().single() }

    private fun snapshot() = runBlocking { stats.load(0L, Long.MAX_VALUE, null) }

    @Test
    fun purged_partner_keeps_link_and_name_but_leaves_stats() = runBlocking {
        partners.delete("p1")
        partners.purge("p1")

        assertNull("伴侣行已被物理删除", db.partnerDao().findIncludingDeleted("p1"))
        assertEquals("墓碑照常写入", "partner", db.tombstoneDao().all().single().tableName)

        val row = row()
        assertEquals("关联 id 不变", listOf("p1"), row.partnerIdList)
        assertEquals("名字用快照还原并标注", listOf("伴侣一（已删除）"), row.partnerList)
        assertEquals("伴侣人数口径仍排除已删除", 0, snapshot().partnerCount)

        val link = db.recordDao().partnerLinkNames(recordId).single()
        assertEquals("伴侣一", link.name)
        assertTrue(link.deleted)
    }

    @Test
    fun purged_sex_type_stays_in_ranking_and_record_row() = runBlocking {
        dictionary.delete(DictionaryKind.SEX_TYPE, "st_test")
        dictionary.purge(DictionaryKind.SEX_TYPE, "st_test")

        assertNull(db.sexTypeDao().byIdIncludingDeleted("st_test"))

        val after = snapshot()
        assertEquals("已彻底删除的类型仍参与排行", 1, after.types.size)
        assertEquals("口交（已删除）", after.types.single().name)
        assertEquals(1, after.types.single().count)
        assertEquals("类型分母不因删除缩水", 1, after.filledTypes)
        assertEquals(listOf("口交（已删除）"), row().typeList)
    }

    @Test
    fun purged_location_position_and_toy_keep_history() = runBlocking {
        dictionary.purge(DictionaryKind.LOCATION, "loc_test")
        dictionary.purge(DictionaryKind.POSITION, "pos_test")
        dictionary.purge(DictionaryKind.TOY, "toy_test")

        assertNull(db.locationDao().byIdIncludingDeleted("loc_test"))
        assertNull(db.positionDao().byIdIncludingDeleted("pos_test"))
        assertNull(db.toyDao().byIdIncludingDeleted("toy_test"))

        val row = row()
        assertEquals(listOf("沙发（已删除）"), row.locationList)
        assertEquals(listOf("侧入（已删除）"), row.positionList)
        assertEquals("玩具数照常计数", 1, row.toyCount)

        val after = snapshot()
        assertEquals("沙发（已删除）", after.locations.single().name)
        assertEquals("侧入（已删除）", after.positions.single().name)
        assertEquals("跳蛋（已删除）", after.toys.single().name)
        assertEquals(1, after.locations.single().count)
        assertEquals(1, after.positions.single().count)
        assertEquals(1, after.toys.single().count)
        assertEquals(1, after.filledLocations)
        assertEquals(1, after.filledPositions)
        assertEquals(1, after.filledToys)
    }

    @Test
    fun editing_after_purge_keeps_the_associations() = runBlocking {
        partners.purge("p1")
        dictionary.purge(DictionaryKind.SEX_TYPE, "st_test")

        val draft = records.loadDraft(recordId)
        assertTrue("草稿仍带着已彻底删除的 id", draft!!.partnerIds.contains("p1"))
        assertTrue(draft.sexTypeIds.contains("st_test"))

        records.save(draft.copy(notes = "改个备注"))

        val row = row()
        assertEquals(listOf("伴侣一（已删除）"), row.partnerList)
        assertEquals(listOf("口交（已删除）"), row.typeList)
        assertEquals(1, db.recordDao().partnerLinkCount("p1"))
    }

    @Test
    fun rename_refreshes_snapshot_before_purge() = runBlocking {
        dictionary.update(DictionaryKind.SEX_TYPE, "st_test", "口爱")

        // 改名当刻：展示用父行当前名字（无标记）
        assertEquals(listOf("口爱"), row().typeList)

        dictionary.purge(DictionaryKind.SEX_TYPE, "st_test")

        // 彻底删除后：快照已是最后一次已知名字，而不是旧名
        assertEquals(listOf("口爱（已删除）"), row().typeList)
        assertEquals("口爱（已删除）", snapshot().types.single().name)
    }

    @Test
    fun purged_items_are_exposed_as_ghost_options_for_the_edit_form() = runBlocking {
        partners.purge("p1")
        dictionary.purge(DictionaryKind.SEX_TYPE, "st_test")

        val links = db.recordDao().observeLinkRows(recordId).first()
        val ghostPartner = links.single { it.kind == "partner" && it.linkId == "p1" }
        val ghostType = links.single { it.kind == "sex_type" && it.linkId == "st_test" }
        assertEquals("伴侣一", ghostPartner.label)
        assertEquals("口交", ghostType.label)
    }
}
