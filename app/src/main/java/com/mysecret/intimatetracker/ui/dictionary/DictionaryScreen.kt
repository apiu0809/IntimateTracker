package com.mysecret.intimatetracker.ui.dictionary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.repo.DictionaryItem
import com.mysecret.intimatetracker.data.repo.DictionaryKind
import com.mysecret.intimatetracker.domain.model.Intensity
import com.mysecret.intimatetracker.ui.component.EmptyState
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import com.mysecret.intimatetracker.ui.component.label
import kotlinx.coroutines.delay
import kotlin.math.abs

private fun DictionaryKind.titleRes(): Int = when (this) {
    DictionaryKind.SEX_TYPE -> R.string.dict_sex_type
    DictionaryKind.POSITION -> R.string.dict_position
    DictionaryKind.TOY -> R.string.dict_toy
    DictionaryKind.LOCATION -> R.string.dict_location
}

private fun DictionaryKind.yoursRes(): Int = when (this) {
    DictionaryKind.SEX_TYPE -> R.string.dict_yours_sex_type
    DictionaryKind.POSITION -> R.string.dict_yours_position
    DictionaryKind.TOY -> R.string.dict_yours_toy
    DictionaryKind.LOCATION -> R.string.dict_yours_location
}

@Composable
fun DictionaryScreen(
    kind: DictionaryKind,
    onBack: () -> Unit,
    viewModel: DictionaryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    val pendingDelete = viewModel.pendingDelete

    ScreenScaffold(title = stringResource(kind.titleRes()), onBack = onBack) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            if (items.isEmpty()) {
                EmptyState(text = stringResource(R.string.dict_empty))
            } else {
                DictionaryReorderList(
                    items = items,
                    yoursTitle = stringResource(kind.yoursRes()),
                    builtinTitle = stringResource(R.string.dict_builtin),
                    builtinHint = stringResource(R.string.dict_hint),
                    reorderTip = stringResource(R.string.dict_reorder_tip),
                    onToggle = viewModel::toggleHidden,
                    onDelete = viewModel::askDelete,
                    onMove = viewModel::move,
                )
            }

            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    }

    if (showAdd) {
        AddDictionaryDialog(
            withIntensity = kind == DictionaryKind.POSITION,
            onDismiss = { showAdd = false },
            onConfirm = { name, intensity ->
                viewModel.add(name, intensity)
                showAdd = false
            },
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(R.string.dict_delete_confirm, item.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

// ---- 排序（方案 B：全局序号、展示分段、段内拖拽） ----

private val RowHeight = 64.dp
private val SectionHeight = 44.dp
private val BuiltinHintHeight = 54.dp
private val TipHeight = 36.dp

private data class ItemSlot(
    val item: DictionaryItem,
    val top: Float,
    val builtin: Boolean,
)

private class SlotLayout(
    val custom: List<ItemSlot>,
    val builtin: List<ItemSlot>,
) {
    fun itemAt(y: Float, rowHeight: Float): ItemSlot? =
        custom.firstOrNull { y >= it.top && y < it.top + rowHeight }
            ?: builtin.firstOrNull { y >= it.top && y < it.top + rowHeight }
}

private fun buildLayout(
    items: List<DictionaryItem>,
    tipHeight: Float,
    sectionHeight: Float,
    hintHeight: Float,
    rowHeight: Float,
): SlotLayout {
    val custom = items.filter { !it.isBuiltin }
    val builtin = items.filter { it.isBuiltin }
    var y = tipHeight

    val customSlots = ArrayList<ItemSlot>(custom.size)
    if (custom.isNotEmpty()) {
        y += sectionHeight
        custom.forEach { item ->
            customSlots += ItemSlot(item, y, builtin = false)
            y += rowHeight
        }
    }
    val builtinSlots = ArrayList<ItemSlot>(builtin.size)
    if (builtin.isNotEmpty()) {
        y += sectionHeight + hintHeight
        builtin.forEach { item ->
            builtinSlots += ItemSlot(item, y, builtin = true)
            y += rowHeight
        }
    }
    return SlotLayout(customSlots, builtinSlots)
}

private class DragState {
    var id by mutableStateOf<String?>(null)
    var builtin by mutableStateOf(false)
    var startTop = 0f
    var startScroll = 0
    var offset by mutableStateOf(0f)

    /** -1 顶部边缘、0 无、1 底部边缘；拖到边缘时持续自动滚动。 */
    var edge by mutableStateOf(0)
    private var endedAt = 0L

    fun translation(scroll: Int): Float = offset + (scroll - startScroll)

    /** 拖拽中，或刚结束的 250ms 内，行点击都要让位给排序手势。 */
    fun suppressesClick(): Boolean =
        id != null || System.currentTimeMillis() - endedAt < 250

    fun stop() {
        id = null
        builtin = false
        startTop = 0f
        startScroll = 0
        offset = 0f
        edge = 0
        endedAt = System.currentTimeMillis()
    }
}

@Composable
private fun DictionaryReorderList(
    items: List<DictionaryItem>,
    yoursTitle: String,
    builtinTitle: String,
    builtinHint: String,
    reorderTip: String,
    onToggle: (DictionaryItem) -> Unit,
    onDelete: (DictionaryItem) -> Unit,
    onMove: (DictionaryItem, Int) -> Unit,
) {
    val density = LocalDensity.current
    val rowPx = with(density) { RowHeight.toPx() }
    val sectionPx = with(density) { SectionHeight.toPx() }
    val hintPx = with(density) { BuiltinHintHeight.toPx() }
    val tipPx = with(density) { TipHeight.toPx() }
    val edgePx = with(density) { 72.dp.toPx() }
    val scrollStepPx = with(density) { 12.dp.toPx() }

    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current

    val customItems = remember(items) { items.filter { !it.isBuiltin } }
    val builtinItems = remember(items) { items.filter { it.isBuiltin } }
    val layout = remember(items, tipPx, sectionPx, hintPx, rowPx) {
        buildLayout(items, tipPx, sectionPx, hintPx, rowPx)
    }
    val currentLayout by rememberUpdatedState(layout)
    val currentOnMove by rememberUpdatedState(onMove)
    val drag = remember { DragState() }

    LaunchedEffect(drag.id, drag.edge) {
        if (drag.id == null || drag.edge == 0) return@LaunchedEffect
        while (true) {
            scrollState.scrollBy(drag.edge * scrollStepPx)
            delay(16)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .pointerInput(Unit) {
                val viewportHeight = size.height
                detectDragGesturesAfterLongPress(
                    onDragStart = { position ->
                        // pointerInput 在滚动内容内部，position 已是内容坐标
                        val contentY = position.y
                        val hit = currentLayout.itemAt(contentY, rowPx)
                        if (hit != null) {
                            drag.id = hit.item.id
                            drag.builtin = hit.builtin
                            drag.startTop = hit.top
                            drag.startScroll = scrollState.value
                            drag.offset = 0f
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, amount ->
                        if (drag.id == null) return@detectDragGesturesAfterLongPress
                        change.consume()
                        drag.offset += amount.y
                        val viewportY = change.position.y - scrollState.value
                        drag.edge = when {
                            viewportY < edgePx -> -1
                            viewportY > viewportHeight - edgePx -> 1
                            else -> 0
                        }
                    },
                    onDragEnd = {
                        val id = drag.id
                        if (id != null) {
                            val center = drag.startTop + drag.translation(scrollState.value) + rowPx / 2f
                            val slots = if (drag.builtin) currentLayout.builtin else currentLayout.custom
                            val item = slots.firstOrNull { it.item.id == id }?.item
                            val target = slots.minByOrNull { abs(it.top + rowPx / 2f - center) }
                            if (item != null && target != null) {
                                currentOnMove(item, slots.indexOf(target))
                            }
                        }
                        drag.stop()
                    },
                    onDragCancel = { drag.stop() },
                )
            },
    ) {
        TipLine(reorderTip)
        if (customItems.isNotEmpty()) {
            SectionLabel(yoursTitle)
            customItems.forEach { item ->
                key(item.id) {
                    DictionaryDragRow(
                        item = item,
                        drag = drag,
                        scroll = { scrollState.value },
                        trailing = {
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.action_delete),
                                )
                            }
                        },
                        onClick = { onToggle(item) },
                    )
                }
            }
        }
        if (builtinItems.isNotEmpty()) {
            SectionLabel(builtinTitle)
            HintLine(builtinHint)
            builtinItems.forEach { item ->
                key(item.id) {
                    DictionaryDragRow(
                        item = item,
                        drag = drag,
                        scroll = { scrollState.value },
                        trailing = {},
                        onClick = { onToggle(item) },
                    )
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun TipLine(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TipHeight)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SectionHeight)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun HintLine(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BuiltinHintHeight)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DictionaryDragRow(
    item: DictionaryItem,
    drag: DragState,
    scroll: () -> Int,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val dragging = drag.id == item.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                if (dragging) {
                    translationY = drag.translation(scroll())
                    shadowElevation = 12.dp.toPx()
                    shape = RoundedCornerShape(12.dp)
                }
            }
            .background(
                color = if (dragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { if (!drag.suppressesClick()) onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isHidden) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.isHidden) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            item.intensity?.let {
                Text(
                    text = "${stringResource(R.string.dict_intensity)}：${it.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun AddDictionaryDialog(
    withIntensity: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Intensity) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var intensity by remember { mutableStateOf(Intensity.MEDIUM) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dict_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (withIntensity) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.dict_intensity),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable { expanded = true }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(intensity.label())
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            Intensity.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label()) },
                                    onClick = {
                                        intensity = option
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, intensity) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
