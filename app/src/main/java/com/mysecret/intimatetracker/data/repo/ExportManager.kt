package com.mysecret.intimatetracker.data.repo

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.sync.BackupArchive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
) {

    /** 导出 zip（数据库 + 照片）到 系统下载/IntimateTracker。返回显示用文件名。 */
    suspend fun exportZip(): String? = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        val archive = File(context.cacheDir, "export.zip")
        BackupArchive.pack(
            dbFile = dbFile,
            imagesDir = File(context.filesDir, "images"),
            manifestJson = """{"schemaVersion":1,"exportedAt":${System.currentTimeMillis()}}""",
            target = archive,
            avatarsDir = File(context.filesDir, "avatars"),
        )

        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val fileName = "IntimateTracker-$stamp.zip"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/IntimateTracker")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            archive.delete()
            return@withContext null
        }
        resolver.openOutputStream(uri)?.use { output ->
            archive.inputStream().use { it.copyTo(output) }
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        archive.delete()
        fileName
    }

    /** 清空：数据库全部表 + 本地照片 + 伴侣头像 + 备份缓存。 */
    suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
        db.clearAllTables()
        File(context.filesDir, "images").deleteRecursively()
        File(context.filesDir, "avatars").deleteRecursively()
        File(context.cacheDir, "sync").deleteRecursively()
        File(context.cacheDir, "export.zip").delete()
        Unit
    }

    fun supportsDownloads() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
        Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
}
