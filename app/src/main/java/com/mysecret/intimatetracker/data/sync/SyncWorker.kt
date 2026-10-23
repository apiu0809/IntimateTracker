package com.mysecret.intimatetracker.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mysecret.intimatetracker.data.db.SettingEntity
import com.mysecret.intimatetracker.data.db.SettingsDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 后台同步任务。只从 settings + 加密存储读取配置，失败按类型决定是否重试。
 * 大包上传时用前台服务通知，避免 WorkManager 的 10 分钟限制。
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsDao,
    private val secureStore: SecureStore,
    private val syncManager: SyncManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = settings.get(WebDavKeys.URL)?.trim().orEmpty()
        val user = settings.get(WebDavKeys.USER).orEmpty()
        val password = secureStore.getPassword()
        if (url.isBlank()) return Result.success()
        settings.put(SettingEntity(SyncManager.KEY_LAST_ATTEMPT, System.currentTimeMillis().toString()))

        runCatching { setForeground(SyncNotifications.foregroundInfo(applicationContext)) }

        return when (val result = syncManager.sync(WebDavConfig(url, user, password))) {
            is SyncResult.Success -> {
                settings.put(SettingEntity(SyncManager.KEY_LAST_RESULT, "ok"))
                Result.success()
            }
            is SyncResult.Pulled -> {
                settings.put(SettingEntity(SyncManager.KEY_LAST_RESULT, "ok"))
                Result.success()
            }
            is SyncResult.NoChange -> {
                settings.put(SettingEntity(SyncManager.KEY_LAST_RESULT, "ok"))
                Result.success()
            }
            is SyncResult.Failure -> {
                settings.put(SettingEntity(SyncManager.KEY_LAST_RESULT, result.message.ifBlank { result.code }))
                if (result.code in TRANSIENT_CODES) Result.retry() else Result.failure()
            }
        }
    }

    private companion object {
        /** 网络/服务端临时错误重试；认证、配置或空间类错误不重试。 */
        val TRANSIENT_CODES = setOf("E_NET", "E_SYNC")
    }
}
