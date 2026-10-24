package com.mysecret.intimatetracker.ui.record

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.data.db.PhotoEntity
import com.mysecret.intimatetracker.data.repo.DictionaryKind
import com.mysecret.intimatetracker.data.repo.FormField
import com.mysecret.intimatetracker.data.repo.DictionaryRepository
import com.mysecret.intimatetracker.data.repo.PartnerRepository
import com.mysecret.intimatetracker.data.repo.PhotoRepository
import com.mysecret.intimatetracker.data.repo.SettingsRepository
import com.mysecret.intimatetracker.data.repo.RecordDraft
import com.mysecret.intimatetracker.data.repo.RecordRepository
import com.mysecret.intimatetracker.domain.RecordIcons
import com.mysecret.intimatetracker.domain.withoutDeletedMarker
import com.mysecret.intimatetracker.domain.model.Initiator
import com.mysecret.intimatetracker.domain.model.Protection
import dagger.hilt.android.lifecycle.HiltViewModel
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_PHOTOS = 3

data class RecordForm(
    val timestamp: Long = System.currentTimeMillis(),
    val icon: String? = "🔥",
    val durationMinutes: Int? = null,
    val satisfaction: Int = 0,
    val initiator: Initiator? = null,
    val protection: Protection? = null,
    val myOrgasm: Int = 0,
    val partnerOrgasm: Int = 0,
    val notes: String = "",
    val partnerIds: Set<String> = emptySet(),
    val sexTypeIds: Set<String> = emptySet(),
    val positionIds: Set<String> = emptySet(),
    val toyIds: Set<String> = emptySet(),
    val locationIds: Set<String> = emptySet(),
)

@HiltViewModel
class AddRecordViewModel @Inject constructor(
    private val records: RecordRepository,
    private val partnerRepository: PartnerRepository,
    dictionaryRepository: DictionaryRepository,
    private val photos: PhotoRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recordId: String? = savedStateHandle.get<String>("recordId")?.takeIf { it.isNotBlank() }

    /** 用户手动点过图标后不再自动覆盖。 */
    private var iconManual = false

    val isEditing: Boolean = recordId != null

    var form by mutableStateOf(RecordForm())
        private set

    val visibility = settingsRepository.observeFormVisibility()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FormField.entries.associateWith { true })

    var saving by mutableStateOf(false)
        private set

    /** 保存失败时的提示文案；非空即弹窗，避免异常直接把进程带崩。 */
    var saveError by mutableStateOf<String?>(null)
        private set

    fun dismissSaveError() {
        saveError = null
    }

    var pendingUris by mutableStateOf<List<Uri>>(emptyList())
        private set

    var existingPhotos by mutableStateOf<List<PhotoEntity>>(emptyList())
        private set

    init {
        if (recordId != null) {
            viewModelScope.launch { existingPhotos = photos.photosFor(recordId) }
            viewModelScope.launch {
                records.loadDraft(recordId)?.let { draft ->
                    form = RecordForm(
                        timestamp = draft.timestamp,
                        icon = draft.icon,
                        durationMinutes = draft.durationMinutes,
                        satisfaction = draft.satisfaction ?: 0,
                        initiator = draft.initiator,
                        protection = draft.protection,
                        myOrgasm = draft.myOrgasmCount,
                        partnerOrgasm = draft.partnerOrgasmCount,
                        notes = draft.notes.orEmpty(),
                        partnerIds = draft.partnerIds.toSet(),
                        sexTypeIds = draft.sexTypeIds.toSet(),
                        positionIds = draft.positionIds.toSet(),
                        toyIds = draft.toyIds.toSet(),
                        locationIds = draft.locationIds.toSet(),
                    )
                }
            }
        }
    }

    // 编辑记录时按历史展示：含已删与已归档，界面只显示「当前可用 + 已被本记录选中」的项
    val partners = partnerRepository.observeAllHistory()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            partnerRepository.cachedActive(),
        )

    val sexTypes = dictionaryRepository.observeIncludingDeleted(DictionaryKind.SEX_TYPE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val positions = dictionaryRepository.observeIncludingDeleted(DictionaryKind.POSITION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val toys = dictionaryRepository.observeIncludingDeleted(DictionaryKind.TOY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val locations = dictionaryRepository.observeIncludingDeleted(DictionaryKind.LOCATION)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 返回是否全部接收；超出的部分被丢弃，调用方据此提示。 */
    fun addUris(uris: List<Uri>): Boolean {
        // 每条记录最多 3 张照片
        val room = (MAX_PHOTOS - existingPhotos.size - pendingUris.size).coerceAtLeast(0)
        val accepted = uris.take(room)
        pendingUris = pendingUris + accepted
        return accepted.size == uris.size
    }

    fun removePending(uri: Uri) {
        pendingUris = pendingUris - uri
    }

    /** 简化输入：至少保留 2 个字段可见。 */
    fun setFieldVisible(field: FormField, visible: Boolean) {
        if (!visible && visibility.value.count { it.value } <= 2) return
        viewModelScope.launch {
            settingsRepository.setFieldVisible(field, visible)
        }
    }

    fun update(transform: (RecordForm) -> RecordForm) {
        form = transform(form)
    }

    fun setIcon(icon: String) {
        iconManual = true
        update { it.copy(icon = icon) }
    }

    fun setSatisfaction(stars: Int) {
        update { autoIcon(it.copy(satisfaction = stars)) }
    }

    /** 依据已选类型 / 星级推导图标，用户手动选过则保持原样。 */
    private fun autoIcon(next: RecordForm): RecordForm {
        if (iconManual) return next
        // 已删类型的名字带「（已删除）」后缀，映射图标前先去掉
        val names = next.sexTypeIds.mapNotNull { id ->
            sexTypes.value.firstOrNull { it.id == id }?.name?.withoutDeletedMarker()
        }
        val derived = names.firstNotNullOfOrNull { RecordIcons.TYPE_ICONS[it] }
            ?: when (next.satisfaction) {
                5 -> RecordIcons.SATISFACTION_FIVE
                4 -> RecordIcons.SATISFACTION_FOUR
                else -> null
            }
        return if (derived != null) next.copy(icon = derived) else next
    }

    fun togglePartner(id: String) = update {
        val next = if (id in it.partnerIds) it.partnerIds - id else it.partnerIds + id
        it.copy(partnerIds = next)
    }

    fun toggleSexType(id: String) = update {
        autoIcon(it.copy(sexTypeIds = if (id in it.sexTypeIds) it.sexTypeIds - id else it.sexTypeIds + id))
    }

    fun togglePosition(id: String) = update {
        it.copy(positionIds = if (id in it.positionIds) it.positionIds - id else it.positionIds + id)
    }

    fun toggleToy(id: String) = update {
        it.copy(toyIds = if (id in it.toyIds) it.toyIds - id else it.toyIds + id)
    }

    fun toggleLocation(id: String) = update {
        it.copy(locationIds = if (id in it.locationIds) it.locationIds - id else it.locationIds + id)
    }

    fun delete(onDone: () -> Unit) {
        val id = recordId ?: return
        viewModelScope.launch {
            records.delete(id)
            onDone()
        }
    }

    fun save(onDone: () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                val current = form
                val savedId = records.save(
                    RecordDraft(
                        id = recordId,
                        timestamp = current.timestamp,
                        icon = current.icon,
                        durationMinutes = current.durationMinutes,
                        satisfaction = current.satisfaction.takeIf { it > 0 },
                        initiator = current.initiator,
                        protection = current.protection,
                        myOrgasmCount = current.myOrgasm,
                        partnerOrgasmCount = current.partnerOrgasm,
                        isSolo = current.partnerIds.isEmpty(),
                        notes = current.notes,
                        partnerIds = current.partnerIds.toList(),
                        sexTypeIds = current.sexTypeIds.toList(),
                        positionIds = current.positionIds.toList(),
                        toyIds = current.toyIds.toList(),
                        locationIds = current.locationIds.toList(),
                    ),
                )
                if (pendingUris.isNotEmpty()) {
                    // 记录已经落库：照片导入失败不回滚记录，也不提示「保存失败」（避免误导成重复保存）
                    runCatching { photos.import(savedId, pendingUris) }
                    pendingUris = emptyList()
                }
                onDone()
            } catch (e: Exception) {
                saveError = e.message ?: e.javaClass.simpleName
            } finally {
                saving = false
            }
        }
    }
}

