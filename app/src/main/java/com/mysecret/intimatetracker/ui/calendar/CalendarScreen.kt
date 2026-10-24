package com.mysecret.intimatetracker.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.db.RecordListRow
import com.mysecret.intimatetracker.data.repo.RhythmBand
import com.mysecret.intimatetracker.data.repo.RhythmCalculator
import com.mysecret.intimatetracker.data.repo.RhythmStats
import com.mysecret.intimatetracker.domain.RhythmSettings
import com.mysecret.intimatetracker.ui.component.AppBottomSheet
import com.mysecret.intimatetracker.ui.component.AppFab
import com.mysecret.intimatetracker.ui.component.RecordRowItem
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import com.mysecret.intimatetracker.ui.component.rememberAnimationsEnabled
import com.mysecret.intimatetracker.ui.theme.PartnerColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.SIMPLIFIED_CHINESE)
private val dayFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
private val shortDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)
private val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

/** 月历单行日期格的高度，翻月补间按这个值换算整块高度。 */
private val DAY_CELL_HEIGHT = 60.dp

/** 该月月历占几行（周一为一周第一天）。 */
private fun rowsOf(month: YearMonth): Int {
    val offset = month.atDay(1).dayOfWeek.value - 1
    return (offset + month.lengthOfMonth() + 6) / 7
}

