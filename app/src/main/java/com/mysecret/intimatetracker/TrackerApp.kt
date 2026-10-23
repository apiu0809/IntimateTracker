package com.mysecret.intimatetracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.Seeder
import com.mysecret.intimatetracker.data.sync.SyncNotifications
import com.mysecret.intimatetracker.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    /**
     * 使用自定义 WorkerFactory 时必须实现 [Configuration.Provider]，
     * 并在 manifest 中移除 WorkManager 默认初始化器。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        com.mysecret.intimatetracker.data.CrashLogger.install(this)
        SyncNotifications.ensureChannel(this)
        appScope.launch {
            Seeder.seed(database)
            // 启动时按当前设置恢复周期任务；已存在则保留原计划，避免冷启动重置
            syncScheduler.ensurePeriodic()
        }
    }
}
