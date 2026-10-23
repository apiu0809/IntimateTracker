package com.mysecret.intimatetracker.ui.more

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.data.repo.ExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val exportManager: ExportManager,
) : ViewModel() {

    var busy by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
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
}
