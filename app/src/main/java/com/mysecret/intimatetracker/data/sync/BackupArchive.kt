package com.mysecret.intimatetracker.data.sync

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 备份包结构：manifest.json + tracker.db + photos/ + avatars/ */
object BackupArchive {

    const val DB_ENTRY = "tracker.db"
    const val MANIFEST_ENTRY = "manifest.json"
    const val PHOTOS_PREFIX = "photos/"
    const val AVATARS_PREFIX = "avatars/"

    fun pack(
        dbFile: File,
        imagesDir: File,
        manifestJson: String,
        target: File,
        avatarsDir: File? = null,
    ) {
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifestJson.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry(DB_ENTRY))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            packDir(zip, imagesDir, PHOTOS_PREFIX)
            packDir(zip, avatarsDir, AVATARS_PREFIX)
        }
    }

    /** 解压到工作目录：db 解到 dbTarget，图片与头像解到对应目录。返回 manifest。 */
    fun unpack(zip: File, dbTarget: File, imagesDir: File, avatarsDir: File? = null): String? {
        var manifest: String? = null
        dbTarget.parentFile?.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry: ZipEntry? = input.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == MANIFEST_ENTRY -> manifest = input.readBytes().decodeToString()
                    name == DB_ENTRY -> dbTarget.outputStream().use { input.copyTo(it) }
                    name.startsWith(PHOTOS_PREFIX) -> {
                        val target = File(imagesDir, name.removePrefix(PHOTOS_PREFIX))
                        target.parentFile?.mkdirs()
                        target.outputStream().use { input.copyTo(it) }
                    }
                    name.startsWith(AVATARS_PREFIX) && avatarsDir != null -> {
                        val target = File(avatarsDir, name.removePrefix(AVATARS_PREFIX))
                        target.parentFile?.mkdirs()
                        target.outputStream().use { input.copyTo(it) }
                    }
                }
                input.closeEntry()
                entry = input.nextEntry
            }
        }
        return manifest
    }

    private fun packDir(zip: ZipOutputStream, dir: File?, prefix: String) {
        if (dir == null || !dir.exists()) return
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(dir).path.replace('\\', '/')
            zip.putNextEntry(ZipEntry(prefix + relative))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }
}
