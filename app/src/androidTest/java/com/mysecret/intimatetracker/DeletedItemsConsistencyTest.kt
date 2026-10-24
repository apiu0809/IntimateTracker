package com.mysecret.intimatetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.LocationEntity
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.db.RecordListRow
import com.mysecret.intimatetracker.data.db.SexTypeEntity
import com.mysecret.intimatetracker.data.db.ToyEntity
import com.mysecret.intimatetracker.data.repo.RecordDraft
import com.mysecret.intimatetracker.data.repo.RecordRepository
import com.mysecret.intimatetracker.data.repo.StatsRepository
import com.mysecret.intimatetracker.data.sync.SyncTrigger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 软删后的统一口径（历史真相）：
 * - 统计：已删字典项仍参与排行并标注「（已删除）」，分母按「有该关联」计算
 * - 记录列表：已删的类型 / 地点 / 姿势 / 伴侣照常显示并标注「（已删除）」，玩具数照常计数
 * - 伴侣人数 / 排行：排除已删与已归档伴侣
 */
@RunWith(AndroidJUnit4::class)
class DeletedItemsConsistencyTest {

    private lateinit var db: AppDatabase
    private lateinit var stats: StatsRepository
    private lateinit var records: RecordRepository

    private val base = 1_700_000_000_000L
    private val day = 86_400_000L
    private val now = 1_700_900_000_000L

    private lateinit var recordWithAll: String
    private lateinit var recordWithLocation: String
    private lateinit var recordWithToy: String

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        stats = StatsRepository(db)
        records = RecordRepository(db, SyncTrigger { })

        db.partnerDao().upsert(PartnerEntity(id = "p1", name = "伴侣一", createdAt = base, updatedAt = base))
        db.sexTypeDao().upsert(SexTypeEntity(id = "st_test", name = "口交", createdAt = base, updatedAt = base))
        db.locationDao().upsert(LocationEntity(id = "loc_test", name = "沙发", sortOrder = 1, createdAt = base, updatedAt = base))
        db.toyDao().upsert(ToyEntity(id = "toy_test", name = "跳蛋", sortOrder = 1, createdAt = base, updatedAt = base))

        // r1：伴侣 + 类型 + 地点 + 玩具；r2：只有地点；r3：只有玩具
        recordWithAll = records.save(
            RecordDraft(
                timestamp = base,
                partnerIds = listOf("p1"),
                sexTypeIds = listOf("st_test"),
                locationIds = listOf("loc_test"),
                toyIds = listOf("toy_test"),
            ),
        )
        recordWithLocation = records.save(RecordDraft(timestamp = base + day, locationIds = listOf("loc_test")))
        recordWithToy = records.save(RecordDraft(timestamp = base + 2 * day, toyIds = listOf("toy_test")))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun snapshot() = runBlocking { stats.load(0L, Long.MAX_VALUE, null) }

    private fun allRows(): List<RecordListRow> = runBlocking {
        records.observeBetween(0L, Long.MAX_VALUE).first()
    }

    private fun rowOf(id: String): RecordListRow = allRows().first { it.record.id == id }

    @Test
    fun deleted_partner_stays_in_records_but_leaves_stats() = runBlocking {
        assertEquals(3, snapshot().total)
        assertEquals(1, snapshot().partnerCount)
        assertEquals(1, snapshot().partnerActivities.size)

        db.partnerDao().softDelete("p1", now)

        val after = snapshot()
        assertEquals("记录本身不受影响", 3, after.total)
        assertEquals("伴侣人数与排行口径一致", 0, after.partnerCount)
        assertTrue(after.partnerActivities.isEmpty())
        assertEquals("记录列表按历史真相展示已删伴侣", listOf("伴侣一（已删除）"), rowOf(recordWithAll).partnerList)
    }

    @Test
    fun archived_partner_leaves_stats_but_stays_in_records_without_marker() = runBlocking {
        assertEquals(1, snapshot().partnerCount)

        db.partnerDao().setArchived("p1", true, now)

        val after = snapshot()
        assertEquals("伴侣人数排除已归档", 0, after.partnerCount)
        assertTrue(after.partnerActivities.isEmpty())
        assertEquals("记录仍显示归档伴侣（未删除，不加标记）", listOf("伴侣一"), rowOf(recordWithAll).partnerList)
        assertEquals(3, after.total)
    }

    @Test
    fun deleted_type_stays_in_stats_and_record_rows_with_marker() {
        assertEquals(1, snapshot().types.single().count)

        runBlocking { db.sexTypeDao().softDelete("st_test", now) }

        val after = snapshot()
        assertEquals("已删类型仍参与统计", 1, after.types.size)
        assertEquals("口交（已删除）", after.types.single().name)
        assertEquals(1, after.types.single().count)
        assertEquals("类型分母仍是全部记录", 3, after.filledTypes)
        assertEquals(33.3, after.types.single().percent, 0.1)
        assertEquals("记录行同时展示已删类型", listOf("口交（已删除）"), rowOf(recordWithAll).typeList)
    }

    @Test
    fun deleted_location_stays_in_stats_and_record_rows_with_marker() {
        val before = snapshot()
        assertEquals(2, before.filledLocations)
        assertEquals(100.0, before.locations.single().percent, 0.01)

        runBlocking { db.locationDao().softDelete("loc_test", now) }

        val after = snapshot()
        assertEquals("已删地点仍参与统计", 1, after.locations.size)
        assertEquals("沙发（已删除）", after.locations.single().name)
        assertEquals(2, after.locations.single().count)
        assertEquals("分母按「有地点关联」算，不因删除缩水", 2, after.filledLocations)
        assertEquals(100.0, after.locations.single().percent, 0.01)
        assertEquals("记录行同时展示已删地点", listOf("沙发（已删除）"), rowOf(recordWithAll).locationList)
    }

    @Test
    fun deleted_toy_stays_in_stats_and_record_toy_count() {
        assertEquals(2, snapshot().filledToys)
        assertEquals(
            mapOf(recordWithAll to 1, recordWithLocation to 0, recordWithToy to 1),
            allRows().associate { it.record.id to it.toyCount },
        )

        runBlocking { db.toyDao().softDelete("toy_test", now) }

        val after = snapshot()
        assertEquals("已删玩具仍参与统计", 1, after.toys.size)
        assertEquals("跳蛋（已删除）", after.toys.single().name)
        assertEquals(2, after.toys.single().count)
        assertEquals(2, after.filledToys)
        assertEquals("记录列表的玩具数同样按历史真相保留", 1, rowOf(recordWithAll).toyCount)
        assertEquals(1, rowOf(recordWithToy).toyCount)
        val softDeleted = runBlocking { db.toyDao().all().single { it.id == "toy_test" }.deletedAt }
        assertNotNull("软删保留行本身", softDeleted)
    }
}
