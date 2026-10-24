package com.mysecret.intimatetracker.data.repo

import androidx.room.withTransaction
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.PartnerDao
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.db.TombstoneEntity
import com.mysecret.intimatetracker.data.sync.SyncTrigger
import com.mysecret.intimatetracker.domain.model.Gender
import com.mysecret.intimatetracker.domain.model.RelationshipType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartnerRepository @Inject constructor(
    private val dao: PartnerDao,
    private val db: AppDatabase,
    private val syncScheduler: SyncTrigger,
) {

    private val activeCache = kotlinx.coroutines.flow.MutableStateFlow<List<PartnerEntity>>(emptyList())

    fun observeActive(): Flow<List<PartnerEntity>> = dao.observeActive()
        .onEach { activeCache.value = it }

    /** 最近一次 active 列表，给新建表单做首帧填充，避免选项闪现。 */
    fun cachedActive(): List<PartnerEntity> = activeCache.value

    fun observeArchived(): Flow<List<PartnerEntity>> = dao.observeArchived()

    /** 含软删与归档：仅「编辑记录」用来展示历史选项。 */
    fun observeAllHistory(): Flow<List<PartnerEntity>> = dao.observeAllHistory()

    /** 已软删，管理页「已删除」区用来恢复。 */
    fun observeDeleted(): Flow<List<PartnerEntity>> = dao.observeDeleted()

    /**
     * 新增 / 改名前的重名检查：[excludeId] 用于编辑时排除自己。
     * **已删除伴侣也算重名**（删除后重建同名会让历史记录割裂），命中已删项时由界面提示恢复。
     */
    suspend fun findByName(name: String, excludeId: String? = null): PartnerEntity? {
        val target = name.trim()
        if (target.isEmpty()) return null
        val matches = dao.allForSync().filter {
            it.id != excludeId && it.name.trim().equals(target, ignoreCase = true)
        }
        return matches.firstOrNull { it.deletedAt == null } ?: matches.firstOrNull()
    }

    /** 彻底删除：物理删行 + 写墓碑，避免被远端旧备份带回来。 */
    suspend fun purge(id: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.hardDelete(id)
            db.tombstoneDao().put(TombstoneEntity(tableName = "partner", rowId = id, deletedAt = now))
        }
        syncScheduler.onLocalChange()
    }

    /**
     * 恢复软删伴侣：清 `deleted_at` 并取消归档，
     * 保证「恢复」之后它真的回到活跃列表，而不是悄悄留在「已归档」里。
     */
    suspend fun restore(id: String) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = dao.findIncludingDeleted(id) ?: return@withTransaction
            dao.upsert(existing.copy(deletedAt = null, isArchived = false, updatedAt = now))
        }
        syncScheduler.onLocalChange()
    }

    /**
     * 重名时「恢复它」：清软删 + 取消归档，同时把用户刚填的资料写回，
     * 避免恢复出旧数据而把新输入（名字 / 性别 / 关系 / 备注 / 头像）静默丢掉。
     */
    suspend fun restoreAndUpdate(
        id: String,
        name: String,
        gender: Gender,
        relationship: RelationshipType,
        notes: String?,
        avatarPath: String?,
    ) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = dao.findIncludingDeleted(id) ?: return@withTransaction
            val restored = existing.copy(
                name = name.trim(),
                gender = gender,
                relationshipType = relationship,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                avatarPath = avatarPath ?: existing.avatarPath,
                isArchived = false,
                deletedAt = null,
                updatedAt = now,
            )
            dao.upsert(restored)
            if (existing.name != restored.name) {
                db.recordDao().relabelPartner(restored.id, restored.name)
            }
        }
        syncScheduler.onLocalChange()
    }

    suspend fun find(id: String): PartnerEntity? = dao.find(id)

    suspend fun save(
        id: String?,
        name: String,
        gender: Gender,
        relationship: RelationshipType,
        notes: String?,
        avatarPath: String? = null,
    ): String {
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.find(it) }
        val entity = PartnerEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            gender = gender,
            relationshipType = relationship,
            avatarPath = avatarPath ?: existing?.avatarPath,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            isArchived = existing?.isArchived ?: false,
            sortOrder = existing?.sortOrder ?: 0,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        db.withTransaction {
            dao.upsert(entity)
            // 改名同步刷新历史关联的快照：以后即使这个伴侣被彻底删除，
            // 历史记录展示的也是「最后一次已知的名字」，而不是旧名字。
            if (existing == null || existing.name != entity.name) {
                db.recordDao().relabelPartner(entity.id, entity.name)
            }
        }
        syncScheduler.onLocalChange()
        return entity.id
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        dao.setArchived(id, archived, System.currentTimeMillis())
        syncScheduler.onLocalChange()
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
        syncScheduler.onLocalChange()
    }

    suspend fun countActive(): Int = dao.countActive()
}
