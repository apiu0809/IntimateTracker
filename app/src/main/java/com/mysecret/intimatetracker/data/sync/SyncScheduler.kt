package com.mysecret.intimatetracker.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mysecret.intimatetracker.data.db.SettingEntity
import com.mysecret.intimatetracker.data.db.SettingsDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动同步调度：
 * - 周期任务（WorkManager，最低 15 分钟）
 * - 打开 App 时的机会性同步（15 分钟内不重复）
 * - 本地修改后的防抖同步（2 分钟后，15 分钟内不重复）
 *
 * 所有入口用唯一任务名 + KEEP/UPDATE，配合 SyncManager 内部的 Mutex 避免叠加。
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsDao,
    private val syncManager: SyncManager,
) : SyncTrigger {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedulePeriodic() {
        scope.launch { schedulePeriodicNow(update = true) }
    }

    /** 应用启动时只确保任务存在，避免每次冷启动都重置周期。 */
    fun ensurePeriodic() {
        scope.launch { schedulePeriodicNow(update = false) }
    }

    fun cancelPeriodic() {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }

    fun maybeSyncOnOpen() {
        scope.launch { maybeSyncOnOpenNow() }
    }

    override fun onLocalChange() {
        scope.launch {
            if (!enabled()) return@launch
            val (wifi, charging) = constraintsFromSettings()
            enqueueNow(DEBOUNCE_WORK, DEBOUNCE_MS, wifi, charging)
        }
    }

    private suspend fun schedulePeriodicNow(update: Boolean) {
        if (!enabled()) {
            cancelPeriodic()
            return
        }
        val interval = (settings.get(KEY_INTERVAL)?.toIntOrNull() ?: DEFAULT_INTERVAL).coerceAtLeast(15)
        val (wifi, charging) = constraintsFromSettings()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints(wifi, charging))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            if (update) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private suspend fun maybeSyncOnOpenNow() {
        if (!enabled() || syncManager.isSyncing) return
        val lastAttempt = settings.get(SyncManager.KEY_LAST_ATTEMPT)?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - lastAttempt < OPEN_THROTTLE_MS) return
        settings.put(SettingEntity(SyncManager.KEY_LAST_ATTEMPT, System.currentTimeMillis().toString()))
        val (wifi, charging) = constraintsFromSettings()
        enqueueNow(NOW_WORK, 0L, wifi, charging)
    }

    private fun enqueueNow(name: String, delayMs: Long, wifiOnly: Boolean, chargingOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints(wifiOnly, chargingOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    private suspend fun enabled(): Boolean =
        settings.get(KEY_AUTO_ENABLED) == "true" &&
            !settings.get(WebDavKeys.URL).isNullOrBlank()

    private suspend fun constraintsFromSettings(): Pair<Boolean, Boolean> {
        val wifiOnly = settings.get(KEY_WIFI_ONLY) != "false"
        val chargingOnly = settings.get(KEY_CHARGING_ONLY) == "true"
        return wifiOnly to chargingOnly
    }

    private fun constraints(wifiOnly: Boolean, chargingOnly: Boolean): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .build()

    companion object {
        const val KEY_AUTO_ENABLED = "sync.auto.enabled"
        const val KEY_INTERVAL = "sync.auto.interval_minutes"
        const val KEY_WIFI_ONLY = "sync.auto.wifi_only"
        const val KEY_CHARGING_ONLY = "sync.auto.charging_only"

        const val DEFAULT_INTERVAL = 60

        private const val PERIODIC_WORK = "sync-periodic"
        private const val NOW_WORK = "sync-now"
        private const val DEBOUNCE_WORK = "sync-debounce"

        private const val OPEN_THROTTLE_MS = 15 * 60 * 1000L
        private const val DEBOUNCE_MS = 2 * 60 * 1000L
    }
}
