package com.mysecret.intimatetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.RecordEntity
import com.mysecret.intimatetracker.data.db.Seeder
import com.mysecret.intimatetracker.data.sync.BackupArchive
import com.mysecret.intimatetracker.data.sync.SyncManager
import com.mysecret.intimatetracker.data.sync.SyncResult
import com.mysecret.intimatetracker.data.sync.WebDavClient
import com.mysecret.intimatetracker.data.sync.WebDavClientFactory
import com.mysecret.intimatetracker.data.sync.WebDavConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SyncFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var dav: FakeDav
    private lateinit var sync: SyncManager
    private val config = WebDavConfig("https://example.com/dav", "u", "p")
    private val dbName = "sync-local-test.db"

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(dbName)
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        Seeder.seed(db)
        dav = FakeDav()
        sync = SyncManager(context, db, object : WebDavClientFactory {
            override fun create(config: WebDavConfig): WebDavClient = dav
        })
    }

    @After
    fun tearDown() {
        db.close()
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(dbName)
    }

    private fun record(id: String, updatedAt: Long) = RecordEntity(
        id = id,
        timestamp = updatedAt,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    @Test
    fun first_sync_uploads_archive(): Unit = runBlocking {
        db.recordDao().upsert(record("r1", 1_000))

        val result = sync.sync(config)

        assertTrue(result is SyncResult.Success)
        assertTrue(dav.files.keys.any { it.startsWith("IntimateTracker/backup-") && it.endsWith(".zip") })
        assertTrue((db.settingsDao().get(SyncManager.KEY_ETAG) ?: "").isNotEmpty())
    }

    @Test
    fun second_sync_with_same_etag_is_no_change(): Unit = runBlocking {
        db.recordDao().upsert(record("r1", 1_000))
        sync.sync(config)

        assertEquals(SyncResult.NoChange, sync.sync(config))
    }
    @Test
    fun new_local_record_forces_upload_even_without_etag(): Unit = runBlocking {
        val now = System.currentTimeMillis()
        db.recordDao().upsert(record("r1", now - 5_000))
        sync.sync(config)

        // 模拟坚果云：服务端不返回 ETag
        dav.etags.clear()
        assertEquals(SyncResult.NoChange, sync.sync(config))

        // 新增一条记录后必须重新上传
        db.recordDao().upsert(record("r2", now + 5_000))
        val result = sync.sync(config)

        assertTrue(result is SyncResult.Success)
    }

    @Test
    fun remote_newer_record_wins_after_merge(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db.recordDao().upsert(record("r1", 1_000))

        val remoteName = "sync-remote-test.db"
        context.deleteDatabase(remoteName)
        val remote = Room.databaseBuilder(context, AppDatabase::class.java, remoteName).build()
        remote.recordDao().upsert(record("r1", 5_000))
        remote.close()

        val archive = File(context.cacheDir, "remote-seed.zip")
        BackupArchive.pack(context.getDatabasePath(remoteName), File(context.filesDir, "images"), "{}", archive)
        dav.dirs += "IntimateTracker"
        dav.files["IntimateTracker/backup-20260101-000000.zip"] = archive.readBytes()
        dav.etags["IntimateTracker/backup-20260101-000000.zip"] = "\"remote-etag\""

        val result = sync.sync(config)

        assertTrue(result is SyncResult.Success)
        assertEquals(5_000L, db.recordDao().find("r1")?.updatedAt)
        archive.delete()
        context.deleteDatabase(remoteName)
    }
}
