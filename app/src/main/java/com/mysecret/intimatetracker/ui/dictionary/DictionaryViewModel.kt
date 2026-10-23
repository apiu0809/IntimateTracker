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

    fun add(name: String, intensity: Intensity) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.add(kind, name, intensity) }
    }

    fun toggleHidden(item: DictionaryItem) {
        viewModelScope.launch { repository.setHidden(kind, item.id, !item.isHidden) }
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
     * 把 [item] 移到它所在分组内的 [targetIndex]（方案 B：序号全局，展示分段）。
     * 落位后把「你的 + 内置」的完整展示顺序重写为 1..N。
     */
    fun move(item: DictionaryItem, targetIndex: Int) {
        val current = items.value
        val custom = current.filter { !it.isBuiltin }
        val builtin = current.filter { it.isBuiltin }
        val section = if (item.isBuiltin) builtin else custom
        val from = section.indexOfFirst { it.id == item.id }
        if (from < 0) return
        val to = targetIndex.coerceIn(0, section.lastIndex)
        if (to == from) return

        val reordered = section.toMutableList().apply { add(to, removeAt(from)) }
        val ordered = if (item.isBuiltin) custom + reordered else reordered + builtin
        viewModelScope.launch { repository.reorder(kind, ordered.map { it.id }) }
    }
}
