package com.mysecret.intimatetracker.data.repo

import androidx.room.withTransaction
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.CalendarEntry
import com.mysecret.intimatetracker.data.db.RecordEntity
import com.mysecret.intimatetracker.data.db.RecordListRow
import com.mysecret.intimatetracker.data.db.RecordLocationCrossRef
import com.mysecret.intimatetracker.data.db.RecordPartnerCrossRef
import com.mysecret.intimatetracker.data.db.RecordPositionCrossRef
import com.mysecret.intimatetracker.data.db.RecordSexTypeCrossRef
import com.mysecret.intimatetracker.data.db.RecordToyCrossRef
import com.mysecret.intimatetracker.data.sync.SyncTrigger
import com.mysecret.intimatetracker.domain.RhythmSettings
import com.mysecret.intimatetracker.domain.model.Initiator
import com.mysecret.intimatetracker.domain.model.Protection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class RecordDraft(
    val id: String? = null,
    val timestamp: Long,
    val icon: String? = null,
    val durationMinutes: Int? = null,
    val satisfaction: Int? = null,
    val initiator: Initiator? = null,
    val protection: Protection? = null,
    val myOrgasmCount: Int = 0,
    val partnerOrgasmCount: Int = 0,
    val isSolo: Boolean = false,
    val notes: String? = null,
    val partnerIds: List<String> = emptyList(),
    val sexTypeIds: List<String> = emptyList(),
    val positionIds: List<String> = emptyList(),
    val toyIds: List<String> = emptyList(),
    val locationIds: List<String> = emptyList(),
)

data class RhythmStats(
    val daysSinceLast: Int?,
    val lastDate: java.time.LocalDate? = null,
    val longestGapDays: Int?,
    val usualRange: IntRange?,
    val weekdayCounts: List<Int> = List(7) { 0 },
    val recordsInRange: Int = 0,
)

@Singleton
class RecordRepository @Inject constructor(
    private val db: AppDatabase,
    private val syncScheduler: SyncTrigger,
) {

    fun observeBetween(from: Long, to: Long): Flow<List<RecordListRow>> =
        db.recordDao().observeListBetween(from, to)

    fun observeCalendar(from: Long, to: Long): Flow<List<CalendarEntry>> =
        db.recordDao().observeCalendar(from, to)

    suspend fun find(id: String): RecordEntity? = db.recordDao().find(id)

    suspend fun save(draft: RecordDraft): String {
        val id = db.withTransaction {
            val dao = db.recordDao()
            val now = System.currentTimeMillis()
            val existing = draft.id?.takeIf { it.isNotBlank() }?.let { dao.find(it) }
            val id = existing?.id ?: UUID.randomUUID().toString()
            dao.upsert(
                RecordEntity(
                    id = id,
                    timestamp = draft.timestamp,
                    icon = draft.icon,
                    durationMinutes = draft.durationMinutes,
                    satisfaction = draft.satisfaction,
                    initiator = draft.initiator,
                    protectionUsed = draft.protection,
                    myOrgasmCount = draft.myOrgasmCount,
                    partnerOrgasmCount = draft.partnerOrgasmCount,
                    isSolo = draft.isSolo || draft.partnerIds.isEmpty(),
                    notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            dao.clearPartners(id)
            dao.clearSexTypes(id)
            dao.clearPositions(id)
            dao.clearToys(id)
            dao.clearLocations(id)
            draft.partnerIds.forEach { dao.linkPartner(RecordPartnerCrossRef(recordId = id, partnerId = it)) }
            draft.sexTypeIds.forEach { dao.linkSexType(RecordSexTypeCrossRef(recordId = id, sexTypeId = it)) }
            draft.positionIds.forEach { dao.linkPosition(RecordPositionCrossRef(recordId = id, positionId = it)) }
            draft.toyIds.forEach { dao.linkToy(RecordToyCrossRef(recordId = id, toyId = it)) }
            draft.locationIds.forEach { dao.linkLocation(RecordLocationCrossRef(recordId = id, locationId = it)) }
            id
        }
        syncScheduler.onLocalChange()
        return id
    }

    suspend fun loadDraft(id: String): RecordDraft? {
        val dao = db.recordDao()
        val record = dao.find(id) ?: return null
        return RecordDraft(
            id = record.id,
            timestamp = record.timestamp,
            icon = record.icon,
            durationMinutes = record.durationMinutes,
            satisfaction = record.satisfaction,
            initiator = record.initiator,
            protection = record.protectionUsed,
            myOrgasmCount = record.myOrgasmCount,
            partnerOrgasmCount = record.partnerOrgasmCount,
            isSolo = record.isSolo,
            notes = record.notes.orEmpty(),
            partnerIds = dao.partnerIds(id),
            sexTypeIds = dao.sexTypeIds(id),
            positionIds = dao.positionIds(id),
            toyIds = dao.toyIds(id),
            locationIds = dao.locationIds(id),
        )
    }
    suspend fun delete(id: String) {
        db.recordDao().softDelete(id, System.currentTimeMillis())
        syncScheduler.onLocalChange()
    }

    suspend fun rhythm(
        settings: RhythmSettings = RhythmSettings(),
        now: Long = System.currentTimeMillis(),
    ): RhythmStats = RhythmCalculator.compute(
        entries = db.recordDao().observeCalendar(0L, Long.MAX_VALUE).first(),
        settings = settings,
        now = now,
    )
}