private val PAGER_START: YearMonth = YearMonth.of(2000, 1)
private const val PAGER_PAGES = 601

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onAdd: () -> Unit,
    onOpenRecord: (String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val dayStyles by viewModel.dayStyles.collectAsStateWithLifecycle()
    val dayRecords by viewModel.dayRecords.collectAsStateWithLifecycle()
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    var showFilter by remember { mutableStateOf(false) }
    var showRhythmSheet by remember { mutableStateOf(false) }
    var showRhythmCustomize by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refreshRhythm() }

    val initialPage = remember {
        (viewModel.month.year - PAGER_START.year) * 12 + (viewModel.month.monthValue - 1)
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { PAGER_PAGES })

    LaunchedEffect(pagerState.settledPage) {
        viewModel.selectMonth(PAGER_START.plusMonths(pagerState.settledPage.toLong()))
    }

    ScreenScaffold(title = stringResource(R.string.calendar_title), showTitleBar = false) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    RhythmCard(
                        stats = viewModel.rhythm,
                        filtered = viewModel.filter.isActive,
                        settings = viewModel.rhythmSettings,
                        onClick = { showRhythmSheet = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedContent(
                            targetState = viewModel.month,
                            transitionSpec = {
                                val forward = targetState > initialState
                                (
                                    slideInHorizontally(tween(220)) { width ->
                                        if (forward) width / 3 else -width / 3
                                    } + fadeIn(tween(180))
                                    ) togetherWith (
                                    slideOutHorizontally(tween(180)) { width ->
                                        if (forward) -width / 3 else width / 3
                                    } + fadeOut(tween(120))
                                    )
                            },
                            label = "monthTitle",
                        ) { month ->
                            Text(
                                text = monthFormatter.format(month) + " ▾",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 18.sp,
                                    lineHeight = 24.sp,
                                ),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { showMonthPicker = true },
                            )
                        }
                        FilterButton(
                            count = viewModel.filter.activeCount,
                            onClick = { showFilter = true },
                        )
                    }
                }
                item {
                    // 5 行 / 6 行月份高度不同：直接按「当前或手势指向的月份」算目标高度来补间。
                    // 变高、变矮都在手势一开始就动，不必等到翻月停稳才回落。
                    val density = LocalDensity.current
                    val cellHeightPx = with(density) { DAY_CELL_HEIGHT.toPx() }
                    var headerHeightPx by remember { mutableFloatStateOf(0f) }

                    // 手势方向要跟「连续位置」的变化量，不能只看 offset 符号：
                    // 越过翻页中点时 currentPage 会进位、offset 同时变号，只看符号会把目标月份算回上一个，高度就来回抖。
                    var scrollForward by remember { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(pagerState) {
                        var previous = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
                            .collect { position ->
                                val delta = position - previous
                                if (abs(delta) > 0.001f) scrollForward = delta > 0f
                                previous = position
                            }
                    }

                    val heightPage by remember {
                        derivedStateOf {
                            val current = pagerState.currentPage
                            val target = pagerState.targetPage
                            val position = current + pagerState.currentPageOffsetFraction
                            val page = when {
                                !pagerState.isScrollInProgress -> current
                                // 快速甩动或月份选择器跨多月：直接取终点，免得途经月份反复改高度
                                abs(target - current) > 1 -> target
                                // 朝运动方向取最近的那个整页：拖动中途不会出现过冲的目标
                                scrollForward == true -> ceil(position).toInt()
                                scrollForward == false -> floor(position).toInt()
                                else -> current
                            }
                            page.coerceIn(0, PAGER_PAGES - 1)
                        }
                    }
                    val targetHeightPx = if (headerHeightPx > 0f) {
                        headerHeightPx + rowsOf(PAGER_START.plusMonths(heightPage.toLong())) * cellHeightPx
                    } else {
                        0f
                    }
                    val heightAnim = remember { Animatable(0f) }
                    LaunchedEffect(targetHeightPx) {
                        if (targetHeightPx <= 0f) return@LaunchedEffect
                        if (heightAnim.value <= 0f) {
                            heightAnim.snapTo(targetHeightPx)
                        } else {
                            heightAnim.animateTo(
                                targetValue = targetHeightPx,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                                val animated = heightAnim.value
                                val height = if (animated > 0f) animated.roundToInt() else placeable.height
                                layout(placeable.width, height) { placeable.place(0, 0) }
                            },
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth(),
                            // 默认是垂直居中：两页行数不同时，矮的那个月会被居中而下沉半行。
                            // 统一顶部对齐，月份始终贴住日历顶部，多出来的行只由外层高度补间控制。
                            verticalAlignment = Alignment.Top,
                        ) { page ->
                            val month = PAGER_START.plusMonths(page.toLong())
                            MonthGrid(
                                month = month,
                                dayStyles = dayStyles,
                                selected = viewModel.selectedDate,
                                isToday = viewModel::isToday,
                                onSelect = viewModel::select,
                                filterActive = viewModel.filter.isActive,
                                modifier = Modifier.onSizeChanged { size ->
                                    // 用实测高度反推表头高度，字号缩放变化也能跟上
                                    val headerPx = size.height - rowsOf(month) * cellHeightPx
                                    if (headerPx > 0f && abs(headerPx - headerHeightPx) > 0.5f) {
                                        headerHeightPx = headerPx
                                    }
                                },
                            )
                        }
                    }
                }
                item(key = "day-content") {
                    AnimatedContent(
                        targetState = viewModel.selectedDate to dayRecords,
                        transitionSpec = { fadeIn(tween(170)) togetherWith fadeOut(tween(110)) },
                        label = "dayContent",
                    ) { (date, records) ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = dayFormatter.format(date),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.calendar_records_count, records.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (records.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.calendar_no_records),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp),
                                )
                            } else {
                                records.forEach { row ->
                                    Box(Modifier.padding(horizontal = 8.dp)) {
                                        RecordRow(row = row, onClick = { onOpenRecord(row.record.id) })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AppFab(
                contentDescription = stringResource(R.string.record_new),
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 100.dp),
            )
        }
    }

    if (showRhythmSheet) {
        RhythmDetailSheet(
            stats = viewModel.rhythm,
            settings = viewModel.rhythmSettings,
            onDismiss = { showRhythmSheet = false },
            onCustomize = {
                showRhythmSheet = false
                showRhythmCustomize = true
            },
        )
    }

    if (showRhythmCustomize) {
        RhythmCustomizeDialog(
            settings = viewModel.rhythmSettings,
            onSave = { next ->
                viewModel.saveRhythmSettings(next)
                showRhythmCustomize = false
            },
            onDismiss = { showRhythmCustomize = false },
        )
    }

    if (showFilter) {
        FilterSheet(
            viewModel = viewModel,
            partners = partners,
            onDismiss = { showFilter = false },
        )
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            initial = viewModel.month,
            onDismiss = { showMonthPicker = false },
            onPick = { target ->
                showMonthPicker = false
                val page = ((target.year - PAGER_START.year) * 12 + (target.monthValue - 1))
                    .coerceIn(0, PAGER_PAGES - 1)
                scope.launch { pagerState.animateScrollToPage(page) }
            },
        )
    }
}

