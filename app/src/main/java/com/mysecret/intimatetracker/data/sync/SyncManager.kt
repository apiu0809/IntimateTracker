package com.mysecret.intimatetracker.data.sync

import android.content.Context
import android.os.Build
import androidx.room.Room
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.Migrations
import com.mysecret.intimatetracker.data.db.RecordLocationCrossRef
import com.mysecret.intimatetracker.data.db.RecordPartnerCrossRef
import com.mysecret.intimatetracker.data.db.RecordPositionCrossRef
import com.mysecret.intimatetracker.data.db.RecordSexTypeCrossRef
import com.mysecret.intimatetracker.data.db.RecordToyCrossRef
import com.mysecret.intimatetracker.data.db.SettingEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class WebDavConfig(val url: String, val user: String, val password: String)

interface WebDavClientFactory {
    fun create(config: WebDavConfig): WebDavClient
}

@Singleton
class OkHttpWebDavFactory @Inject constructor() : WebDavClientFactory {
    override fun create(config: WebDavConfig): WebDavClient =
        OkHttpWebDavClient(config.url, config.user, config.password)
}

sealed interface SyncResult {
    data object NoChange : SyncResult
    data class Pulled(val mergedRecords: Int) : SyncResult
    data class Success(val mergedRecords: Int, val uploadedName: String) : SyncResult
    data class Failure(val code: String, val message: String) : SyncResult
}

