package com.mysecret.intimatetracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.RecordEntity
import com.mysecret.intimatetracker.data.repo.PhotoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: PhotoRepository
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PhotoRepository(context, db)
        val now = System.currentTimeMillis()
        db.recordDao().upsert(RecordEntity(id = "r1", timestamp = now, createdAt = now, updatedAt = now))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sourceImage(width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.RED)
        val file = File(context.cacheDir, "source-${System.nanoTime()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    @Test
    fun import_copies_compresses_and_persists_photo() = runBlocking {
        val photos = repository.import("r1", listOf(sourceImage(3000, 2000)))

        assertEquals(1, photos.size)
        val photo = photos.first()
        val file = repository.fileFor(photo.relPath)
        assertTrue(file.exists())
        assertTrue(photo.width!! <= 2048)
        assertTrue(photo.height!! <= 2048)
        assertTrue(photo.relPath.startsWith("images/"))
        assertEquals(1, repository.photosFor("r1").size)
    }

    @Test
    fun multiple_uris_are_imported_in_order() = runBlocking {
        val photos = repository.import("r1", listOf(sourceImage(400, 300), sourceImage(500, 500)))

        assertEquals(2, photos.size)
        assertEquals(2, repository.photosFor("r1").size)
        assertEquals(2, repository.observeAll().first().size)
    }

    @Test
    fun delete_soft_deletes_row_and_removes_file() = runBlocking {
        val photo = repository.import("r1", listOf(sourceImage(300, 300))).first()

        repository.delete(photo)

        assertTrue(repository.photosFor("r1").isEmpty())
        assertTrue(!repository.fileFor(photo.relPath).exists())
    }
}
