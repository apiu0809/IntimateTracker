package com.mysecret.intimatetracker.ui.record

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.PhotoEntity
import com.mysecret.intimatetracker.data.db.RecordEntity
import com.mysecret.intimatetracker.domain.withDeletedMarker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordDetail(
    val record: RecordEntity,
    val types: List<String>,
    val partners: List<String>,
    val locations: List<String>,
    val positions: List<String>,
    val toys: List<String>,
    val photos: List<PhotoEntity>,
)

@HiltViewModel
class RecordDetailViewModel @Inject constructor(
    private val db: AppDatabase,
    @ApplicationContext private val context: android.content.Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recordId: String = savedStateHandle.get<String>("recordId").orEmpty()

    var detail by mutableStateOf<RecordDetail?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val record = db.recordDao().find(recordId) ?: return@launch
            // 详情按「历史真相」：父行还在就用当前名字，父行被彻底删除就用关联快照，
            // 两种情况只要父行不可用就标「（已删除）」，关联本身永远不会消失。
            val dao = db.recordDao()
            detail = RecordDetail(
                record = record,
                types = dao.sexTypeLinkNames(recordId).mapNotNull { it.name?.withDeletedMarker(it.deleted) },
                partners = dao.partnerLinkNames(recordId).mapNotNull { it.name?.withDeletedMarker(it.deleted) },
                locations = dao.locationLinkNames(recordId).mapNotNull { it.name?.withDeletedMarker(it.deleted) },
                positions = dao.positionLinkNames(recordId).mapNotNull { it.name?.withDeletedMarker(it.deleted) },
                toys = dao.toyLinkNames(recordId).mapNotNull { it.name?.withDeletedMarker(it.deleted) },
                photos = db.photoDao().byRecord(recordId),
            )
        }
    }

    /** 删除单张照片：软删数据库行 + 删除本地文件，然后刷新详情。 */
    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            db.photoDao().softDelete(photo.id, System.currentTimeMillis())
            runCatching { java.io.File(context.filesDir, photo.relPath).delete() }
            load()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            db.recordDao().softDelete(recordId, System.currentTimeMillis())
            onDone()
        }
    }
}
