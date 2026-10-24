package com.mysecret.intimatetracker.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.db.PartnerEntity
import kotlinx.coroutines.launch
import com.mysecret.intimatetracker.data.repo.MonthPoint
import com.mysecret.intimatetracker.data.repo.NamedCount
import com.mysecret.intimatetracker.data.repo.StatsSnapshot
import com.mysecret.intimatetracker.ui.component.EmptyState
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import com.mysecret.intimatetracker.ui.component.rememberAnimationsEnabled
import kotlin.math.roundToInt

private data class StatsTab(val labelRes: Int)

private val tabs = listOf(
    StatsTab(R.string.stats_tab_overview),
    StatsTab(R.string.stats_tab_type),
    StatsTab(R.string.stats_tab_location),
    StatsTab(R.string.stats_tab_position),
    StatsTab(R.string.stats_tab_toy),
    StatsTab(R.string.stats_tab_initiator),
    StatsTab(R.string.stats_tab_activity),
    StatsTab(R.string.stats_tab_orgasm),
    StatsTab(R.string.stats_tab_duration),
)

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = hiltViewModel()) {
    val statsData by viewModel.data.collectAsStateWithLifecycle()
    val snapshot = statsData.current
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    var showCustomRange by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    ScreenScaffold(title = stringResource(R.string.stats_title)) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
            FilterRow(
                period = viewModel.period,
                partnerId = viewModel.partnerId,
                partners = partners,
                onPeriod = { picked ->
                    if (picked == StatsPeriod.CUSTOM) showCustomRange = true else viewModel.selectPeriod(picked)
                },
                onPartner = viewModel::selectPartner,
            )
            HorizontalDivider(color = com.mysecret.intimatetracker.ui.theme.Divider)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                when (page) {
                    0 -> OverviewTab(snapshot, statsData.previous)
                    1 -> RankTab(snapshot.total, snapshot.filledTypes, snapshot.types)
                    2 -> RankTab(snapshot.total, snapshot.filledLocations, snapshot.locations)
                    3 -> RankTab(snapshot.total, snapshot.filledPositions, snapshot.positions)
                    4 -> RankTab(snapshot.total, snapshot.filledToys, snapshot.toys)
                    5 -> InitiatorTab(snapshot)
                    6 -> BarTab(snapshot.monthActivities, total = snapshot.total)
                    7 -> BarTab(snapshot.monthOrgasms, total = snapshot.myOrgasm)
                    8 -> BarTab(snapshot.monthDurations, total = snapshot.totalDuration)
                }
            }
        }
    }

    if (showCustomRange) {
        CustomRangeDialog(
            onDismiss = { showCustomRange = false },
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showCustomRange = false
            },
        )
    }
}

