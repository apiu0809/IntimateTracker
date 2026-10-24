package com.mysecret.intimatetracker.data.repo

import androidx.room.withTransaction
import com.mysecret.intimatetracker.data.db.AppDatabase
import com.mysecret.intimatetracker.data.db.CalendarEntry
import com.mysecret.intimatetracker.data.db.RecordEntity
import com.mysecret.intimatetracker.data.db.RecordLinkRow
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

    /** 编辑记录时观察关联快照，用于补齐「父行已彻底删除」的历史选项。 */
    fun observeLinks(recordId: String): Flow<List<RecordLinkRow>> =
        db.recordDao().observeLinkRows(recordId)

    suspend fun find(id: String): RecordEntity? = db.recordDao().find(id)

    suspend fun save(draft: RecordDraft): String {
        val id = db.withTransaction {
            val dao = db.recordDao()
            val now = System.currentTimeMillis()
            val existing = draft.id?.takeIf { it.isNotBlank() }?.let { dao.find(it) }
            val id = existing?.id ?: UUID.randomUUID().toString()

            // 关联是「发生过的事实」：父行（伴侣 / 字典项）被彻底删除后，关联行与名字快照
            // 都必须保留。父行还在就写当前名字（改名后重新保存会刷新快照），
            // 不在就用这条记录原来的快照；只有「既没有父行、也没有快照」的脏 id 才丢弃。
            suspend fun resolved(
                ids: List<String>,
                currentName: suspend (String) -> String?,
                oldLabels: Map<String, String?>,
            ): List<Pair<String, String>> = ids.distinct().mapNotNull { linkId ->
                (currentName(linkId) ?: oldLabels[linkId])
                    ?.takeIf { it.isNotBlank() }
                    ?.let { linkId to it }
            }

            val partnerIds = resolved(
                draft.partnerIds,
                { db.partnerDao().findIncludingDeleted(it)?.name },
                existing?.let { dao.partnerLinkRows(id).associate { link -> link.partnerId to link.label } } ?: emptyMap(),
            )
            val sexTypeIds = resolved(
                draft.sexTypeIds,
                { db.sexTypeDao().byIdIncludingDeleted(it)?.name },
                existing?.let { dao.sexTypeLinkRows(id).associate { link -> link.sexTypeId to link.label } } ?: emptyMap(),
            )
            val positionIds = resolved(
                draft.positionIds,
                { db.positionDao().byIdIncludingDeleted(it)?.name },
                existing?.let { dao.positionLinkRows(id).associate { link -> link.positionId to link.label } } ?: emptyMap(),
            )
            val toyIds = resolved(
                draft.toyIds,
                { db.toyDao().byIdIncludingDeleted(it)?.name },
                existing?.let { dao.toyLinkRows(id).associate { link -> link.toyId to link.label } } ?: emptyMap(),
            )
            val locationIds = resolved(
                draft.locationIds,
                { db.locationDao().byIdIncludingDeleted(it)?.name },
                existing?.let { dao.locationLinkRows(id).associate { link -> link.locationId to link.label } } ?: emptyMap(),
            )

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
                    isSolo = draft.isSolo || partnerIds.isEmpty(),
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
            partnerIds.forEach { (partnerId, label) ->
                dao.linkPartner(RecordPartnerCrossRef(recordId = id, partnerId = partnerId, label = label))
            }
            sexTypeIds.forEach { (sexTypeId, label) ->
                dao.linkSexType(RecordSexTypeCrossRef(recordId = id, sexTypeId = sexTypeId, label = label))
            }
            positionIds.forEach { (positionId, label) ->
                dao.linkPosition(RecordPositionCrossRef(recordId = id, positionId = positionId, label = label))
            }
            toyIds.forEach { (toyId, label) ->
                dao.linkToy(RecordToyCrossRef(recordId = id, toyId = toyId, label = label))
            }
            locationIds.forEach { (locationId, label) ->
                dao.linkLocation(RecordLocationCrossRef(recordId = id, locationId = locationId, label = label))
            }
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
