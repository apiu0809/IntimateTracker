package com.mysecret.intimatetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.LocationEntity
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.db.PositionEntity
import com.mysecret.intimatetracker.data.db.RecordEntity
import com.mysecret.intimatetracker.data.db.RecordLocationCrossRef
import com.mysecret.intimatetracker.data.db.RecordPartnerCrossRef
import com.mysecret.intimatetracker.data.db.RecordPositionCrossRef
import com.mysecret.intimatetracker.data.db.RecordSexTypeCrossRef
import com.mysecret.intimatetracker.data.db.Seeder
import com.mysecret.intimatetracker.data.repo.StatsRepository
import com.mysecret.intimatetracker.domain.model.Initiator
import com.mysecret.intimatetracker.domain.model.Protection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * 黄金用例：用 17 条固定记录构造数据，逐项断言统计口径。
 * 基准数值：活动17 / 伴侣4 / 自慰10 / 平均满意度4.3 / 平均时长15 / 总时长215 /
 * 防护100% / 高潮20 / 伴侣高潮10 / 平均1.2 / 伴侣平均1.4（本 fixture 为 7 条伴侣记录）。
 */
@RunWith(AndroidJUnit4::class)
class StatsGoldenTest {

    private lateinit var db: AppDatabase
    private lateinit var stats: StatsRepository

    private val day = 86_400_000L
    private val base = 1_760_000_000_000L // 2025-10 左右，只用于范围查询

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        stats = StatsRepository(db)
        Seeder.seed(db)
        listOf(
            "pos_001" to "正面女上位",
            "pos_002" to "背身女上位",
            "pos_003" to "传教士",
            "pos_004" to "跪姿后入",
        ).forEachIndexed { index, (id, name) ->
            db.positionDao().upsert(
                PositionEntity(
                    id = id,
                    name = name,
                    sortOrder = index,
                    createdAt = base,
                    updatedAt = base,
                ),
            )
        }
        seedPartners()
        seedCustomLocations()
        seedRecords()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedPartners() = runBlocking {
        listOf("p1" to "馨儿", "p2" to "丁丁", "p3" to "幼辞", "p4" to "于晚晚").forEach { (id, name) ->
            db.partnerDao().upsert(PartnerEntity(id = id, name = name, createdAt = base, updatedAt = base))
        }
    }

    private fun seedCustomLocations() = runBlocking {
        db.sexTypeDao().upsert(com.mysecret.intimatetracker.data.db.SexTypeEntity(id = "st_milk", name = "乳交", createdAt = base, updatedAt = base))
        db.sexTypeDao().upsert(com.mysecret.intimatetracker.data.db.SexTypeEntity(id = "st_010", name = "被动", createdAt = base, updatedAt = base))
        db.locationDao().upsert(LocationEntity(id = "loc_sofa", name = "沙发", createdAt = base, updatedAt = base))
        db.locationDao().upsert(LocationEntity(id = "loc_window", name = "窗前", createdAt = base, updatedAt = base))
    }

    private fun seedRecords() = runBlocking {
        // ---- 10 条单人记录：自慰；厕所7 / 卧室1 / 无地点2 ----
        repeat(10) { index ->
            val id = "solo-$index"
            val locations = when {
                index < 5 -> listOf("loc_004") // 厕所 5
                index < 7 -> listOf("loc_004", "loc_001") // 厕所+卧室 2
                else -> listOf("loc_001") // 卧室 3
            }
            insertRecord(
                id = id,
                timestamp = base + index * day,
                isSolo = true,
                duration = 10,
                satisfaction = 4,
                myOrgasm = 0,
                sexTypes = listOf("st_004"), // 自慰
                locations = locations,
            )
        }

        // ---- 7 条伴侣记录 ----
        // 地点：卧室7；另有一条加沙发、一条加窗前、一条加客厅、一条加电影院
        val partnerLocations = listOf(
            listOf("loc_001", "loc_005"), // 卧室 + 客厅
            listOf("loc_001", "loc_sofa"), // 卧室 + 沙发
            listOf("loc_001", "loc_window"), // 卧室 + 窗前
            listOf("loc_001", "loc_015"), // 卧室 + 电影院
            listOf("loc_001"),
            emptyList(),
            emptyList(),
        )
        val durations = listOf(15, 25, 35, 40, null, null, null)
        val satisfactions = listOf(5, 5, 5, 5, null, null, null)
        val partnerOrgasms = listOf(2, 2, 2, 2, 1, 1, 0)
        val initiators = listOf(Initiator.ME, Initiator.ME, Initiator.ME, Initiator.ME, Initiator.ME, Initiator.ME, Initiator.PARTNER)
        // 类型：阴道性交7 / 口交6 / 乳交2 / 手交1 / 指交1 / 被动1
        val sexTypes = listOf(
            listOf("st_001", "st_002", "st_003"), // 阴道性交 口交 手交
            listOf("st_001", "st_002", "st_005"), // 指交
            listOf("st_001", "st_002", "st_milk"), // 乳交
            listOf("st_001", "st_002", "st_milk"),
            listOf("st_001", "st_002", "st_010"), // 被动
            listOf("st_001", "st_002"),
            listOf("st_001"),
        )
        val positions = listOf(
            listOf("pos_001", "pos_003"),
            listOf("pos_001", "pos_003"),
            listOf("pos_001", "pos_004"),
            listOf("pos_001", "pos_004"),
            listOf("pos_001", "pos_002"),
            listOf("pos_003"),
            emptyList(),
        )

        repeat(7) { index ->
            insertRecord(
                id = "p-$index",
                timestamp = base + (10 + index) * day,
                isSolo = false,
                duration = durations[index],
                satisfaction = satisfactions[index],
                myOrgasm = listOf(4, 3, 3, 3, 3, 2, 2)[index], // 合计 20
                partnerOrgasm = partnerOrgasms[index],
                initiator = initiators[index],
                protection = Protection.YES,
                partnerId = listOf("p1", "p1", "p1", "p1", "p2", "p3", "p4")[index],
                sexTypes = sexTypes[index],
                locations = partnerLocations[index],
                positions = positions[index],
            )
        }
    }

