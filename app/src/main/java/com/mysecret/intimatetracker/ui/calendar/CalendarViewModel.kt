package com.mysecret.intimatetracker.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.data.db.CalendarEntry
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.db.RecordListRow
import com.mysecret.intimatetracker.data.repo.PartnerRepository
import com.mysecret.intimatetracker.data.repo.RecordRepository
import com.mysecret.intimatetracker.data.repo.RhythmCalculator
import com.mysecret.intimatetracker.data.repo.SettingsRepository
import com.mysecret.intimatetracker.data.repo.RhythmStats
import com.mysecret.intimatetracker.domain.RecordIcons
import com.mysecret.intimatetracker.domain.RhythmSettings
import com.mysecret.intimatetracker.domain.withoutDeletedMarker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** 日历筛选：伴侣（可多选）+ 只有自慰 + 无伴侣，组内 OR。 */
data class CalendarFilter(
    val partnerIds: Set<String> = emptySet(),
    val onlySolo: Boolean = false,
) {
    val activeCount: Int get() = partnerIds.size + (if (onlySolo) 1 else 0)
    val isActive: Boolean get() = activeCount > 0

    fun matches(entry: CalendarEntry): Boolean {
        if (!isActive) return true
        if (partnerIds.isNotEmpty() && entry.partnerIdList.any { it in partnerIds }) return true
        return onlySolo && entry.typeList.any { it.withoutDeletedMarker() == "自慰" }
    }
}

data class DayStyle(
    val icons: List<String>,
    val partnerId: String?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val records: RecordRepository,
    private val settingsRepo: SettingsRepository,
    partnerRepository: PartnerRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now()

    var month by mutableStateOf(YearMonth.from(today))
        private set

    var selectedDate by mutableStateOf(today)
        private set

    var rhythm by mutableStateOf<RhythmStats?>(null)
        private set

    var rhythmSettings by mutableStateOf(RhythmSettings())
        private set

    var filter by mutableStateOf(CalendarFilter())
        private set

    val partners: StateFlow<List<PartnerEntity>> = partnerRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val monthFlow = MutableStateFlow(YearMonth.from(today))
    private val dayFlow = MutableStateFlow(today)
    private val filterFlow = MutableStateFlow(CalendarFilter())
    private val rhythmSettingsFlow = MutableStateFlow(RhythmSettings())

    val dayStyles: StateFlow<Map<LocalDate, DayStyle>> = combine(
        monthFlow.flatMapLatest { ym -> records.observeCalendar(startOfMonth(ym), endOfMonth(ym)) },
        filterFlow,
    ) { entries, activeFilter ->
        entries
            .filter { activeFilter.matches(it) }
            .sortedBy { it.timestamp }
            .groupBy { toDate(it.timestamp) }
            .mapValues { (_, list) ->
                DayStyle(
                    icons = RecordIcons.forDay(list.map { it.icons }),
                    partnerId = list.firstNotNullOfOrNull { it.partnerIdList.firstOrNull() },
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val dayRecords: StateFlow<List<RecordListRow>> = dayFlow
        .flatMapLatest { date ->
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            records.observeBetween(start, start + DAY_MILLIS - 1)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // 伴侣被删/归档后从筛选里剔除，避免出现「看不见但仍在生效」的筛选条件
        viewModelScope.launch {
            partners.collect { list ->
                val activeIds = list.mapTo(HashSet()) { it.id }
                val kept = filter.partnerIds.filterTo(LinkedHashSet()) { it in activeIds }
                if (kept.size != filter.partnerIds.size) applyFilter(filter.copy(partnerIds = kept))
            }
        }
        viewModelScope.launch {
            rhythmSettings = settingsRepo.rhythmSettings()
            rhythmSettingsFlow.value = rhythmSettings
        }
        // 记录或设置变化时自动重算，避免返回日历还显示旧数字
        viewModelScope.launch {
            combine(
                records.observeCalendar(0L, Long.MAX_VALUE),
                rhythmSettingsFlow,
                filterFlow,
            ) { entries, settings, filter ->
                // 节奏卡跟随日历筛选：只统计被筛选的记录
                RhythmCalculator.compute(entries.filter { filter.matches(it) }, settings)
            }
                .collect { rhythm = it }
        }
    }

    /** 用户点击日历上的某一天：唯一会改变选中日期的入口。 */
    fun select(date: LocalDate) {
        selectedDate = date
        dayFlow.value = date
    }

    /**
     * 切换日历面板显示的月份。
     *
     * 仅更新视图月份（用于加载该月的日期角标），绝不修改 [selectedDate]：
     * 选中日期与记录列表只由用户点击日期驱动，滑动/跳转月份时保持不变。
     */
    fun selectMonth(target: YearMonth) {
        if (target == month) return
        month = target
        monthFlow.value = target
    }

    fun applyFilter(value: CalendarFilter) {
        filter = value
        filterFlow.value = value
    }

    fun clearFilter() = applyFilter(CalendarFilter())

    fun togglePartner(id: String) {
        val next = if (id in filter.partnerIds) filter.partnerIds - id else filter.partnerIds + id
        applyFilter(filter.copy(partnerIds = next))
    }

    fun toggleOnlySolo() = applyFilter(filter.copy(onlySolo = !filter.onlySolo))

    fun refreshRhythm() {
        // 数据变化由观察流自动驱动，这里仅保持接口兼容
    }

    fun saveRhythmSettings(next: RhythmSettings) {
        rhythmSettings = next
        rhythmSettingsFlow.value = next
        viewModelScope.launch {
            settingsRepo.saveRhythmSettings(next)
            rhythm = records.rhythm(next)
        }
    }

    fun isToday(date: LocalDate): Boolean = date == today

    private fun toDate(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    private fun startOfMonth(ym: YearMonth): Long =
        ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun endOfMonth(ym: YearMonth): Long =
        ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
