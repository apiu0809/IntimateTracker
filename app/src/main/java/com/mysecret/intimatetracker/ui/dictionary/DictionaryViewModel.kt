package com.mysecret.intimatetracker.ui.dictionary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.data.repo.DictionaryItem
import com.mysecret.intimatetracker.data.repo.DictionaryKind
import com.mysecret.intimatetracker.data.repo.DictionaryRepository
import com.mysecret.intimatetracker.domain.model.Intensity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 重名时暂存的一次保存请求；[duplicate] 是已存在的同名项。 */
data class PendingDictionarySave(
    val item: DictionaryItem?,
    val name: String,
    val note: String,
    val intensity: Intensity,
    val duplicate: DictionaryItem,
)

@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val kind: DictionaryKind = DictionaryKind.fromRoute(savedStateHandle.get<String>("kind"))

    var pendingDelete by mutableStateOf<DictionaryItem?>(null)
        private set

    val items = repository.observe(kind)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 管理页「已删除」区。 */
    val deletedItems = repository.observeDeleted(kind)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 重名时挂起的一次保存，等用户决定「仍要新建 / 去编辑已有」。 */
    var pendingSave by mutableStateOf<PendingDictionarySave?>(null)
        private set

    fun submit(item: DictionaryItem?, name: String, note: String, intensity: Intensity) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val duplicate = repository.findByName(kind, name, item?.id)
            if (duplicate == null) {
                persist(item, name, note, intensity)
            } else {
                pendingSave = PendingDictionarySave(item, name, note, intensity, duplicate)
            }
        }
    }

    /** 重名确认：仍然保存。 */
    fun saveAnyway() {
        val pending = pendingSave ?: return
        pendingSave = null
        persist(pending.item, pending.name, pending.note, pending.intensity)
    }

    fun dismissDuplicate() {
        pendingSave = null
    }

    fun restore(item: DictionaryItem) {
        viewModelScope.launch { repository.restore(kind, item.id) }
    }

    /** 重名命中的是已删项时，直接把它恢复，并把这次填的名称 / 备注 / 强度写回。 */
    fun restoreDuplicate() {
        val pending = pendingSave ?: return
        pendingSave = null
        viewModelScope.launch {
            repository.restoreAndUpdate(
                kind = kind,
                id = pending.duplicate.id,
                name = pending.name,
                note = pending.note,
                intensity = pending.intensity,
            )
        }
    }

    /** 彻底删除确认。 */
    var pendingPurge by mutableStateOf<DictionaryItem?>(null)
        private set

    fun askPurge(item: DictionaryItem) {
        pendingPurge = item
    }

    fun cancelPurge() {
        pendingPurge = null
    }

    fun confirmPurge() {
        val item = pendingPurge ?: return
        pendingPurge = null
        viewModelScope.launch { repository.purge(kind, item.id) }
    }

    private fun persist(item: DictionaryItem?, name: String, note: String, intensity: Intensity) {
        viewModelScope.launch {
            if (item == null) repository.add(kind, name, note, intensity)
            else repository.update(kind, item.id, name, note, intensity)
        }
    }

    fun askDelete(item: DictionaryItem) {
        pendingDelete = item
    }

    fun cancelDelete() {
        pendingDelete = null
    }

    fun confirmDelete() {
        val item = pendingDelete ?: return
        pendingDelete = null
        viewModelScope.launch { repository.delete(kind, item.id) }
    }

    /**
     * 把 [item] 移到列表的 [targetIndex]（列表为平铺顺序，内置项与自建项混排）。
     * 落位后把完整展示顺序重写为 1..N。
     */
    fun move(item: DictionaryItem, targetIndex: Int) {
        val current = items.value
        val from = current.indexOfFirst { it.id == item.id }
        if (from < 0) return
        val to = targetIndex.coerceIn(0, current.lastIndex)
        if (to == from) return

        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        viewModelScope.launch { repository.reorder(kind, reordered.map { it.id }) }
    }
}
