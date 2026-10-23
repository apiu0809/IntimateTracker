package com.mysecret.intimatetracker.data.repo

import androidx.room.withTransaction
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.LocationEntity
import com.mysecret.intimatetracker.data.db.PositionEntity
import com.mysecret.intimatetracker.data.db.SexTypeEntity
import com.mysecret.intimatetracker.data.db.ToyEntity
import com.mysecret.intimatetracker.data.sync.SyncScheduler
import com.mysecret.intimatetracker.domain.model.Intensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class DictionaryKind(val route: String) {
    SEX_TYPE("sexType"),
    POSITION("position"),
    TOY("toy"),
    LOCATION("location"),
    ;

    companion object {
        fun fromRoute(route: String?): DictionaryKind =
            entries.firstOrNull { it.route == route } ?: SEX_TYPE
    }
}

data class DictionaryItem(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val intensity: Intensity? = null,
    val isBuiltin: Boolean,
    val isHidden: Boolean,
)

@Singleton
class DictionaryRepository @Inject constructor(
    private val db: AppDatabase,
    private val syncScheduler: SyncScheduler,
) {

    fun observe(kind: DictionaryKind): Flow<List<DictionaryItem>> = when (kind) {
        DictionaryKind.SEX_TYPE -> db.sexTypeDao().observeAll().map { list ->
            list.map { DictionaryItem(it.id, it.name, null, null, it.isBuiltin, it.isHidden) }
        }

        DictionaryKind.POSITION -> db.positionDao().observeAll().map { list ->
            list.map { DictionaryItem(it.id, it.name, null, it.intensity, it.isBuiltin, it.isHidden) }
        }

        DictionaryKind.TOY -> db.toyDao().observeAll().map { list ->
            list.map { DictionaryItem(it.id, it.name, null, null, it.isBuiltin, it.isHidden) }
        }

        DictionaryKind.LOCATION -> db.locationDao().observeAll().map { list ->
            list.map { DictionaryItem(it.id, it.name, null, null, it.isBuiltin, it.isHidden) }
        }
    }

    suspend fun add(kind: DictionaryKind, name: String, intensity: Intensity = Intensity.MEDIUM) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val sort = when (kind) {
            DictionaryKind.SEX_TYPE -> db.sexTypeDao().maxSortOrder() + 1
            DictionaryKind.POSITION -> db.positionDao().maxSortOrder() + 1
            DictionaryKind.TOY -> db.toyDao().maxSortOrder() + 1
            DictionaryKind.LOCATION -> db.locationDao().maxSortOrder() + 1
        }
        when (kind) {
            DictionaryKind.SEX_TYPE -> db.sexTypeDao().upsert(
                SexTypeEntity(id = id, name = name.trim(), sortOrder = sort, createdAt = now, updatedAt = now),
            )

            DictionaryKind.POSITION -> db.positionDao().upsert(
                PositionEntity(id = id, name = name.trim(), intensity = intensity, sortOrder = sort, createdAt = now, updatedAt = now),
            )

            DictionaryKind.TOY -> db.toyDao().upsert(
                ToyEntity(id = id, name = name.trim(), sortOrder = sort, createdAt = now, updatedAt = now),
            )

            DictionaryKind.LOCATION -> db.locationDao().upsert(
                LocationEntity(id = id, name = name.trim(), sortOrder = sort, createdAt = now, updatedAt = now),
            )
        }
        syncScheduler.onLocalChange()
    }

    suspend fun setHidden(kind: DictionaryKind, id: String, hidden: Boolean) {
        val now = System.currentTimeMillis()
        when (kind) {
            DictionaryKind.SEX_TYPE -> db.sexTypeDao().setHidden(id, hidden, now)
            DictionaryKind.POSITION -> db.positionDao().setHidden(id, hidden, now)
            DictionaryKind.TOY -> db.toyDao().setHidden(id, hidden, now)
            DictionaryKind.LOCATION -> db.locationDao().setHidden(id, hidden, now)
        }
        syncScheduler.onLocalChange()
    }

    suspend fun delete(kind: DictionaryKind, id: String) {
        val now = System.currentTimeMillis()
        when (kind) {
            DictionaryKind.SEX_TYPE -> db.sexTypeDao().softDelete(id, now)
            DictionaryKind.POSITION -> db.positionDao().softDelete(id, now)
            DictionaryKind.TOY -> db.toyDao().softDelete(id, now)
            DictionaryKind.LOCATION -> db.locationDao().softDelete(id, now)
        }
        syncScheduler.onLocalChange()
    }

    /**
     * 按 [orderedIds] 的顺序重排字典项（方案 B）。
     *
     * 自建项与内置项共用同一套全局 `sort_order`：传入的列表就是「你的 → 内置」的展示顺序，
     * 依次写为 1..N，保证序号连续且不重复。软删项不在列表里，保留旧序号，不影响展示。
     */
    suspend fun reorder(kind: DictionaryKind, orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                val order = index + 1
                when (kind) {
                    DictionaryKind.SEX_TYPE -> db.sexTypeDao().setSortOrder(id, order, now)
                    DictionaryKind.POSITION -> db.positionDao().setSortOrder(id, order, now)
                    DictionaryKind.TOY -> db.toyDao().setSortOrder(id, order, now)
                    DictionaryKind.LOCATION -> db.locationDao().setSortOrder(id, order, now)
                }
            }
        }
        syncScheduler.onLocalChange()
    }
}
