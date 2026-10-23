package com.mysecret.intimatetracker

import com.mysecret.intimatetracker.data.sync.BackupArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BackupArchiveTest {

    @Test
    fun `pack and unpack keep photos and avatars`() {
        val root = Files.createTempDirectory("backup-test").toFile()
        val db = File(root, "tracker.db").apply { writeText("sqlite-bytes") }
        val images = File(root, "images/2026/10").apply { mkdirs() }
        File(images, "a.jpg").writeText("photo-a")
        val avatars = File(root, "avatars").apply { mkdirs() }
        File(avatars, "p1.jpg").writeText("avatar-p1")

        val zip = File(root, "backup.zip")
        BackupArchive.pack(db, File(root, "images"), """{"schemaVersion":1}""", zip, avatars)
        assertTrue(zip.length() > 0)

        val out = File(root, "out").apply { mkdirs() }
        val dbOut = File(out, "restored.db")
        val imagesOut = File(out, "images")
        val avatarsOut = File(out, "avatars")

        val manifest = BackupArchive.unpack(zip, dbOut, imagesOut, avatarsOut)

        assertEquals("""{"schemaVersion":1}""", manifest)
        assertEquals("sqlite-bytes", dbOut.readText())
        assertEquals("photo-a", File(imagesOut, "2026/10/a.jpg").readText())
        assertEquals("avatar-p1", File(avatarsOut, "p1.jpg").readText())

        root.deleteRecursively()
    }
}