package com.mysecret.intimatetracker.data.repo

import androidx.room.withTransaction
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.LocationEntity
import com.mysecret.intimatetracker.data.db.PositionEntity
import com.mysecret.intimatetracker.data.db.SexTypeEntity
import com.mysecret.intimatetracker.data.db.TombstoneEntity
import com.mysecret.intimatetracker.data.db.ToyEntity
import com.mysecret.intimatetracker.data.sync.SyncTrigger
import com.mysecret.intimatetracker.domain.model.Intensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class DictionaryKind(val route: String, val table: String) {
    SEX_TYPE("sexType", "sex_type"),
    POSITION("position", "position"),
    TOY("toy", "toy"),
    LOCATION("location", "location"),
    ;

    companion object {
        fun fromRoute(route: String?): DictionaryKind =
            entries.firstOrNull { it.route == route } ?: SEX_TYPE
    }
}

/**
 * 字典项的统一视图。
 *
 * 四类字典共用一套「名称 + 备注 + 强度（仅姿势）」结构：
 * 名称是记录表单里唯一展示的字段；备注只在管理页出现；强度只属于姿势。
 */
data class DictionaryItem(
    val id: String,
    val name: String,
    val note: String? = null,
    val intensity: Intensity? = null,
    /** 已软删（只用于「编辑记录」时把历史选项也显示出来）。 */
    val deleted: Boolean = false,
)

