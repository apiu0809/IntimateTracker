package com.mysecret.intimatetracker.ui.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.data.db.SettingEntity
import com.mysecret.intimatetracker.data.db.SettingsDao
import com.mysecret.intimatetracker.data.sync.SecureStore
import com.mysecret.intimatetracker.data.sync.SyncManager
import com.mysecret.intimatetracker.data.sync.SyncResult
import com.mysecret.intimatetracker.data.sync.SyncScheduler
import com.mysecret.intimatetracker.data.sync.WebDavConfig
import com.mysecret.intimatetracker.data.sync.WebDavKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebDavSettingsViewModel @Inject constructor(
    private val settings: SettingsDao,
    private val syncManager: SyncManager,
    private val syncScheduler: SyncScheduler,
    private val secureStore: SecureStore,
) : ViewModel() {

    var url by mutableStateOf("")
        private set
    var user by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var lastSyncAt by mutableStateOf<Long?>(null)
        private set

    var autoEnabled by mutableStateOf(false)
        private set
    var intervalMinutes by mutableStateOf(SyncScheduler.DEFAULT_INTERVAL)
        private set
    var wifiOnly by mutableStateOf(true)
        private set
    var chargingOnly by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            url = settings.get(WebDavKeys.URL).orEmpty()
            user = settings.get(WebDavKeys.USER).orEmpty()
            password = secureStore.getPassword()
            settings.put(SettingEntity(WebDavKeys.PASSWORD, ""))
            lastSyncAt = settings.get(SyncManager.KEY_LAST_SYNC)?.toLongOrNull()
            autoEnabled = settings.get(SyncScheduler.KEY_AUTO_ENABLED) == "true"
            intervalMinutes = settings.get(SyncScheduler.KEY_INTERVAL)?.toIntOrNull()
                ?: SyncScheduler.DEFAULT_INTERVAL
            wifiOnly = settings.get(SyncScheduler.KEY_WIFI_ONLY) != "false"
            chargingOnly = settings.get(SyncScheduler.KEY_CHARGING_ONLY) == "true"
        }
    }

    fun onUrlChange(value: String) {
        url = value
    }

    fun onUserChange(value: String) {
        user = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    private suspend fun persist() {
        settings.put(SettingEntity(WebDavKeys.URL, url.trim()))
        settings.put(SettingEntity(WebDavKeys.USER, user.trim()))
        settings.put(SettingEntity(WebDavKeys.PASSWORD, ""))
        secureStore.setPassword(password)
    }

    private fun config() = WebDavConfig(url.trim(), user.trim(), password)

    fun updateAutoEnabled(value: Boolean) {
        autoEnabled = value
        viewModelScope.launch {
            settings.put(SettingEntity(SyncScheduler.KEY_AUTO_ENABLED, value.toString()))
            if (value && (url.isBlank() || password.isBlank())) {
                status = "请先填写服务器地址和密码"
            }
            persist()
            syncScheduler.schedulePeriodic()
        }
    }

    fun updateInterval(minutes: Int) {
        intervalMinutes = minutes
        viewModelScope.launch {
            settings.put(SettingEntity(SyncScheduler.KEY_INTERVAL, minutes.toString()))
            syncScheduler.schedulePeriodic()
        }
    }

    fun updateWifiOnly(value: Boolean) {
        wifiOnly = value
        viewModelScope.launch {
            settings.put(SettingEntity(SyncScheduler.KEY_WIFI_ONLY, value.toString()))
            syncScheduler.schedulePeriodic()
        }
    }

    fun updateChargingOnly(value: Boolean) {
        chargingOnly = value
        viewModelScope.launch {
            settings.put(SettingEntity(SyncScheduler.KEY_CHARGING_ONLY, value.toString()))
            syncScheduler.schedulePeriodic()
        }
    }

    fun testConnection() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            persist()
            val result = syncManager.testConnection(config())
            status = result.fold(
                onSuccess = { "连接成功" },
                onFailure = { e -> "连接失败：${e.message ?: e.javaClass.simpleName}" },
            )
            busy = false
        }
    }

    fun syncNow() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            persist()
            status = when (val result = syncManager.sync(config())) {
                is SyncResult.NoChange -> "已是最新，无需同步"
                is SyncResult.Pulled -> "已从云端合并 ${result.mergedRecords} 条"
                is SyncResult.Success -> "同步完成（合并 ${result.mergedRecords} 条）"
                is SyncResult.Failure -> "同步失败：${result.message.ifBlank { result.code }}"
            }
            lastSyncAt = settings.get(SyncManager.KEY_LAST_SYNC)?.toLongOrNull()
            busy = false
        }
    }
}
