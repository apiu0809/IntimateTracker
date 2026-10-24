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
            // 详情同样按「历史真相」：已删的字典项与伴侣照常展示，名字后加「（已删除）」
            val typeNames = db.sexTypeDao().all().associate { it.id to it.name.withDeletedMarker(it.deletedAt != null) }
            val partnerNames = db.partnerDao().allForSync().associate { it.id to it.name.withDeletedMarker(it.deletedAt != null) }
            val locationNames = db.locationDao().all().associate { it.id to it.name.withDeletedMarker(it.deletedAt != null) }
            val positionNames = db.positionDao().all().associate { it.id to it.name.withDeletedMarker(it.deletedAt != null) }
            val toyNames = db.toyDao().all().associate { it.id to it.name.withDeletedMarker(it.deletedAt != null) }

            detail = RecordDetail(
                record = record,
                types = db.recordDao().sexTypeIds(recordId).mapNotNull { typeNames[it] },
                partners = db.recordDao().partnerIds(recordId).mapNotNull { partnerNames[it] },
                locations = db.recordDao().locationIds(recordId).mapNotNull { locationNames[it] },
                positions = db.recordDao().positionIds(recordId).mapNotNull { positionNames[it] },
                toys = db.recordDao().toyIds(recordId).mapNotNull { toyNames[it] },
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