/**
 * v3 同步：
 * - 远端只维护一份 `current.zip`（先上传 `.tmp` 再 MOVE，避免半截文件）
 * - 每天首次成功同步额外留一份 `history/backup-yyyyMMdd.zip`，共保留 14 份历史
 * - 下载 current.zip → 逐行 LWW 合并（伴侣 / 字典 / 记录关联 / 照片）→ 仅在本地有新改动时上传
 * - 兼容旧版根目录下的 backup-*.zip：首次升级会以最新一份为源，合并后生成 current.zip
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val clientFactory: WebDavClientFactory,
) {

    private val remoteDir = "IntimateTracker"
    private val workDir: File get() = File(context.cacheDir, "sync").apply { mkdirs() }
    private val remoteDbName = "sync-remote.db"
    private val mutex = Mutex()

    val isSyncing: Boolean get() = mutex.isLocked

    suspend fun testConnection(config: WebDavConfig): Result<Unit> = runCatching {
        val client = clientFactory.create(config)
        val entries = client.propfind(remoteDir)
        if (entries.isEmpty()) client.mkcol(remoteDir)
    }

    suspend fun sync(config: WebDavConfig): SyncResult = withContext(Dispatchers.IO) {
        mutex.withLock { runSync(config) }
    }

    private suspend fun runSync(config: WebDavConfig): SyncResult {
        val client = clientFactory.create(config)
        return try {
            if (client.propfind(remoteDir).isEmpty()) client.mkcol(remoteDir)

            val current = findCurrent(client)
            val legacy = if (current == null) latestLegacy(client) else null
            val source = current ?: legacy

            val localVersion = db.recordDao().dataVersion() ?: 0L
            val syncedVersion = setting(KEY_LOCAL_VERSION)?.toLongOrNull() ?: -1L
            val localCount = db.recordDao().countActive()
            val localUnchanged = localVersion <= syncedVersion

            val remoteSame = current != null && when {
                !current.etag.isNullOrBlank() -> {
                    val localEtag = setting(KEY_ETAG).orEmpty()
                    localEtag.isNotBlank() && current.etag == localEtag
                }
                else -> {
                    val localSize = setting(KEY_REMOTE_SIZE)?.toLongOrNull() ?: -1L
                    localSize >= 0 && current.size == localSize
                }
            }
            if (localUnchanged && remoteSame && localCount > 0) {
                return SyncResult.NoChange
            }

            var merged = 0
            if (source != null) {
                val archive = File(workDir, "download.zip")
                download(client, source, archive)
                merged = mergeFromArchive(archive)
                archive.delete()
            }

            val afterVersion = db.recordDao().dataVersion() ?: 0L
            // 本地没有新改动、且远端本来就有 current.zip：拉取即可，不必再回传一份
            val mustPush = !localUnchanged || source == null || current == null

            if (!mustPush) {
                rememberRemote(client, afterVersion, "ok")
                pruneHistory(client)
                return SyncResult.Pulled(merged)
            }

            val uploaded = push(client, afterVersion)
            rememberUploaded(uploaded, afterVersion)
            pruneHistory(client)
            SyncResult.Success(mergedRecords = merged, uploadedName = CURRENT_NAME)
        } catch (e: SyncException) {
            android.util.Log.w("SyncManager", "sync failed ${e.code}: ${e.message}")
            SyncResult.Failure(e.code, e.message.orEmpty())
        } catch (e: Exception) {
            android.util.Log.w("SyncManager", "sync failed", e)
            SyncResult.Failure("E_SYNC", e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun setting(key: String): String? = db.settingsDao().get(key)

    private suspend fun putSetting(key: String, value: String) {
        db.settingsDao().put(SettingEntity(key, value))
    }

    private suspend fun findCurrent(client: WebDavClient): RemoteEntry? =
        client.propfind(remoteDir).firstOrNull { !it.isDirectory && it.path == CURRENT_NAME }

    private suspend fun latestLegacy(client: WebDavClient): RemoteEntry? =
        client.propfind(remoteDir)
            .filter { !it.isDirectory && isBackup(it.path) }
            .maxByOrNull { it.path }

    /** 下载 current.zip；失败时回退到最近几份日备，避免坏包卡死。 */
    private suspend fun download(client: WebDavClient, source: RemoteEntry, target: File) {
        val candidates = mutableListOf("$remoteDir/${source.path}")
        if (source.path == CURRENT_NAME) {
            runCatching { client.propfind("$remoteDir/$HISTORY_DIR") }
                .getOrDefault(emptyList())
                .filter { !it.isDirectory && it.path.endsWith(".zip") }
                .sortedByDescending { it.path }
                .take(3)
                .forEach { candidates += "$remoteDir/$HISTORY_DIR/${it.path}" }
        }
        var last: Exception? = null
        for (path in candidates) {
            try {
                client.get(path, target)
                return
            } catch (e: Exception) {
                last = e
                target.delete()
            }
        }
        throw last ?: SyncException("E_NOT_FOUND", "远端备份不存在")
    }

    private suspend fun mergeFromArchive(archive: File): Int {
        val remoteDbFile = context.getDatabasePath(remoteDbName).apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }
        val imagesDir = File(context.filesDir, "images")
        val avatarsDir = File(context.filesDir, "avatars")
        BackupArchive.unpack(archive, remoteDbFile, imagesDir, avatarsDir)
        if (!remoteDbFile.exists()) return 0

        val remote = Room.databaseBuilder(context, AppDatabase::class.java, remoteDbName)
            .addMigrations(*Migrations.ALL)
            .build()
        var changed = 0
        try {
            remote.partnerDao().allForSync().forEach { partner ->
                val local = db.partnerDao().findIncludingDeleted(partner.id)
                if (local == null || partner.updatedAt > local.updatedAt) {
                    db.partnerDao().upsert(partner)
                    changed++
                }
            }
            mergeDictionaries(remote)
            changed += mergeRecords(remote)
            changed += mergePhotos(remote)
        } finally {
            remote.close()
            context.getDatabasePath(remoteDbName).delete()
        }
        return changed
    }

    private suspend fun mergeDictionaries(remote: AppDatabase) {
        remote.sexTypeDao().all().forEach { item ->
            val local = db.sexTypeDao().byId(item.id)
            if (local == null || item.updatedAt > local.updatedAt) db.sexTypeDao().upsert(item)
        }
        remote.positionDao().all().forEach { item ->
            val local = db.positionDao().byId(item.id)
            if (local == null || item.updatedAt > local.updatedAt) db.positionDao().upsert(item)
        }
        remote.toyDao().all().forEach { item ->
            val local = db.toyDao().byId(item.id)
            if (local == null || item.updatedAt > local.updatedAt) db.toyDao().upsert(item)
        }
        remote.locationDao().all().forEach { item ->
            val local = db.locationDao().byId(item.id)
            if (local == null || item.updatedAt > local.updatedAt) db.locationDao().upsert(item)
        }
    }

    private suspend fun mergeRecords(remote: AppDatabase): Int {
        var changed = 0
        remote.recordDao().allForSync().forEach { record ->
            val local = db.recordDao().findIncludingDeleted(record.id)
            if (local == null || record.updatedAt > local.updatedAt) {
                db.recordDao().upsert(record)
                copyLinks(remote, record.id)
                changed++
            }
        }
        return changed
    }

    /** 照片行按 updated_at LWW 合并；远端墓碑会同时删掉本地文件。 */
    private suspend fun mergePhotos(remote: AppDatabase): Int {
        var changed = 0
        remote.photoDao().allForSync().forEach { photo ->
            val local = db.photoDao().byId(photo.id)
            if (local == null || photo.updatedAt > local.updatedAt) {
                db.photoDao().insert(photo)
                if (photo.deletedAt != null) {
                    runCatching { File(context.filesDir, photo.relPath).delete() }
                }
                changed++
            }
        }
        return changed
    }

    private suspend fun copyLinks(remote: AppDatabase, recordId: String) {
        db.recordDao().clearPartners(recordId)
        db.recordDao().clearSexTypes(recordId)
        db.recordDao().clearPositions(recordId)
        db.recordDao().clearToys(recordId)
        db.recordDao().clearLocations(recordId)
        remote.recordDao().partnerIds(recordId).forEach {
            db.recordDao().linkPartner(RecordPartnerCrossRef(recordId, it))
        }
        remote.recordDao().sexTypeIds(recordId).forEach {
            db.recordDao().linkSexType(RecordSexTypeCrossRef(recordId, it))
        }
        remote.recordDao().positionIds(recordId).forEach {
            db.recordDao().linkPosition(RecordPositionCrossRef(recordId, it))
        }
        remote.recordDao().toyIds(recordId).forEach {
            db.recordDao().linkToy(RecordToyCrossRef(recordId, it))
        }
        remote.recordDao().locationIds(recordId).forEach {
            db.recordDao().linkLocation(RecordLocationCrossRef(recordId, it))
        }
    }

    private suspend fun push(client: WebDavClient, localVersion: Long): RemoteEntry {
        val snapshot = snapshotDb()
        val deviceId = setting(KEY_DEVICE_ID) ?: UUID.randomUUID().toString().also {
            putSetting(KEY_DEVICE_ID, it)
        }
        val manifest = JSONObject()
            .put("schemaVersion", 2)
            .put("deviceId", deviceId)
            .put("exportedAt", System.currentTimeMillis())
            .put("records", db.recordDao().countActive())
            .put("dataVersion", localVersion)
            .toString()

        val archive = File(workDir, "current-upload.zip")
        try {
            BackupArchive.pack(
                snapshot,
                File(context.filesDir, "images"),
                manifest,
                archive,
                File(context.filesDir, "avatars"),
            )
        } finally {
            snapshot.delete()
        }

        val tmpPath = "$remoteDir/$CURRENT_NAME.tmp"
        client.put(tmpPath, archive)
        try {
            client.move(tmpPath, "$remoteDir/$CURRENT_NAME")
        } catch (e: SyncException) {
            // 部分 WebDAV 服务不允许 MOVE 覆盖已有文件：先删旧文件再移动
            client.delete("$remoteDir/$CURRENT_NAME")
            client.move(tmpPath, "$remoteDir/$CURRENT_NAME")
        }

        // 每天首次成功同步留一份历史，避免每次同步都产生整包照片副本
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (setting(KEY_LAST_HISTORY) != day) {
            runCatching {
                client.mkcol("$remoteDir/$HISTORY_DIR")
                client.put("$remoteDir/$HISTORY_DIR/$HISTORY_PREFIX$day.zip", archive)
                putSetting(KEY_LAST_HISTORY, day)
            }
        }
        archive.delete()

        return findCurrent(client)
            ?: RemoteEntry(CURRENT_NAME, null, 0, isDirectory = false)
    }

    /**
     * 生成一致的数据库快照：Android 10+ 用 VACUUM INTO，旧系统回退到 checkpoint + 复制。
     */
    private fun snapshotDb(): File {
        val target = File(workDir, "snapshot.db").apply { if (exists()) delete() }
        val writable = db.openHelper.writableDatabase
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val path = target.absolutePath.replace("'", "''")
                writable.execSQL("VACUUM INTO '$path'")
            }.onFailure {
                target.delete()
                writable.query("PRAGMA wal_checkpoint(FULL)").close()
                context.getDatabasePath(AppDatabase.NAME).copyTo(target, overwrite = true)
            }
        } else {
            writable.query("PRAGMA wal_checkpoint(FULL)").close()
            context.getDatabasePath(AppDatabase.NAME).copyTo(target, overwrite = true)
        }
        return target
    }

    private suspend fun rememberUploaded(uploaded: RemoteEntry, version: Long) {
        rememberRemote(client = null, version = version, result = "ok", uploaded = uploaded)
    }

    private suspend fun rememberRemote(
        client: WebDavClient?,
        version: Long,
        result: String,
        uploaded: RemoteEntry? = null,
    ) {
        val current = uploaded ?: client?.let { findCurrent(it) }
        putSetting(KEY_ETAG, current?.etag.orEmpty())
        putSetting(KEY_REMOTE_SIZE, (current?.size ?: 0L).toString())
        putSetting(KEY_LAST_BACKUP, if (current != null) CURRENT_NAME else "")
        putSetting(KEY_LOCAL_VERSION, version.toString())
        putSetting(KEY_LAST_SYNC, System.currentTimeMillis().toString())
        putSetting(KEY_LAST_RESULT, result)
    }

    private suspend fun pruneHistory(client: WebDavClient) {
        val root = runCatching { client.propfind(remoteDir) }.getOrDefault(emptyList())
        val history = runCatching { client.propfind("$remoteDir/$HISTORY_DIR") }.getOrDefault(emptyList())
        val backups = buildList {
            root.filter { !it.isDirectory && isBackup(it.path) }
                .forEach { add("$remoteDir/${it.path}" to it.path) }
            history.filter { !it.isDirectory && it.path.endsWith(".zip") }
                .forEach { add("$remoteDir/$HISTORY_DIR/${it.path}" to it.path) }
        }
        backups.sortedByDescending { it.second }
            .drop(MAX_BACKUPS)
            .forEach { (path, _) -> runCatching { client.delete(path) } }
    }

    private fun isBackup(name: String): Boolean =
        name.endsWith(".zip") && (name.startsWith(BACKUP_PREFIX) || name == "backup.zip")

    companion object {
        const val KEY_ETAG = "sync.etag"
        const val KEY_LAST_SYNC = "sync.last_at"
        const val KEY_DEVICE_ID = "sync.device_id"
        const val KEY_LAST_BACKUP = "sync.last_backup"
        const val KEY_LOCAL_VERSION = "sync.local_version"
        const val KEY_REMOTE_SIZE = "sync.remote_size"
        const val KEY_LAST_HISTORY = "sync.last_history_day"
        const val KEY_LAST_RESULT = "sync.last_result"
        const val KEY_LAST_ATTEMPT = "sync.last_attempt"

        const val CURRENT_NAME = "current.zip"
        const val HISTORY_DIR = "history"
        const val HISTORY_PREFIX = "backup-"
        const val BACKUP_PREFIX = "backup-"
        const val MAX_BACKUPS = 14
    }
}
