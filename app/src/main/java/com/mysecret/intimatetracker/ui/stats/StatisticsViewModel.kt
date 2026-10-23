package com.mysecret.intimatetracker.ui.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.repo.PartnerRepository
import com.mysecret.intimatetracker.data.repo.StatsRepository
import com.mysecret.intimatetracker.data.repo.StatsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class StatsPeriod(val label: String, val isCompare: Boolean) {
    LAST_7("最近7天", false),
    LAST_30("最近30天", false),
    MONTH_COMPARE("逐月比较", true),
    YEAR_COMPARE("逐年比较", true),
    CUSTOM("自订比较", true),
    ALL("全部时间", false),
}

data class StatsQuery(
    val period: StatsPeriod,
    val partnerId: String?,
    val start: Long? = null,
    val end: Long? = null,
)

/** 当前区间 + 用于对比的上一个区间。 */
data class StatsData(
    val current: StatsSnapshot = StatsSnapshot(),
    val previous: StatsSnapshot? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val stats: StatsRepository,
    partnerRepository: PartnerRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    var period by mutableStateOf(StatsPeriod.ALL)
        private set

    var partnerId by mutableStateOf<String?>(null)
        private set

    private val query = MutableStateFlow(StatsQuery(StatsPeriod.ALL, null))

    val partners: StateFlow<List<PartnerEntity>> = partnerRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val data: StateFlow<StatsData> = query
        .flatMapLatest { q ->
            flow {
                val (from, to) = range(q)
                val current = stats.load(from, to, q.partnerId)
                val previous = previousRange(q, from, to)
                    ?.let { (pf, pt) -> stats.load(pf, pt, q.partnerId) }
                emit(StatsData(current, previous))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsData())

    fun selectPeriod(value: StatsPeriod) {
        period = value
        query.value = query.value.copy(period = value)
    }

    fun selectPartner(id: String?) {
        partnerId = id
        query.value = query.value.copy(partnerId = id)
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        period = StatsPeriod.CUSTOM
        query.value = query.value.copy(
            period = StatsPeriod.CUSTOM,
            start = start.atStartOfDay(zone).toInstant().toEpochMilli(),
            end = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
        )
    }

    private fun range(q: StatsQuery): Pair<Long, Long> {
        val now = LocalDate.now()
        val endOfToday = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return when (q.period) {
            StatsPeriod.ALL -> 0L to endOfToday
            StatsPeriod.LAST_7 -> now.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli() to endOfToday
            StatsPeriod.LAST_30 -> now.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli() to endOfToday
            StatsPeriod.MONTH_COMPARE -> now.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() to endOfToday
            StatsPeriod.YEAR_COMPARE -> now.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli() to endOfToday
            StatsPeriod.CUSTOM -> {
                val from = q.start ?: now.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
                val to = q.end ?: endOfToday
                from to to
            }
        }
    }

    /** 对比区间：逐月=上个月整天；逐年=去年整天；自订/其它=等长且紧邻的上一个区间。 */
    private fun previousRange(q: StatsQuery, from: Long, to: Long): Pair<Long, Long>? {
        if (!q.period.isCompare) return null
        val today = LocalDate.now()
        return when (q.period) {
            StatsPeriod.MONTH_COMPARE -> {
                val firstThisMonth = today.withDayOfMonth(1)
                val prevFrom = firstThisMonth.minusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val prevTo = firstThisMonth.atStartOfDay(zone).toInstant().toEpochMilli() - 1
                prevFrom to prevTo
            }
            StatsPeriod.YEAR_COMPARE -> {
                val firstThisYear = today.withDayOfYear(1)
                val prevFrom = firstThisYear.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val prevTo = firstThisYear.atStartOfDay(zone).toInstant().toEpochMilli() - 1
                prevFrom to prevTo
            }
            else -> {
                val span = to - from + 1
                val prevTo = from - 1
                (prevTo - span + 1) to prevTo
            }
        }
    }
}