@Composable
private fun FilterSheet(
    viewModel: CalendarViewModel,
    partners: List<com.mysecret.intimatetracker.data.db.PartnerEntity>,
    onDismiss: () -> Unit,
) {
    val filter = viewModel.filter
    val total = partners.size + 1

    AppBottomSheet(
        title = stringResource(R.string.calendar_filter_title),
        onDismiss = onDismiss,
        actionLabel = stringResource(R.string.calendar_filter_clear),
        onAction = viewModel::clearFilter,
    ) {
        Text(
            text = stringResource(R.string.calendar_filter_selected, filter.activeCount, total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filter.isActive) {
                (listOfNotNull(
                    if (filter.partnerIds.isNotEmpty()) PartnerColors.palette.first() else null,
                    if (filter.onlySolo) PartnerColors.forPartner("", PartnerColors.isLightTheme()) else null,
                )).forEach { color ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(color, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        FlowChips {
            FilterChip(
                selected = filter.onlySolo,
                onClick = viewModel::toggleOnlySolo,
                label = { Text(stringResource(R.string.calendar_filter_only_solo)) },
            )
            partners.forEach { partner ->
                val color = PartnerColors.forPartner(partner.id)
                FilterChip(
                    selected = partner.id in filter.partnerIds,
                    onClick = { viewModel.togglePartner(partner.id) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(color, CircleShape),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(partner.name)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FlowChips(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun FilterButton(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.FilterList,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = stringResource(R.string.calendar_filter),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun RhythmCard(
    stats: RhythmStats?,
    filtered: Boolean,
    settings: RhythmSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val band = RhythmCalculator.band(settings, stats?.daysSinceLast)
    val animated = rememberAnimationsEnabled()
    val ringColor by animateColorAsState(
        targetValue = rhythmRingColor(settings, band),
        animationSpec = if (animated) tween(durationMillis = 420) else snap(),
        label = "rhythmRingColor",
    )
    val arc by animateFloatAsState(
        targetValue = RhythmCalculator.arcFraction(stats?.daysSinceLast, settings.longGapDays),
        animationSpec = if (animated) {
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
        } else {
            snap()
        },
        label = "rhythmRingArc",
    )
    val bigNumber = RhythmCalculator.displayNumber(stats?.daysSinceLast, settings.weeklyUnit)
    val unit = RhythmCalculator.displayUnit(stats?.daysSinceLast, settings.weeklyUnit)
    // 环本身是装饰性的，整块只报一句人话，别让读屏念零散的数字与单位。
    val ringDescription = when {
        stats?.daysSinceLast == null -> stringResource(R.string.calendar_ring_desc_none)
        band == RhythmBand.ALERT -> stringResource(R.string.calendar_ring_desc_over, "$bigNumber${unit.orEmpty()}")
        else -> stringResource(R.string.calendar_ring_desc, "$bigNumber${unit.orEmpty()}")
    }
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // 内距与下面的字号/间距按参考稿实测值复刻（16·12·14dp）：底边比上边多 2dp，
        // 因为底行是 12sp 的小字，视觉上需要多一点留白才与上方的 15sp 区块平衡。
        Column(
            Modifier.padding(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 14.dp,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clearAndSetSemantics { contentDescription = ringDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    // 轨道 + 进度弧：弧长说明「过去多久」，颜色只做加强。
                    // 轨道用 surfaceContainerHighest —— M3 进度轨道的标准角色，换主题自动跟着走。
                    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 6.dp.toPx()
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val topLeft = Offset(stroke / 2f, stroke / 2f)
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                        if (arc > 0f) {
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = 360f * arc,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                    Text(
                        text = bigNumber,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (RhythmCalculator.showsPartnerTitle(settings.partnerOnly, filtered)) {
                                stringResource(R.string.calendar_days_ago_partner)
                            } else {
                                stringResource(R.string.calendar_days_ago)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (filtered) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.calendar_filtered),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.size(4.dp))
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            // 参考稿里这个图标比标题行中线低约 5dp（与标题的字身底部更近），
                            // offset 只改绘制位置，不参与排版。
                            modifier = Modifier
                                .padding(end = 3.dp)
                                .size(18.dp)
                                .offset(y = 5.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RhythmStat(
                            label = stringResource(R.string.calendar_last_record),
                            value = stats?.lastDate?.let { shortDayFormatter.format(it) } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        RhythmStat(
                            label = stringResource(R.string.calendar_usual),
                            value = stats?.usualRange?.let { "每${it.first}-${it.last}天" } ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (settings.showLongestGap) {
                Spacer(Modifier.height(9.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.calendar_longest_gap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stats?.longestGapDays?.let { "$it 天" } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** 节奏卡的一列统计：小号灰色标签 + 大号数值。 */
@Composable
private fun RhythmStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    dayStyles: Map<LocalDate, DayStyle>,
    selected: LocalDate,
    isToday: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
    filterActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val rows = rowsOf(month)

    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth()) {
            weekLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(1.dp))
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayNumber = row * 7 + col - offset + 1
                    Box(
                        modifier = Modifier.weight(1f).height(DAY_CELL_HEIGHT),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (dayNumber in 1..days) {
                            val date = month.atDay(dayNumber)
                            val style = dayStyles[date]
                            DayCell(
                                date = date,
                                icons = style?.icons.orEmpty(),
                                hasRecords = style != null,
                                ringColor = if (filterActive && style != null) {
                                    val light = PartnerColors.isLightTheme()
                                    style.partnerId?.let { id -> PartnerColors.forPartner(id, light) }
                                        ?: PartnerColors.forPartner(null, light)
                                } else {
                                    null
                                },
                                isSelected = date == selected,
                                isToday = isToday(date),
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    icons: List<String>,
    hasRecords: Boolean,
    ringColor: Color?,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    // 外圈（直径大）= 筛选命中的记录日期；内圈（直径小）= 今天
    val filterRing = ringColor
    // 点击时闪过的涟漪 / state layer 按圆角裁剪，避免整格方形高亮
    val tapShape = RoundedCornerShape(16.dp)
    val selectionColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(160),
        label = "daySelectionColor",
    )
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.8f,
        animationSpec = tween(180),
        label = "daySelectionScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(tapShape)
            .clickable(onClick = onClick)
            .padding(top = 2.dp, start = 2.dp, end = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .then(
                    if (filterRing != null) {
                        Modifier.border(2.dp, filterRing, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .drawBehind {
                        if (selectionColor.alpha > 0.01f) {
                            drawCircle(
                                color = selectionColor,
                                radius = size.minDimension / 2f * selectionScale,
                            )
                        }
                    }
                    .then(
                        if (isToday) {
                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    fontWeight = when {
                        hasRecords -> FontWeight.Bold
                        isSelected || isToday -> FontWeight.Medium
                        else -> FontWeight.Normal
                    },
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        Row {
            icons.take(2).forEach { icon ->
                Text(text = icon, style = MaterialTheme.typography.labelSmall)
            }
            if (icons.isEmpty()) {
                Text(text = " ", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RecordRow(row: RecordListRow, onClick: () -> Unit) {
    RecordRowItem(row = row, onClick = onClick, showIcons = false)
}

/** 月份选择器：左右切年，12 个月三列排布。 */
@Composable
private fun MonthPickerDialog(
    initial: YearMonth,
    onDismiss: () -> Unit,
    onPick: (YearMonth) -> Unit,
) {
    var year by remember { mutableIntStateOf(initial.year) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { year -= 1 }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上一年")
                    }
                    Text(
                        text = "$year 年",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { year += 1 }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下一年")
                    }
                }
                Spacer(Modifier.height(8.dp))
                repeat(4) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(3) { col ->
                            val month = row * 3 + col + 1
                            val selected = year == initial.year && month == initial.monthValue
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .clickable { onPick(YearMonth.of(year, month)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${month}月",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                    if (row < 3) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}