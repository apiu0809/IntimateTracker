package com.mysecret.intimatetracker.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.PhotoEntity
import com.mysecret.intimatetracker.data.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class PhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val syncScheduler: SyncScheduler,
) {

    fun fileFor(relPath: String): File = File(context.filesDir, relPath)

    fun observeAll(): Flow<List<PhotoEntity>> = db.photoDao().observeAll()

    fun observeAlbum(): Flow<List<com.mysecret.intimatetracker.data.db.PhotoWithRecord>> =
        db.photoDao().observeAllWithRecord()

    suspend fun photosFor(recordId: String): List<PhotoEntity> = db.photoDao().byRecord(recordId)

    /** 把系统相册的 Uri 复制压缩到 filesDir/images/yyyy/MM/<uuid>.jpg，并写入 photo 表。 */
    suspend fun import(recordId: String, uris: List<Uri>): List<PhotoEntity> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val imported = uris.mapNotNull { uri ->
            runCatching { copyToPrivate(uri) }.getOrNull()?.let { (relPath, width, height, size) ->
                val entity = PhotoEntity(
                    id = UUID.randomUUID().toString(),
                    recordId = recordId,
                    relPath = relPath,
                    width = width,
                    height = height,
                    sizeBytes = size,
                    createdAt = now,
                    updatedAt = now,
                )
                db.photoDao().insert(entity)
                entity
            }
        }
        if (imported.isNotEmpty()) syncScheduler.onLocalChange()
        imported
    }

    suspend fun delete(photo: PhotoEntity) {
        db.photoDao().softDelete(photo.id, System.currentTimeMillis())
        runCatching { fileFor(photo.relPath).delete() }
        syncScheduler.onLocalChange()
    }

    private fun copyToPrivate(uri: Uri): PhotoFile {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: error("无法解码图片")

        val scaled = scaleDown(bitmap)
        val width = scaled.width
        val height = scaled.height
        val monthDir = File(context.filesDir, "images/${YearMonth.now()}").apply { mkdirs() }
        val file = File(monthDir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()

        return PhotoFile(
            relPath = file.relativeTo(context.filesDir).path,
            width = width,
            height = height,
            size = file.length(),
        )
    }

    /** 头像：中心裁成正方形，缩到 512px 存到 filesDir/avatars/，返回相对路径。 */
    suspend fun importAvatar(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("无法读取图片")

        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("无法解码图片")

        val side = min(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        val square = Bitmap.createBitmap(bitmap, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
        if (square !== bitmap) bitmap.recycle()
        if (scaled !== square) square.recycle()

        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 88, out) }
        scaled.recycle()
        file.relativeTo(context.filesDir).path
    }
    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE) return bitmap
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private data class PhotoFile(val relPath: String, val width: Int, val height: Int, val size: Long)

    companion object {
        private const val MAX_EDGE = 2048
    }
}
