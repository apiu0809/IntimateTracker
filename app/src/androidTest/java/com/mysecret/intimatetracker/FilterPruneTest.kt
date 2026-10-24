package com.mysecret.intimatetracker

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.db.RecordEntity
import com.mysecret.intimatetracker.data.db.RecordPartnerCrossRef
import com.mysecret.intimatetracker.data.repo.PartnerRepository
import com.mysecret.intimatetracker.data.repo.PhotoRepository
import com.mysecret.intimatetracker.data.repo.RecordRepository
import com.mysecret.intimatetracker.data.repo.SettingsRepository
import com.mysecret.intimatetracker.data.sync.SyncTrigger
import com.mysecret.intimatetracker.domain.model.Gender
import com.mysecret.intimatetracker.domain.model.RelationshipType
import com.mysecret.intimatetracker.ui.calendar.CalendarViewModel
import com.mysecret.intimatetracker.ui.history.HistoryViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 伴侣被删/归档后，日历与历史的筛选应自动剔除它，避免留下看不见的筛选条件。 */
@RunWith(AndroidJUnit4::class)
class FilterPruneTest {

    private lateinit var db: AppDatabase
    private lateinit var partnerRepository: PartnerRepository
    private lateinit var recordRepository: RecordRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var photoRepository: PhotoRepository

    /** 测试里手动 new 的 ViewModel 不会自动 clear，必须收尾取消，否则会与 db.close() 抢连接。 */
    private val viewModels = mutableListOf<androidx.lifecycle.ViewModel>()

    private fun <T : androidx.lifecycle.ViewModel> track(vm: T): T {
        viewModels += vm
        return vm
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        partnerRepository = PartnerRepository(db.partnerDao(), db, SyncTrigger { })
        recordRepository = RecordRepository(db, SyncTrigger { })
        settingsRepository = SettingsRepository(db.settingsDao())
        photoRepository = PhotoRepository(context, db, SyncTrigger { })

        db.partnerDao().upsert(
            PartnerEntity(id = "p1", name = "伴侣一", createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L),
        )
    }

    @After
    fun tearDown() {
        // 先停掉 ViewModel 的协程并等主线程空闲，再关库；否则 FilterPruneTest 偶发
        // 「Process crashed: SQLiteConnectionPool.close()」。
        viewModels.forEach { it.viewModelScope.cancel() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        db.close()
    }

    @Test
    fun calendar_filter_drops_deleted_partner() {
        val vm = track(CalendarViewModel(recordRepository, settingsRepository, partnerRepository))
        vm.togglePartner("p1")
        assertEquals(setOf("p1"), vm.filter.partnerIds)

        runBlocking { partnerRepository.delete("p1") }

        awaitUntil("日历筛选未清理") { vm.filter.partnerIds.isEmpty() }
        assertTrue(vm.filter.partnerIds.isEmpty())
    }

    @Test
    fun history_filter_drops_archived_partner() {
        val vm = track(HistoryViewModel(recordRepository, photoRepository, partnerRepository))
        vm.togglePartner("p1")
        assertEquals(setOf("p1"), vm.filter.partnerIds)

        // 归档同样会让伴侣从「活跃列表」消失
        runBlocking { partnerRepository.setArchived("p1", true) }

        awaitUntil("历史筛选未清理") { vm.filter.partnerIds.isEmpty() }
        assertTrue(vm.filter.partnerIds.isEmpty())
    }

    @Test
    fun partner_history_list_keeps_archived_and_deleted() = runBlocking {
        partnerRepository.setArchived("p1", true)
        partnerRepository.delete("p1")

        assertTrue("活跃列表排除归档与已删", partnerRepository.observeActive().first().isEmpty())
        val history = partnerRepository.observeAllHistory().first()
        assertEquals(1, history.size)
        assertNotNull(history.single().deletedAt)
        assertTrue(history.single().isArchived)
    }

    @Test
    fun partner_restore_brings_it_back() = runBlocking {
        partnerRepository.delete("p1")
        assertTrue(partnerRepository.observeActive().first().isEmpty())
        assertEquals(1, partnerRepository.observeDeleted().first().size)

        partnerRepository.restore("p1")

        assertEquals(listOf("p1"), partnerRepository.observeActive().first().map { it.id })
        assertTrue(partnerRepository.observeDeleted().first().isEmpty())
    }

    @Test
    fun partner_restore_unarchives_and_returns_to_active_list() = runBlocking {
        partnerRepository.setArchived("p1", true)
        partnerRepository.delete("p1")
        assertTrue(partnerRepository.observeActive().first().isEmpty())

        partnerRepository.restore("p1")

        assertEquals(
            "恢复应同时取消归档，否则它只是从「已删除」挪进「已归档」，看起来像没恢复",
            listOf("p1"),
            partnerRepository.observeActive().first().map { it.id },
        )
    }

    @Test
    fun partner_restore_and_update_keeps_the_values_just_typed() = runBlocking {
        partnerRepository.setArchived("p1", true)
        partnerRepository.delete("p1")

        partnerRepository.restoreAndUpdate(
            id = "p1",
            name = "伴侣一改",
            gender = Gender.FEMALE,
            relationship = RelationshipType.LOVER,
            notes = "恢复时填的备注",
            avatarPath = null,
        )

        val restored = partnerRepository.observeActive().first().single()
        assertEquals("伴侣一改", restored.name)
        assertEquals(Gender.FEMALE, restored.gender)
        assertEquals(RelationshipType.LOVER, restored.relationshipType)
        assertEquals("恢复时填的备注", restored.notes)
        assertFalse(restored.isArchived)
        assertNull(restored.deletedAt)
    }

    @Test
    fun partner_find_by_name_includes_deleted_but_ignores_self() = runBlocking {
        assertEquals("p1", partnerRepository.findByName(" 伴侣一 ")?.id)
        assertNull(partnerRepository.findByName("伴侣一", excludeId = "p1"))

        partnerRepository.delete("p1")
        val deleted = partnerRepository.findByName("伴侣一")
        assertEquals("已删除伴侣同样算重名", "p1", deleted?.id)
        assertNotNull(deleted?.deletedAt)
    }

    @Test
    fun partner_rename_and_restore_keep_the_record_links() = runBlocking {
        val now = 1_700_000_000_000L
        db.recordDao().upsert(RecordEntity(id = "r1", timestamp = now, createdAt = now, updatedAt = now))
        db.recordDao().linkPartner(RecordPartnerCrossRef(recordId = "r1", partnerId = "p1"))

        partnerRepository.save("p1", "伴侣一改", Gender.FEMALE, RelationshipType.LOVER, "备注")
        assertEquals("改名不能把记录关联级联删掉", 1, db.recordDao().partnerLinkCount("p1"))

        partnerRepository.delete("p1")
        partnerRepository.restore("p1")
        assertEquals("恢复后记录关联仍在", 1, db.recordDao().partnerLinkCount("p1"))
    }

    @Test
    fun partner_purge_removes_row_and_writes_tombstone() = runBlocking {
        partnerRepository.delete("p1")
        partnerRepository.purge("p1")

        assertTrue("物理删除后连全量表里也没有", partnerRepository.observeAllHistory().first().isEmpty())
        val tomb = db.tombstoneDao().all().single()
        assertEquals("partner", tomb.tableName)
        assertEquals("p1", tomb.rowId)
    }

    private fun awaitUntil(message: String, timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        fail(message)
    }
}
