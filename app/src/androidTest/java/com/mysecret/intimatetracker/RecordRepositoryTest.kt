package com.mysecret.intimatetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.db.PositionEntity
import com.mysecret.intimatetracker.data.db.Seeder
import com.mysecret.intimatetracker.data.repo.RecordDraft
import com.mysecret.intimatetracker.data.repo.RecordRepository
import com.mysecret.intimatetracker.data.sync.SyncTrigger
import com.mysecret.intimatetracker.domain.model.Initiator
import com.mysecret.intimatetracker.domain.model.Protection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RecordRepository
    private val day = 86_400_000L
    private val base = 1_700_000_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
        repository = RecordRepository(db, SyncTrigger { })
        Seeder.seed(db)
        db.partnerDao().upsert(
            PartnerEntity(
                id = "p1",
                name = "伴侣一",
                createdAt = base,
                updatedAt = base,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun save_writes_record_and_all_cross_refs() = runBlocking {
        val id = repository.save(
            RecordDraft(
                timestamp = base,
                icon = "🔥",
                durationMinutes = 30,
                satisfaction = 4,
                initiator = Initiator.ME,
                protection = Protection.YES,
                myOrgasmCount = 1,
                partnerOrgasmCount = 2,
                partnerIds = listOf("p1"),
                sexTypeIds = listOf("st_001"),
                positionIds = listOf("pos_003"),
                locationIds = listOf("loc_001"),
            ),
        )

        val saved = repository.find(id)
        assertEquals(30, saved?.durationMinutes)
        assertEquals(4, saved?.satisfaction)
        assertEquals(1, saved?.myOrgasmCount)
        assertEquals(2, saved?.partnerOrgasmCount)
        assertEquals(false, saved?.isSolo)

        assertEquals(1, db.recordDao().partnerLinkCount("p1"))
        val rows = repository.observeBetween(base - day, base + day).first()
        assertEquals(1, rows.size)
        assertEquals(listOf("阴道性交"), rows[0].typeList)
        assertEquals(listOf("卧室"), rows[0].locationList)
        assertEquals(listOf("传教士"), rows[0].positionList)
        assertEquals("🔥", rows[0].emoji)
    }

    @Test
    fun saving_again_replaces_links_instead_of_duplicating() = runBlocking {
        val id = repository.save(
            RecordDraft(timestamp = base, partnerIds = listOf("p1"), sexTypeIds = listOf("st_001")),
        )
        repository.save(
            RecordDraft(id = id, timestamp = base, partnerIds = emptyList(), sexTypeIds = listOf("st_002")),
        )

        assertEquals(0, db.recordDao().partnerLinkCount("p1"))
        val rows = repository.observeBetween(base - day, base + day).first()
        assertEquals(listOf("口交"), rows[0].typeList)
        assertTrue(rows[0].record.isSolo)
    }

    @Test
    fun calendar_entry_falls_back_to_type_emoji() = runBlocking {
        repository.save(RecordDraft(timestamp = base, sexTypeIds = listOf("st_004")))

        val entries = repository.observeCalendar(base - day, base + day).first()
        assertEquals(1, entries.size)
        assertEquals(listOf("🖐️"), entries[0].icons)
    }

    @Test
    fun loadDraft_returns_all_selections_for_editing() = runBlocking {
        val id = repository.save(
            RecordDraft(
                timestamp = base,
                icon = "🔥",
                durationMinutes = 30,
                satisfaction = 4,
                partnerIds = listOf("p1"),
                sexTypeIds = listOf("st_001"),
                positionIds = listOf("pos_003"),
                locationIds = listOf("loc_001"),
            ),
        )

        val draft = repository.loadDraft(id)

        assertNotNull(draft)
        assertEquals("🔥", draft!!.icon)
        assertEquals(30, draft.durationMinutes)
        assertEquals(4, draft.satisfaction)
        assertEquals(listOf("p1"), draft.partnerIds)
        assertEquals(listOf("st_001"), draft.sexTypeIds)
        assertEquals(listOf("pos_003"), draft.positionIds)
        assertEquals(listOf("loc_001"), draft.locationIds)
    }

    @Test
    fun rhythm_reports_days_since_longest_gap_and_needs_three_samples() = runBlocking {
        val now = (base / day) * day + 12 * 3_600_000L
        repository.save(RecordDraft(timestamp = now - 2 * day))
        repository.save(RecordDraft(timestamp = now - 6 * day))
        repository.save(RecordDraft(timestamp = now - 8 * day))
        repository.save(RecordDraft(timestamp = now - 12 * day))

        val stats = repository.rhythm(now = now)
        assertEquals(2, stats.daysSinceLast)
        assertEquals(4, stats.longestGapDays)
        assertEquals(3..5, stats.usualRange)
    }

    @Test
    fun rhythm_is_empty_when_no_records() = runBlocking {
        val stats = repository.rhythm(now = base)
        assertNull(stats.daysSinceLast)
        assertNull(stats.longestGapDays)
        assertNull(stats.usualRange)
    }
}
