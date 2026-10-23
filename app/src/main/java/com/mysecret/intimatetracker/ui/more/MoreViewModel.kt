package com.mysecret.intimatetracker.ui.more

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.data.repo.ExportManager
import com.mysecret.intimatetracker.data.UpdateChecker
import com.mysecret.intimatetracker.data.UpdateInfo
import com.mysecret.intimatetracker.data.UpdateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 检查更新的界面状态。 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Failed(val reason: String) : UpdateUiState
}

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val exportManager: ExportManager,
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    var busy by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var updateState by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
        private set

    fun export() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            message = runCatching { exportManager.exportZip() }
                .fold(
                    onSuccess = { name -> if (name == null) "导出失败：无法写入下载目录" else "已导出到下载/IntimateTracker/$name" },
                    onFailure = { "导出失败：${it.message}" },
                )
            busy = false
        }
    }

    fun clearAll() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            message = runCatching { exportManager.clearAll() }
                .fold(onSuccess = { "已清空所有数据" }, onFailure = { "清空失败：${it.message}" })
            busy = false
        }
    }

    fun dismissMessage() {
        message = null
    }

    /** 检查 GitHub Releases 上是否有比 [currentVersion] 更新的版本。 */
    fun checkUpdate(currentVersion: String) {
        if (updateState is UpdateUiState.Checking) return
        updateState = UpdateUiState.Checking
        viewModelScope.launch {
            updateState = when (val result = updateChecker.check(currentVersion)) {
                is UpdateResult.Available -> UpdateUiState.Available(result.info)
                UpdateResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateResult.Failed -> UpdateUiState.Failed(result.reason)
            }
        }
    }

    fun clearUpdateState() {
        updateState = UpdateUiState.Idle
    }
}
