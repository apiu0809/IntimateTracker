package com.mysecret.intimatetracker.data.repo

import com.mysecret.intimatetracker.data.db.TemplateDao
import com.mysecret.intimatetracker.data.db.TemplateEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepository @Inject constructor(private val dao: TemplateDao) {

    fun observeAll(): Flow<List<TemplateEntity>> = dao.observeAll()

    suspend fun save(name: String, payloadJson: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.upsert(
            TemplateEntity(
                id = id,
                name = name,
                payloadJson = payloadJson,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id
    }

    suspend fun delete(id: String) = dao.softDelete(id, System.currentTimeMillis())
}