private fun RecordForm.toPayloadJson(): String = JSONObject().apply {
    put("icon", icon ?: "")
    put("duration", durationMinutes ?: -1)
    put("satisfaction", satisfaction)
    put("initiator", initiator?.name ?: "")
    put("protection", protection?.name ?: "")
    put("myOrgasm", myOrgasm)
    put("partnerOrgasm", partnerOrgasm)
    put("notes", notes)
    put("partnerIds", JSONArray(partnerIds.toList()))
    put("sexTypeIds", JSONArray(sexTypeIds.toList()))
    put("locationIds", JSONArray(locationIds.toList()))
    put("positionIds", JSONArray(positionIds.toList()))
}.toString()

private fun RecordForm.withJson(raw: String): RecordForm {
    val json = JSONObject(raw)
    fun array(key: String): Set<String> {
        val value = json.optJSONArray(key) ?: return emptySet()
        return (0 until value.length()).map { value.getString(it) }.toSet()
    }
    return copy(
        icon = json.optString("icon").ifEmpty { null },
        durationMinutes = json.optInt("duration", -1).takeIf { it >= 0 },
        satisfaction = json.optInt("satisfaction", 0),
        initiator = json.optString("initiator").takeIf { it.isNotEmpty() }?.let { Initiator.valueOf(it) },
        protection = json.optString("protection").takeIf { it.isNotEmpty() }?.let { Protection.valueOf(it) },
        myOrgasm = json.optInt("myOrgasm", 0),
        partnerOrgasm = json.optInt("partnerOrgasm", 0),
        notes = json.optString("notes"),
        partnerIds = array("partnerIds"),
        sexTypeIds = array("sexTypeIds"),
        locationIds = array("locationIds"),
        positionIds = array("positionIds"),
    )
}