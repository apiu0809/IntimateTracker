package com.mysecret.intimatetracker.data.repo

import com.mysecret.intimatetracker.data.db.PartnerDao
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.sync.SyncScheduler
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
    private val syncScheduler: SyncScheduler,
) {

    private val activeCache = kotlinx.coroutines.flow.MutableStateFlow<List<PartnerEntity>>(emptyList())

    fun observeActive(): Flow<List<PartnerEntity>> = dao.observeActive()
        .onEach { activeCache.value = it }

    /** 最近一次 active 列表，给新建表单做首帧填充，避免选项闪现。 */
    fun cachedActive(): List<PartnerEntity> = activeCache.value

    fun observeArchived(): Flow<List<PartnerEntity>> = dao.observeArchived()

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
        dao.upsert(entity)
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
