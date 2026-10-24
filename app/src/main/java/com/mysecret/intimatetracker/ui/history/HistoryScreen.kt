package com.mysecret.intimatetracker.ui.history

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.Icons
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mysecret.intimatetracker.data.db.PhotoEntity
import java.io.File
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.db.RecordListRow
import com.mysecret.intimatetracker.ui.component.AppBottomSheet
import com.mysecret.intimatetracker.ui.component.RecordRowItem
import com.mysecret.intimatetracker.ui.component.EmptyState
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val monthPattern = DateTimeFormatter.ofPattern("yyyy年M月", Locale.SIMPLIFIED_CHINESE)
private val dayPattern = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)

@Composable
fun HistoryScreen(
    onOpenRecord: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    var showFilter by remember { mutableStateOf(false) }
    var viewerPhotos by remember { mutableStateOf<List<com.mysecret.intimatetracker.data.db.PhotoWithRecord>>(emptyList()) }
    var viewerIndex by remember { mutableIntStateOf(0) }
    var viewerOpen by remember { mutableStateOf(false) }
    val months by viewModel.months.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    ScreenScaffold(
        title = stringResource(R.string.history_title),
        actions = {
            IconButton(onClick = { showFilter = true }) {
                Icon(Icons.Outlined.FilterList, contentDescription = "筛选")
            }
        },
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                listOf(
                    stringResource(R.string.history_tab_activity),
                    stringResource(R.string.history_tab_album),
                ).forEachIndexed { index, label ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(label) },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { page ->
                if (page == 0) {
                    AnimatedContent(
                        targetState = months.isEmpty(),
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                        modifier = Modifier.fillMaxSize(),
                        label = "historyActivity",
                    ) { empty ->
                        if (empty) {
                            EmptyState(text = stringResource(R.string.history_empty))
                        } else {
                            ActivityList(months, onOpenRecord)
                        }
                    }
                } else {
                    AlbumGrid(photos, onClick = { index ->
                        viewerPhotos = photos
                        viewerIndex = index
                        viewerOpen = true
                    })
                }
            }
        }
    }

    if (viewerOpen && viewerPhotos.isNotEmpty()) {
        com.mysecret.intimatetracker.ui.component.PhotoViewer(
            photos = viewerPhotos.map {
                com.mysecret.intimatetracker.ui.component.ViewerPhoto(
                    id = it.photo.id,
                    relPath = it.photo.relPath,
                    recordId = it.photo.recordId,
                    timestamp = it.recordTimestamp,
                )
            },
            startIndex = viewerIndex,
            showRecordButton = true,
            onOpenRecord = { recordId ->
                viewerOpen = false
                onOpenRecord(recordId)
            },
            onDismiss = { viewerOpen = false },
        )
    }

    if (showFilter) {
        HistoryFilterSheet(viewModel = viewModel, onDismiss = { showFilter = false })
    }
}

@Composable
private fun ActivityList(months: List<HistoryMonth>, onOpenRecord: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        months.forEach { month ->
            item(key = "month-${month.month}") {
                Text(
                    text = monthPattern.format(month.month),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
            }
            month.days.forEach { day ->
                item(key = "day-${day.date}") {
                    Text(
                        text = dayPattern.format(day.date),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                items(count = day.rows.size, key = { index -> day.rows[index].record.id }) { index ->
                    HistoryCard(
                        row = day.rows[index],
                        onClick = { onOpenRecord(day.rows[index].record.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

}

@Composable
private fun HistoryCard(row: RecordListRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    RecordRowItem(row = row, onClick = onClick, modifier = modifier)
}

@Composable
private fun AlbumGrid(photos: List<com.mysecret.intimatetracker.data.db.PhotoWithRecord>, onClick: (Int) -> Unit) {
    if (photos.isEmpty()) {
        EmptyState(text = stringResource(R.string.album_empty))
        return
    }
    val context = LocalContext.current
    val zone = java.time.ZoneId.systemDefault()
    val groups = photos.withIndex().groupBy { item ->
        java.time.Instant.ofEpochMilli(item.value.recordTimestamp).atZone(zone).toLocalDate()
            .let { java.time.YearMonth.of(it.year, it.monthValue) }
    }.toList()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        groups.forEach { (month, items) ->
            item(
                key = "header-$month",
                span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
            ) {
                Text(
                    text = "${month.year}年${month.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            items(items, key = { it.value.photo.id }) { item ->
                AsyncImage(
                    model = File(context.filesDir, item.value.photo.relPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(1f).clickable { onClick(item.index) },
                )
            }
        }
    }
}
@Composable
private fun HistoryFilterSheet(
    viewModel: HistoryViewModel,
    onDismiss: () -> Unit,
) {
    val filter = viewModel.filter
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    val datePattern = java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日")

    AppBottomSheet(
        title = "筛选活动",
        onDismiss = onDismiss,
        actionLabel = "清除",
        onAction = viewModel::clearFilter,
    ) {
        Text(
            text = if (filter.isActive) "已筛选 ${filter.activeCount} 项" else "未筛选",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "日期范围",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FilterDateRow(
            label = "开始",
            value = filter.start?.format(datePattern) ?: "不限日期",
            onClick = { pickingStart = true },
        )
        FilterDateRow(
            label = "结束",
            value = filter.end?.format(datePattern) ?: "不限日期",
            onClick = { pickingEnd = true },
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "和谁一起",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = filter.onlySolo,
                onClick = viewModel::toggleOnlySolo,
                label = { Text("自慰") },
            )
            partners.forEach { partner ->
                FilterChip(
                    selected = partner.id in filter.partnerIds,
                    onClick = { viewModel.togglePartner(partner.id) },
                    label = { Text(partner.name) },
                )
            }
        }
    }

    if (pickingStart || pickingEnd) {
        val initial = if (pickingStart) filter.start else filter.end
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initial?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickingStart = false; pickingEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    }
                    if (pickingStart) viewModel.setStart(picked) else viewModel.setEnd(picked)
                    pickingStart = false
                    pickingEnd = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = false; pickingEnd = false }) { Text("取消") }
            },
        ) {
            androidx.compose.material3.DatePicker(state = state)
        }
    }
}

@Composable
private fun FilterDateRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
