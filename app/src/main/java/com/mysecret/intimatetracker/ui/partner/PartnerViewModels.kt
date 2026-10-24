package com.mysecret.intimatetracker.ui.partner

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.repo.PartnerRepository
import com.mysecret.intimatetracker.domain.model.Gender
import com.mysecret.intimatetracker.domain.model.RelationshipType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PartnerListViewModel @Inject constructor(private val repo: PartnerRepository) : ViewModel() {
    val partners = kotlinx.coroutines.flow.combine(
        repo.observeActive(),
        repo.observeArchived(),
        repo.observeDeleted(),
    ) { active, archived, deleted -> PartnerListState(active, archived, deleted) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PartnerListState())

    fun restore(id: String) {
        viewModelScope.launch { repo.restore(id) }
    }

    /** 彻底删除确认。 */
    var pendingPurge by mutableStateOf<PartnerEntity?>(null)
        private set

    fun askPurge(partner: PartnerEntity) {
        pendingPurge = partner
    }

    fun cancelPurge() {
        pendingPurge = null
    }

    fun confirmPurge() {
        val target = pendingPurge ?: return
        pendingPurge = null
        viewModelScope.launch { repo.purge(target.id) }
    }
}

data class PartnerListState(
    val active: List<com.mysecret.intimatetracker.data.db.PartnerEntity> = emptyList(),
    val archived: List<com.mysecret.intimatetracker.data.db.PartnerEntity> = emptyList(),
    val deleted: List<com.mysecret.intimatetracker.data.db.PartnerEntity> = emptyList(),
)

@HiltViewModel
class PartnerEditViewModel @Inject constructor(
    private val repo: PartnerRepository,
    private val photos: com.mysecret.intimatetracker.data.repo.PhotoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val partnerId: String? = savedStateHandle.get<String>("partnerId")

    var name by mutableStateOf("")
        private set
    var gender by mutableStateOf(Gender.UNKNOWN)
        private set
    var relationship by mutableStateOf(RelationshipType.OTHER)
        private set
    var notes by mutableStateOf("")
        private set
    var avatarPath by mutableStateOf<String?>(null)
        private set
    var importingAvatar by mutableStateOf(false)
        private set
    var isArchived by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(partnerId == null)
        private set

    val isNew: Boolean = partnerId == null

    init {
        if (partnerId != null) {
            viewModelScope.launch {
                repo.find(partnerId)?.let { partner ->
                    name = partner.name
                    gender = partner.gender
                    relationship = partner.relationshipType
                    notes = partner.notes.orEmpty()
                    avatarPath = partner.avatarPath
                    isArchived = partner.isArchived
                }
                loaded = true
            }
        }
    }

    fun onNameChange(value: String) {
        name = value
    }

    fun onGenderChange(value: Gender) {
        gender = value
    }

    fun onRelationshipChange(value: RelationshipType) {
        relationship = value
    }

    fun onNotesChange(value: String) {
        notes = value
    }

    fun setAvatar(uri: Uri) {
        if (importingAvatar) return
        importingAvatar = true
        viewModelScope.launch {
            runCatching { photos.importAvatar(uri) }.onSuccess { avatarPath = it }
            importingAvatar = false
        }
    }

    fun clearAvatar() {
        avatarPath = null
    }

    /** 重名时提示；非空表示有待确认的同名伴侣（可能已被删除）。 */
    var duplicate by mutableStateOf<PartnerEntity?>(null)
        private set

    private var pendingDone: (() -> Unit)? = null

    fun save(onDone: () -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val found = repo.findByName(name, excludeId = partnerId)
            if (found == null) {
                persist()
                onDone()
            } else {
                duplicate = found
                pendingDone = onDone
            }
        }
    }

    /** 重名确认：仍然保存。 */
    fun saveAnyway() {
        val done = pendingDone
        duplicate = null
        pendingDone = null
        viewModelScope.launch {
            persist()
            done?.invoke()
        }
    }

    /** 重名命中的是已删伴侣时，直接恢复它，并把这次填的资料一并写回。 */
    fun restoreDuplicate(onDone: () -> Unit) {
        val found = duplicate ?: return
        duplicate = null
        pendingDone = null
        viewModelScope.launch {
            repo.restoreAndUpdate(
                id = found.id,
                name = name,
                gender = gender,
                relationship = relationship,
                notes = notes,
                avatarPath = avatarPath,
            )
            onDone()
        }
    }

    fun dismissDuplicate() {
        duplicate = null
        pendingDone = null
    }

    private suspend fun persist() {
        repo.save(partnerId, name, gender, relationship, notes, avatarPath)
    }

    fun toggleArchive(onDone: () -> Unit) {
        val id = partnerId ?: return
        viewModelScope.launch {
            repo.setArchived(id, !isArchived)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = partnerId ?: return
        viewModelScope.launch {
            repo.delete(id)
            onDone()
        }
    }
}
