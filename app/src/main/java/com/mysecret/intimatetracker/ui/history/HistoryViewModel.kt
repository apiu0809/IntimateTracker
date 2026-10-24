package com.mysecret.intimatetracker.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.db.RecordListRow
import com.mysecret.intimatetracker.data.repo.PartnerRepository
import com.mysecret.intimatetracker.data.repo.PhotoRepository
import com.mysecret.intimatetracker.data.repo.RecordRepository
import com.mysecret.intimatetracker.domain.withoutDeletedMarker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class HistoryDay(
    val date: LocalDate,
    val rows: List<RecordListRow>,
)

data class HistoryMonth(
    val month: YearMonth,
    val days: List<HistoryDay>,
)

/** 历史筛选：日期范围 + 参与者。 */
data class HistoryFilter(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val partnerIds: Set<String> = emptySet(),
    val onlySolo: Boolean = false,
) {
    val activeCount: Int
        get() = (if (start != null) 1 else 0) +
            (if (end != null) 1 else 0) +
            partnerIds.size +
            (if (onlySolo) 1 else 0)

    val isActive: Boolean get() = activeCount > 0

    fun matches(row: RecordListRow, date: LocalDate): Boolean {
        if (start != null && date.isBefore(start)) return false
        if (end != null && date.isAfter(end)) return false
        val partners = row.partnerIdList
        if (partnerIds.isNotEmpty() && partners.none { it in partnerIds }) return false
        // 与日历筛选同一口径：类型里含「自慰」
        if (onlySolo && row.typeList.none { it.withoutDeletedMarker() == "自慰" }) return false
        return true
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: RecordRepository,
    private val photoRepository: PhotoRepository,
    partnerRepository: PartnerRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    var filter by mutableStateOf(HistoryFilter())
        private set

    private val filterFlow = MutableStateFlow(HistoryFilter())

    val partners = partnerRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val months = combine(
        repository.observeBetween(0L, Long.MAX_VALUE),
        filterFlow,
    ) { rows, active ->
        rows
            .filter { active.matches(it, toDate(it.record.timestamp)) }
            .groupBy { toDate(it.record.timestamp) }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayRows) ->
                HistoryDay(date, dayRows.sortedByDescending { it.record.timestamp })
            }
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap(compareByDescending { it })
            .map { (month, days) -> HistoryMonth(month, days) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // 与日历一致：伴侣被删/归档后自动从筛选里剔除
        viewModelScope.launch {
            partners.collect { list ->
                val activeIds = list.mapTo(HashSet()) { it.id }
                val kept = filter.partnerIds.filterTo(LinkedHashSet()) { it in activeIds }
                if (kept.size != filter.partnerIds.size) applyFilter(filter.copy(partnerIds = kept))
            }
        }
    }

    val photos = photoRepository.observeAlbum()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun applyFilter(next: HistoryFilter) {
        filter = next
        filterFlow.value = next
    }

    fun clearFilter() = applyFilter(HistoryFilter())

    fun togglePartner(id: String) {
        val next = if (id in filter.partnerIds) filter.partnerIds - id else filter.partnerIds + id
        applyFilter(filter.copy(partnerIds = next, onlySolo = false))
    }

    fun toggleOnlySolo() {
        applyFilter(filter.copy(onlySolo = !filter.onlySolo, partnerIds = emptySet()))
    }

    fun setStart(date: LocalDate?) {
        val end = filter.end
        val next = if (date != null && end != null && date.isAfter(end)) filter.copy(start = date, end = null)
        else filter.copy(start = date)
        applyFilter(next)
    }

    fun setEnd(date: LocalDate?) = applyFilter(filter.copy(end = date))

    private fun toDate(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}
