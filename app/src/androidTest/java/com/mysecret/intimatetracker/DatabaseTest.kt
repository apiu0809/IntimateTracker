package com.mysecret.intimatetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.db.RecordEntity
import com.mysecret.intimatetracker.data.db.RecordPartnerCrossRef
import com.mysecret.intimatetracker.data.db.Seeder
import com.mysecret.intimatetracker.domain.model.Gender
import com.mysecret.intimatetracker.domain.model.RelationshipType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seed_writes_9_types_23_locations_0_positions() = runBlocking {
        Seeder.seed(db)

        assertEquals(9, db.sexTypeDao().count())
        assertEquals(23, db.locationDao().count())
        assertEquals(0, db.positionDao().count())
        assertEquals(9, db.sexTypeDao().countBuiltin())
        assertEquals(23, db.locationDao().countBuiltin())
        assertEquals(0, db.positionDao().countBuiltin())
    }

    @Test
    fun seed_is_idempotent() = runBlocking {
        Seeder.seed(db)
        Seeder.seed(db)

        assertEquals(9, db.sexTypeDao().count())
        assertEquals(23, db.locationDao().count())
        assertEquals(0, db.positionDao().count())
    }

    @Test
    fun hard_deleting_partner_cascades_to_cross_ref() = runBlocking {
        val now = System.currentTimeMillis()
        db.partnerDao().upsert(
            PartnerEntity(
                id = "p1",
                name = "测试伴侣",
                gender = Gender.FEMALE,
                relationshipType = RelationshipType.CASUAL,
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.recordDao().upsert(
            RecordEntity(id = "r1", timestamp = now, createdAt = now, updatedAt = now),
        )
        db.recordDao().linkPartner(RecordPartnerCrossRef(recordId = "r1", partnerId = "p1"))

        assertEquals(1, db.recordDao().partnerLinkCount("p1"))

        db.partnerDao().hardDelete("p1")

        assertEquals(0, db.recordDao().partnerLinkCount("p1"))
        assertEquals(0, db.partnerDao().countActive())
    }

    @Test
    fun soft_deleted_partner_disappears_from_active_flow() = runBlocking {
        val now = System.currentTimeMillis()
        db.partnerDao().upsert(
            PartnerEntity(id = "p2", name = "已删除", createdAt = now, updatedAt = now),
        )
        assertEquals(1, db.partnerDao().observeActive().first().size)

        db.partnerDao().softDelete("p2", now)

        assertEquals(0, db.partnerDao().observeActive().first().size)
        assertEquals(0, db.partnerDao().countActive())
    }

    @Test
    fun archiving_keeps_partner_but_hides_from_active_list() = runBlocking {
        val now = System.currentTimeMillis()
        db.partnerDao().upsert(
            PartnerEntity(id = "p3", name = "已归档", createdAt = now, updatedAt = now),
        )

        db.partnerDao().setArchived("p3", true, now)

        assertEquals(0, db.partnerDao().observeActive().first().size)
        assertEquals(1, db.partnerDao().observeArchived().first().size)
        assertEquals(1, db.partnerDao().countActive())
        assertNotNull(db.partnerDao().find("p3"))
    }

    @Test
    fun soft_delete_keeps_history_link_but_hides_partner() = runBlocking {
        val now = System.currentTimeMillis()
        db.partnerDao().upsert(PartnerEntity(id = "p9", name = "软删", createdAt = now, updatedAt = now))
        db.recordDao().upsert(RecordEntity(id = "r9", timestamp = now, createdAt = now, updatedAt = now))
        db.recordDao().linkPartner(RecordPartnerCrossRef(recordId = "r9", partnerId = "p9"))

        db.partnerDao().softDelete("p9", now)

        // 软删只隐藏伴侣，历史记录上的关联保留（统计口径据此过滤）
        assertEquals(0, db.partnerDao().countActive())
        assertEquals(1, db.recordDao().partnerLinkCount("p9"))
    }

    @Test
    fun record_last_timestamp_and_count() = runBlocking {
        val now = System.currentTimeMillis()
        db.recordDao().upsert(RecordEntity(id = "r1", timestamp = now - 1000, createdAt = now, updatedAt = now))
        db.recordDao().upsert(RecordEntity(id = "r2", timestamp = now, createdAt = now, updatedAt = now))

        assertEquals(2, db.recordDao().countActive())
        assertEquals(now, db.recordDao().lastTimestamp())
    }
}