@Composable
private fun FilterRow(
    period: StatsPeriod,
    partnerId: String?,
    partners: List<PartnerEntity>,
    onPeriod: (StatsPeriod) -> Unit,
    onPartner: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterChipBox(
            caption = "期间",
            label = period.label,
            modifier = Modifier.weight(1f),
        ) { dismiss ->
            StatsPeriod.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onPeriod(option)
                        dismiss()
                    },
                )
            }
        }
        val partnerLabel = partners.firstOrNull { it.id == partnerId }?.name
            ?: stringResource(R.string.stats_partner_all)
        FilterChipBox(caption = "伴侣", label = partnerLabel, modifier = Modifier.weight(1f)) { dismiss ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.stats_partner_all)) },
                onClick = {
                    onPartner(null)
                    dismiss()
                },
            )
            partners.forEach { partner ->
                DropdownMenuItem(
                    text = { Text(partner.name) },
                    onClick = {
                        onPartner(partner.id)
                        dismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterChipBox(
    caption: String,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(start = 16.dp, end = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .offset(x = 12.dp, y = (-7).dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 4.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

@Composable
private fun OverviewTab(snapshot: StatsSnapshot, previous: StatsSnapshot?) {
    if (snapshot.total == 0) {
        EmptyState(text = stringResource(R.string.stats_empty))
        return
    }

    fun diff(cur: Double, pre: Double?, decimals: Boolean = false): String? {
        if (pre == null) return null
        val shown = if (decimals) String.format("%.1f", pre) else pre.roundToInt().toString()
        val pct = if (pre == 0.0) null else ((cur - pre) / pre * 100).roundToInt()
        return when {
            pct == null && cur == 0.0 -> "$shown · 0%"
            pct == null -> "$shown · 新增"
            pct >= 0 -> "$shown · ↑ +$pct%"
            else -> "$shown · ↓ $pct%"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val cards = listOf(
                    Triple(
                        stringResource(R.string.stats_card_activity),
                        snapshot.total.toString(),
                        diff(snapshot.total.toDouble(), previous?.total?.toDouble()),
                    ),
                    Triple(
                        stringResource(R.string.stats_card_partners),
                        snapshot.partnerCount.toString(),
                        diff(snapshot.partnerCount.toDouble(), previous?.partnerCount?.toDouble()),
                    ),
                    Triple(
                        stringResource(R.string.stats_card_solo),
                        snapshot.solo.toString(),
                        diff(snapshot.solo.toDouble(), previous?.solo?.toDouble()),
                    ),
                    Triple(
                        stringResource(R.string.stats_card_satisfaction),
                        if (snapshot.avgSatisfaction == 0.0) "—" else String.format("%.1f", snapshot.avgSatisfaction),
                        diff(snapshot.avgSatisfaction, previous?.avgSatisfaction, decimals = true),
                    ),
                    Triple(
                        stringResource(R.string.stats_card_avg_duration),
                        "${snapshot.avgDuration} 分钟",
                        diff(snapshot.avgDuration.toDouble(), previous?.avgDuration?.toDouble()),
                    ),
                    Triple(
                        stringResource(R.string.stats_card_total_duration),
                        "${snapshot.totalDuration} 分钟",
                        diff(snapshot.totalDuration.toDouble(), previous?.totalDuration?.toDouble()),
                    ),
                    Triple(
                        stringResource(R.string.stats_card_protection),
                        if (snapshot.partnerCount == 0) "—" else "${snapshot.protectionRate.roundToInt()}%",
                        diff(snapshot.protectionRate, previous?.protectionRate),
                    ),
                    Triple(
                        stringResource(R.string.stats_card_orgasm),
                        snapshot.myOrgasm.toString(),
                        diff(snapshot.myOrgasm.toDouble(), previous?.myOrgasm?.toDouble()),
                    ),
                    Triple(
                        stringResource(R.string.stats_card_orgasm_partner),
                        snapshot.partnerOrgasm.toString(),
                        diff(snapshot.partnerOrgasm.toDouble(), previous?.partnerOrgasm?.toDouble()),
                    ),
                    Triple(
                        stringResource(R.string.stats_card_avg_orgasm),
                        String.format("%.1f", snapshot.avgMyOrgasm),
                        diff(snapshot.avgMyOrgasm, previous?.avgMyOrgasm, decimals = true),
                    ),
                    Triple(
                        stringResource(R.string.stats_card_avg_orgasm_partner),
                        String.format("%.1f", snapshot.avgPartnerOrgasm),
                        diff(snapshot.avgPartnerOrgasm, previous?.avgPartnerOrgasm, decimals = true),
                    ),
                )
                cards.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (label, value, secondary) ->
                            StatCard(
                                label = label,
                                value = value,
                                secondary = secondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (snapshot.partnerActivities.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.stats_partner_rank)) }
            items(snapshot.partnerActivities) { item ->
                RankRow(item = item)
            }
            item {
                RankRow(item = NamedCount("自慰", snapshot.solo, snapshot.solo * 100.0 / snapshot.total))
            }
        }

        item { SectionTitle(stringResource(R.string.stats_weekday)) }
        item { WeekdayChart(snapshot.weekdays) }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    secondary: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (secondary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 3 })
                },
                label = "statValue",
            ) { shown ->
                Text(
                    text = shown,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 32.sp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun RankTab(total: Int, filled: Int, items: List<NamedCount>) {
    if (items.isEmpty()) {
        EmptyState(text = stringResource(R.string.stats_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.stats_filled_header, total, filled),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.stats_percent_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items) { item -> RankRow(item) }
    }
}

@Composable
private fun RankRow(item: NamedCount) {
    val animationsEnabled = rememberAnimationsEnabled()
    var appeared by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) { appeared = true }
    val targetProgress = (item.percent / 100.0).toFloat().coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = if (appeared) targetProgress else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "rankProgress",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${item.count} · ${item.percent.roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
    }
}

@Composable
private fun InitiatorTab(snapshot: StatsSnapshot) {
    if (snapshot.initiators.isEmpty()) {
        EmptyState(text = stringResource(R.string.stats_empty))
        return
    }
    val total = snapshot.initiators.sumOf { it.count }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
            ) {
                snapshot.initiators.forEachIndexed { index, item ->
                    val weight = (item.count.toFloat() / total).coerceAtLeast(0.02f)
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxSize()
                            .padding(1.dp)
                            .background(
                                color = initiatorColor(index),
                                shape = RoundedCornerShape(6.dp),
                            ),
                    )
                }
            }
        }
        items(snapshot.initiators) { item -> RankRow(item) }
    }
}

@Composable
private fun initiatorColor(index: Int) = when (index % 3) {
    0 -> MaterialTheme.colorScheme.primary
    1 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun BarTab(points: List<MonthPoint>, total: Int) {
    if (points.isEmpty()) {
        EmptyState(text = stringResource(R.string.stats_empty))
        return
    }
    val max = points.maxOf { it.value }.coerceAtLeast(1)
    val animationsEnabled = rememberAnimationsEnabled()
    var appeared by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) { appeared = true }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.stats_total, total),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                points.forEach { point ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = point.value.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val targetFraction = point.value.toFloat() / max
                        val heightFraction by animateFloatAsState(
                            targetValue = if (appeared) targetFraction else 0f,
                            animationSpec = tween(450, easing = FastOutSlowInEasing),
                            label = "monthBarHeight",
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((160 * heightFraction).dp.coerceAtLeast(4.dp))
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = point.label.takeLast(2),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayChart(counts: List<Int>) {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val animationsEnabled = rememberAnimationsEnabled()
    var appeared by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) { appeared = true }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEachIndexed { index, value ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val targetFraction = value.toFloat() / max
                val fraction by animateFloatAsState(
                    targetValue = if (appeared) targetFraction else 0f,
                    animationSpec = tween(450, easing = FastOutSlowInEasing),
                    label = "weekdayBarHeight",
                )
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height((110 * fraction).dp.coerceAtLeast(2.dp))
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = labels[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (java.time.LocalDate, java.time.LocalDate) -> Unit,
) {
    var start by remember { mutableStateOf(java.time.LocalDate.now().minusDays(29)) }
    var end by remember { mutableStateOf(java.time.LocalDate.now()) }
    var picking by remember { mutableIntStateOf(0) }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日")

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自订比较") },
        text = {
            Column {
                Text(
                    text = "选一段日期，统计会与它前面等长的一段对比。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { picking = 1 }.padding(vertical = 12.dp),
                ) {
                    Text("开始", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(start.format(fmt), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { picking = 2 }.padding(vertical = 12.dp),
                ) {
                    Text("结束", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(end.format(fmt), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (end.isBefore(start)) onConfirm(end, start) else onConfirm(start, end)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (picking != 0) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = (if (picking == 1) start else end)
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { picking = 0 },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        if (picking == 1) start = picked else end = picked
                    }
                    picking = 0
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { picking = 0 }) { Text("取消") } },
        ) {
            androidx.compose.material3.DatePicker(state = state)
        }
    }
}