    private fun insertRecord(
        id: String,
        timestamp: Long,
        isSolo: Boolean,
        duration: Int?,
        satisfaction: Int?,
        myOrgasm: Int,
        partnerOrgasm: Int = 0,
        initiator: Initiator? = null,
        protection: Protection? = null,
        partnerId: String? = null,
        sexTypes: List<String> = emptyList(),
        locations: List<String> = emptyList(),
        positions: List<String> = emptyList(),
    ) = runBlocking {
        db.recordDao().upsert(
            RecordEntity(
                id = id,
                timestamp = timestamp,
                durationMinutes = duration,
                satisfaction = satisfaction,
                initiator = initiator,
                protectionUsed = protection,
                myOrgasmCount = myOrgasm,
                partnerOrgasmCount = partnerOrgasm,
                isSolo = isSolo,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        partnerId?.let { db.recordDao().linkPartner(RecordPartnerCrossRef(recordId = id, partnerId = it)) }
        sexTypes.forEach { db.recordDao().linkSexType(RecordSexTypeCrossRef(recordId = id, sexTypeId = it)) }
        locations.forEach { db.recordDao().linkLocation(RecordLocationCrossRef(recordId = id, locationId = it)) }
        positions.forEach { db.recordDao().linkPosition(RecordPositionCrossRef(recordId = id, positionId = it)) }
    }

    private fun snapshot() = runBlocking { stats.load(0L, Long.MAX_VALUE, null) }

    @Test
    fun overview_numbers_match_reference_screenshot() {
        val s = snapshot()

        assertEquals(17, s.total)
        assertEquals(4, s.partnerCount)
        assertEquals(10, s.solo)
        assertEquals(4.3, s.avgSatisfaction, 0.05)
        assertEquals(15, s.avgDuration)
        assertEquals(215, s.totalDuration)
        assertEquals(100.0, s.protectionRate, 0.01)
        assertEquals(20, s.myOrgasm)
        assertEquals(10, s.partnerOrgasm)
        assertEquals(1.2, s.avgMyOrgasm, 0.05)
        assertEquals(1.4, s.avgPartnerOrgasm, 0.05)
    }

    @Test
    fun type_percentages_use_all_records_as_denominator() {
        val types = snapshot().types.associateBy { it.name }

        assertEquals(10, types.getValue("自慰").count)
        assertEquals(59, types.getValue("自慰").percent.roundToInt())
        assertEquals(7, types.getValue("阴道性交").count)
        assertEquals(41, types.getValue("阴道性交").percent.roundToInt())
        assertEquals(6, types.getValue("口交").count)
        assertEquals(35, types.getValue("口交").percent.roundToInt())
    }

    @Test
    fun location_percentages_use_filled_records_as_denominator() {
        val locations = snapshot().locations.associateBy { it.name }

        assertEquals(10, locations.getValue("卧室").count)
        assertEquals(67, locations.getValue("卧室").percent.roundToInt())
        assertEquals(7, locations.getValue("厕所").count)
        assertEquals(47, locations.getValue("厕所").percent.roundToInt())
    }

    @Test
    fun position_percentages_use_filled_records_as_denominator() {
        val positions = snapshot().positions.associateBy { it.name }

        assertEquals(5, positions.getValue("正面女上位").count)
        assertEquals(83, positions.getValue("正面女上位").percent.roundToInt())
        assertEquals(3, positions.getValue("传教士").count)
        assertEquals(50, positions.getValue("传教士").percent.roundToInt())
    }

    @Test
    fun initiator_split_matches_screenshot() {
        val initiators = snapshot().initiators.associateBy { it.name }

        assertEquals(6, initiators.getValue("我").count)
        assertEquals(86, initiators.getValue("我").percent.roundToInt())
        assertEquals(1, initiators.getValue("伴侣").count)
        assertEquals(14, initiators.getValue("伴侣").percent.roundToInt())
    }

    @Test
    fun weekday_distribution_sums_to_total() {
        val s = snapshot()

        assertEquals(17, s.weekdays.sum())
        assertEquals(7, s.weekdays.size)
    }
}