@Singleton
class DictionaryRepository @Inject constructor(
    private val db: AppDatabase,
    private val syncScheduler: SyncTrigger,
) {

    fun observe(kind: DictionaryKind): Flow<List<DictionaryItem>> = when (kind) {
        DictionaryKind.SEX_TYPE -> db.sexTypeDao().observeAll().map { list ->
            list.map { DictionaryItem(it.id, it.name, it.notes) }
        }

        DictionaryKind.POSITION -> db.positionDao().observeAll().map { list ->
            list.map { DictionaryItem(it.id, it.name, it.notes, it.intensity) }
        }

        DictionaryKind.TOY -> db.toyDao().observeAll().map { list ->
            list.map { DictionaryItem(it.id, it.name, it.notes) }
        }

        DictionaryKind.LOCATION -> db.locationDao().observeAll().map { list ->
            list.map { DictionaryItem(it.id, it.name, it.notes) }
        }
    }

    /**
     * 含软删的全量列表：仅供「编辑记录」把已删选项也展示出来（历史真相），
     * 管理页与新建记录仍用 [observe]（只看未删项）。
     */
    fun observeIncludingDeleted(kind: DictionaryKind): Flow<List<DictionaryItem>> = when (kind) {
        DictionaryKind.SEX_TYPE -> db.sexTypeDao().observeAllIncludingDeleted().map { list ->
            list.map { DictionaryItem(it.id, it.name, it.notes, null, it.deletedAt != null) }
        }

        DictionaryKind.POSITION -> db.positionDao().observeAllIncludingDeleted().map { list ->
            list.map { DictionaryItem(it.id, it.name, it.notes, it.intensity, it.deletedAt != null) }
        }

        DictionaryKind.TOY -> db.toyDao().observeAllIncludingDeleted().map { list ->
            list.map { DictionaryItem(it.id, it.name, it.notes, null, it.deletedAt != null) }
        }

        DictionaryKind.LOCATION -> db.locationDao().observeAllIncludingDeleted().map { list ->
            list.map { DictionaryItem(it.id, it.name, it.notes, null, it.deletedAt != null) }
        }
    }

    /** 管理页「已删除」区：只列软删项，用于恢复。 */
    fun observeDeleted(kind: DictionaryKind): Flow<List<DictionaryItem>> =
        observeIncludingDeleted(kind).map { list -> list.filter { it.deleted } }

    /**
     * 新增 / 改名前的重名检查：[excludeId] 用于编辑时排除自己。
     * **已删除项也算重名**（删除后重建同名会造成历史割裂），命中已删项时由界面提示恢复。
     */
    suspend fun findByName(
        kind: DictionaryKind,
        name: String,
        excludeId: String? = null,
    ): DictionaryItem? {
        val target = name.trim()
        if (target.isEmpty()) return null
        val matches = observeIncludingDeleted(kind).first().filter {
            it.id != excludeId && it.name.trim().equals(target, ignoreCase = true)
        }
        return matches.firstOrNull { !it.deleted } ?: matches.firstOrNull()
    }

    /** 空字符串一律存 null，避免管理页出现没有内容的「说明」行。 */
    private fun String?.normalized(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    suspend fun add(
        kind: DictionaryKind,
        name: String,
        note: String? = null,
        intensity: Intensity = Intensity.VERY_LOW,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val notes = note.normalized()
        val sort = when (kind) {
            DictionaryKind.SEX_TYPE -> db.sexTypeDao().maxSortOrder() + 1
            DictionaryKind.POSITION -> db.positionDao().maxSortOrder() + 1
            DictionaryKind.TOY -> db.toyDao().maxSortOrder() + 1
            DictionaryKind.LOCATION -> db.locationDao().maxSortOrder() + 1
        }
        when (kind) {
            DictionaryKind.SEX_TYPE -> db.sexTypeDao().upsert(
                SexTypeEntity(id = id, name = trimmed, notes = notes, sortOrder = sort, createdAt = now, updatedAt = now),
            )

            DictionaryKind.POSITION -> db.positionDao().upsert(
                PositionEntity(
                    id = id,
                    name = trimmed,
                    intensity = intensity,
                    notes = notes,
                    sortOrder = sort,
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            DictionaryKind.TOY -> db.toyDao().upsert(
                ToyEntity(id = id, name = trimmed, notes = notes, sortOrder = sort, createdAt = now, updatedAt = now),
            )

            DictionaryKind.LOCATION -> db.locationDao().upsert(
                LocationEntity(id = id, name = trimmed, notes = notes, sortOrder = sort, createdAt = now, updatedAt = now),
            )
        }
        syncScheduler.onLocalChange()
    }

    /**
     * 改写已有字典项的名称 / 说明 / 强度（编辑弹窗用）。
     *
     * 实现上先读回原行再 `copy`，因此排序与内置标记都不受影响；
     * 软删的行不会被 `byId` 读到，等于「删掉的项改不了」。
     */
    suspend fun update(
        kind: DictionaryKind,
        id: String,
        name: String,
        note: String? = null,
        intensity: Intensity = Intensity.VERY_LOW,
    ) {
        // 字典行与历史关联的 label 快照必须一起改，避免只改一半
        val changed = db.withTransaction {
            updateInternal(kind, id, name, note, intensity, System.currentTimeMillis())
        }
        if (changed) syncScheduler.onLocalChange()
    }

    /** 返回是否真的写到了行（行不存在或被软删时为 false）。 */
    private suspend fun updateInternal(
        kind: DictionaryKind,
        id: String,
        name: String,
        note: String?,
        intensity: Intensity,
        now: Long,
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val notes = note.normalized()
        val written: Boolean? = when (kind) {
            DictionaryKind.SEX_TYPE -> db.sexTypeDao().byId(id)?.let {
                db.sexTypeDao().upsert(it.copy(name = trimmed, notes = notes, updatedAt = now))
                db.recordDao().relabelSexType(id, trimmed)
                true
            }

            DictionaryKind.POSITION -> db.positionDao().byId(id)?.let {
                db.positionDao().upsert(
                    it.copy(name = trimmed, notes = notes, intensity = intensity, updatedAt = now),
                )
                db.recordDao().relabelPosition(id, trimmed)
                true
            }

            DictionaryKind.TOY -> db.toyDao().byId(id)?.let {
                db.toyDao().upsert(it.copy(name = trimmed, notes = notes, updatedAt = now))
                db.recordDao().relabelToy(id, trimmed)
                true
            }

            DictionaryKind.LOCATION -> db.locationDao().byId(id)?.let {
                db.locationDao().upsert(it.copy(name = trimmed, notes = notes, updatedAt = now))
                db.recordDao().relabelLocation(id, trimmed)
                true
            }
        }
        return written == true
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
     * 彻底删除：物理删行 + 写墓碑，避免被远端旧备份带回来。
     *
     * 只删字典项自己（名称 / 备注 / 强度）；记录与它的关联行仍在，并带着 label 快照，
     * 历史记录、统计、编辑表单照常显示（标「（已删除）」）。这是有意的「历史真相」口径。
     */
    suspend fun purge(kind: DictionaryKind, id: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            when (kind) {
                DictionaryKind.SEX_TYPE -> db.sexTypeDao().hardDelete(id)
                DictionaryKind.POSITION -> db.positionDao().hardDelete(id)
                DictionaryKind.TOY -> db.toyDao().hardDelete(id)
                DictionaryKind.LOCATION -> db.locationDao().hardDelete(id)
            }
            db.tombstoneDao().put(TombstoneEntity(tableName = kind.table, rowId = id, deletedAt = now))
        }
        syncScheduler.onLocalChange()
    }

    /** 恢复软删项（管理页「已删除」区）：清 deleted_at，并把序号追加到列表末尾。 */
    suspend fun restore(kind: DictionaryKind, id: String) {
        val now = System.currentTimeMillis()
        db.withTransaction { restoreInternal(kind, id, now) }
        syncScheduler.onLocalChange()
    }

    /**
     * 重名时「恢复它」：清软删，同时把用户刚填的名称 / 备注 / 强度写回，
     * 避免恢复出旧数据而把新输入静默丢掉。
     */
    suspend fun restoreAndUpdate(
        kind: DictionaryKind,
        id: String,
        name: String,
        note: String? = null,
        intensity: Intensity = Intensity.VERY_LOW,
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            restoreInternal(kind, id, now)
            updateInternal(kind, id, name, note, intensity, now)
        }
        syncScheduler.onLocalChange()
    }

    private suspend fun restoreInternal(kind: DictionaryKind, id: String, now: Long) {
        when (kind) {
            DictionaryKind.SEX_TYPE -> db.sexTypeDao().restore(id, db.sexTypeDao().maxSortOrder() + 1, now)
            DictionaryKind.POSITION -> db.positionDao().restore(id, db.positionDao().maxSortOrder() + 1, now)
            DictionaryKind.TOY -> db.toyDao().restore(id, db.toyDao().maxSortOrder() + 1, now)
            DictionaryKind.LOCATION -> db.locationDao().restore(id, db.locationDao().maxSortOrder() + 1, now)
        }
    }

    /**
     * 按 [orderedIds] 的顺序重排字典项：传入的列表就是完整展示顺序，
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
