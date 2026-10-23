package com.mysecret.intimatetracker.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mysecret.intimatetracker.data.repo.RhythmStats
import com.mysecret.intimatetracker.domain.RhythmSettings
import java.text.NumberFormat

/** 节奏卡圆环颜色：中性 → 主题色 → 警示色（按距上次天数）。 */
@Composable
fun rhythmRingColor(settings: RhythmSettings, daysSinceLast: Int?): Color =
    when (com.mysecret.intimatetracker.data.repo.RhythmCalculator.band(settings, daysSinceLast)) {
        com.mysecret.intimatetracker.data.repo.RhythmBand.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        com.mysecret.intimatetracker.data.repo.RhythmBand.NORMAL -> MaterialTheme.colorScheme.primary
        com.mysecret.intimatetracker.data.repo.RhythmBand.ALERT -> MaterialTheme.colorScheme.error
    }

private val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmDetailSheet(
    stats: RhythmStats?,
    settings: RhythmSettings,
    onDismiss: () -> Unit,
    onCustomize: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                text = "你的节奏",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
            StatRow("距上次活动天数", stats?.daysSinceLast?.let { "$it 天" } ?: "—")
            StatRow("最长间隔", stats?.longestGapDays?.let { "$it 天" } ?: "—")
            StatRow(
                "通常",
                stats?.usualRange?.let { "每${it.first}-${it.last}天" } ?: "—",
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "星期活动分布",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            WeekdayChart(stats?.weekdayCounts ?: List(7) { 0 })
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onCustomize,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text("自订")
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WeekdayChart(counts: List<Int>) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        counts.forEachIndexed { index, count ->
            val ratio = count.toFloat() / max
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height((8 + ratio * 76).dp)
                        .background(
                            if (count > 0) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            },
                            RoundedCornerShape(8.dp),
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = weekLabels[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun RhythmCustomizeDialog(
    settings: RhythmSettings,
    onSave: (RhythmSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(settings) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(760.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onSave(draft) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        text = "自订卡片",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { onSave(draft) },
                        modifier = Modifier.padding(end = 12.dp),
                    ) { Text("完成") }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    ToggleRow(
                        title = "为圆环上色",
                        subtitle = "依距离上次记录的时间长短着色",
                        checked = draft.colorize,
                        onCheckedChange = { draft = draft.copy(colorize = it) },
                    )
                    StepperRow(
                        title = "中性颜色上限（天）",
                        subtitle = "距上次活动在这个天数以内，圆环维持中性颜色",
                        value = draft.neutralCapDays,
                        onChange = { draft = draft.copy(neutralCapDays = it) },
                        range = 1..60,
                    )
                    StepperRow(
                        title = "长间隔起算（天）",
                        subtitle = "距上次活动超过这个天数后，圆环会显示长间隔",
                        value = draft.longGapDays,
                        onChange = { draft = draft.copy(longGapDays = it) },
                        range = 2..365,
                    )
                    SegmentedRow(
                        title = "计数单位",
                        subtitle = "大字计数要用天数，还是整周数",
                        options = listOf("天", "周"),
                        selectedIndex = if (draft.weeklyUnit) 1 else 0,
                        onSelect = { draft = draft.copy(weeklyUnit = it == 1) },
                    )
                    SegmentedRow(
                        title = "计入哪些记录",
                        subtitle = "全部记录，或只算与伴侣的记录",
                        options = listOf("全部记录", "只算与伴侣的记录"),
                        selectedIndex = if (draft.partnerOnly) 1 else 0,
                        onSelect = { draft = draft.copy(partnerOnly = it == 1) },
                    )
                    ToggleRow(
                        title = "排除只有自慰的日子",
                        subtitle = "只有自慰记录的日子不算活动",
                        checked = draft.excludeSolo,
                        onCheckedChange = { draft = draft.copy(excludeSolo = it) },
                    )
                    StepperRow(
                        title = "资料范围",
                        subtitle = "最近几个有记录的日子会纳入计算，影响节奏与最长间隔",
                        value = draft.rangeDays,
                        onChange = { draft = draft.copy(rangeDays = it) },
                        range = 1..100,
                    )
                    ToggleRow(
                        title = "在摘要中显示最长间隔",
                        subtitle = "关闭后节奏卡不显示最长间隔一行",
                        checked = draft.showLongestGap,
                        onCheckedChange = { draft = draft.copy(showLongestGap = it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(
    title: String,
    subtitle: String,
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { if (value > range.first) onChange(value - 1) }) {
            Icon(Icons.Filled.Remove, contentDescription = "减少")
        }
        Text(text = value.toString(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { if (value < range.last) onChange(value + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "增加")
        }
    }
}

@Composable
private fun SegmentedRow(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(label) }
            }
        }
    }
}

@Suppress("unused")
private fun formatDays(days: Int): String = NumberFormat.getIntegerInstance().format(days)
