package com.mysecret.intimatetracker.ui.dictionary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.repo.DictionaryItem
import com.mysecret.intimatetracker.data.repo.DictionaryKind
import com.mysecret.intimatetracker.domain.model.Intensity
import com.mysecret.intimatetracker.ui.component.AppFab
import com.mysecret.intimatetracker.ui.component.EmptyState
import com.mysecret.intimatetracker.ui.component.FormSelectField
import com.mysecret.intimatetracker.ui.component.FormTextField
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import com.mysecret.intimatetracker.ui.component.label
import com.mysecret.intimatetracker.ui.component.pressScale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---- 字典类型文案 ----

private fun DictionaryKind.titleRes(): Int = when (this) {
    DictionaryKind.SEX_TYPE -> R.string.dict_sex_type
    DictionaryKind.POSITION -> R.string.dict_position
    DictionaryKind.TOY -> R.string.dict_toy
    DictionaryKind.LOCATION -> R.string.dict_location
}

private fun DictionaryKind.addTitleRes(): Int = when (this) {
    DictionaryKind.SEX_TYPE -> R.string.dict_add_sex_type
    DictionaryKind.POSITION -> R.string.dict_add_position
    DictionaryKind.TOY -> R.string.dict_add_toy
    DictionaryKind.LOCATION -> R.string.dict_add_location
}



private fun DictionaryKind.editTitleRes(): Int = when (this) {
    DictionaryKind.SEX_TYPE -> R.string.dict_edit_sex_type
    DictionaryKind.POSITION -> R.string.dict_edit_position
    DictionaryKind.TOY -> R.string.dict_edit_toy
    DictionaryKind.LOCATION -> R.string.dict_edit_location
}

/** 弹窗的初始内容：[item] 为空表示新增。 */
private data class EditorRequest(
    val item: DictionaryItem? = null,
    val name: String = "",
    val note: String = "",
    val intensity: Intensity = Intensity.VERY_LOW,
)

@Composable
fun DictionaryScreen(
    kind: DictionaryKind,
    onBack: () -> Unit,
    viewModel: DictionaryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val deletedItems by viewModel.deletedItems.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    val pendingDelete = viewModel.pendingDelete

    ScreenScaffold(title = stringResource(kind.titleRes()), onBack = onBack) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            // 只有「在用 + 已删除」都为空时才是真正的空态；
            // 否则即使在用项全被删光，也要把底部的「已删除」区露出来，否则没法恢复。
            AnimatedContent(
                targetState = items.isEmpty() && deletedItems.isEmpty(),
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.fillMaxSize(),
                label = "dictionaryContent",
            ) { empty ->
                if (empty) {
                    EmptyState(text = stringResource(R.string.dict_empty))
                } else {
                    DictionaryReorderList(
                        items = items,
                        deletedItems = deletedItems,
                        onRestore = viewModel::restore,
                        onPurge = viewModel::askPurge,
                        onEdit = { item ->
                            editor = EditorRequest(
                                item = item,
                                name = item.name,
                                note = item.note.orEmpty(),
                                intensity = item.intensity ?: Intensity.VERY_LOW,
                            )
                        },
                        onMove = viewModel::move,
                    )
                }
            }

            AppFab(
                contentDescription = stringResource(R.string.action_add),
                onClick = { editor = EditorRequest() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
            )
        }
    }

    val request = editor
    if (request != null) {
        key(request.item?.id, request.name) {
            DictionaryEditorDialog(
                title = stringResource(
                    if (request.item == null) kind.addTitleRes() else kind.editTitleRes(),
                ),

                withIntensity = kind == DictionaryKind.POSITION,
                initialName = request.name,
                initialNote = request.note,
                initialIntensity = request.intensity,
                canDelete = request.item != null,
                onDismiss = { editor = null },
                onDelete = {
                    request.item?.let { item ->
                        editor = null
                        viewModel.askDelete(item)
                    }
                },
                onSave = { name, note, intensity ->
                    viewModel.submit(request.item, name, note, intensity)
                    editor = null
                },
            )
        }
    }

    viewModel.pendingSave?.let { pending ->
        val editing = pending.item != null
        val deleted = pending.duplicate.deleted
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.duplicate_title)) },
            text = {
                Text(
                    stringResource(
                        if (!editing && deleted) R.string.duplicate_dict_message_deleted
                        else R.string.duplicate_dict_message,
                        pending.duplicate.name,
                    ),
                )
            },
            confirmButton = {
                when {
                    editing -> TextButton(onClick = viewModel::saveAnyway) {
                        Text(stringResource(R.string.duplicate_save_anyway))
                    }

                    deleted -> TextButton(onClick = viewModel::restoreDuplicate) {
                        Text(stringResource(R.string.duplicate_restore_existing))
                    }

                    else -> TextButton(onClick = {
                        val duplicate = pending.duplicate
                        viewModel.dismissDuplicate()
                        editor = EditorRequest(
                            item = duplicate,
                            name = duplicate.name,
                            note = duplicate.note.orEmpty(),
                            intensity = duplicate.intensity ?: Intensity.VERY_LOW,
                        )
                    }) {
                        Text(stringResource(R.string.duplicate_edit_existing))
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::dismissDuplicate) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    if (!editing) {
                        TextButton(onClick = viewModel::saveAnyway) {
                            Text(stringResource(R.string.duplicate_create_anyway))
                        }
                    }
                }
            },
        )
    }

    viewModel.pendingPurge?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPurge,
            title = { Text(stringResource(R.string.action_purge)) },
            text = { Text(stringResource(R.string.purge_confirm_dict, target.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPurge) {
                    Text(
                        text = stringResource(R.string.action_purge),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelPurge) {
                    Text(stringResource(R.string.action_cancel))
                }
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
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
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

// ---- 列表（拖动行尾手柄排序，复刻参考项目的选项管理） ----

private val RowHeight = 76.dp
private val HandleZoneWidth = 48.dp

private class DragState {
    var id by mutableStateOf<String?>(null)
    var startIndex = 0
    var startScroll = 0
    var offset by mutableStateOf(0f)
    var target by mutableStateOf(0)
    var edge by mutableStateOf(0)

    fun translation(scroll: Int): Float = offset + (scroll - startScroll)

    fun stop() {
        id = null
        startIndex = 0
        startScroll = 0
        offset = 0f
        target = 0
        edge = 0
    }
}

@Composable
private fun DictionaryReorderList(
    items: List<DictionaryItem>,
    deletedItems: List<DictionaryItem>,
    onRestore: (DictionaryItem) -> Unit,
    onPurge: (DictionaryItem) -> Unit,
    onEdit: (DictionaryItem) -> Unit,
    onMove: (DictionaryItem, Int) -> Unit,
) {
    val density = LocalDensity.current
    val rowPx = with(density) { RowHeight.toPx() }
    val handlePx = with(density) { HandleZoneWidth.toPx() }
    val edgePx = with(density) { 64.dp.toPx() }
    val scrollStepPx = with(density) { 12.dp.toPx() }

    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current

    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)
    val dragState = remember { DragState() }

    fun refreshTarget() {
        val count = currentItems.size
        if (count == 0) return
        val moved = (dragState.translation(scrollState.value) / rowPx).roundToInt()
        dragState.target = (dragState.startIndex + moved).coerceIn(0, count - 1)
    }

    // 拖到上下边缘时持续滚动，手感对齐参考项目的 ReorderableListView
    LaunchedEffect(dragState.id, dragState.edge) {
        if (dragState.id == null || dragState.edge == 0) return@LaunchedEffect
        while (true) {
            scrollState.scrollBy(dragState.edge * scrollStepPx)
            refreshTarget()
            delay(16)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .pointerInput(rowPx, handlePx) {
                val viewportHeight = size.height
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 只有落点在行尾手柄上才起拖，其余手势留给列表滚动与行点击
                    if (down.position.x < size.width - handlePx) return@awaitEachGesture
                    val index = (down.position.y / rowPx).toInt()
                    val item = currentItems.getOrNull(index) ?: return@awaitEachGesture
                    down.consume()

                    dragState.id = item.id
                    dragState.startIndex = index
                    dragState.startScroll = scrollState.value
                    dragState.offset = 0f
                    dragState.target = index
                    dragState.edge = 0
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    val completed = drag(down.id) { change ->
                        // 先取位移再 consume：positionChange() 对被消费的事件返回零
                        dragState.offset += change.positionChange().y
                        change.consume()
                        refreshTarget()
                        val viewportTop = dragState.startIndex * rowPx + dragState.offset - dragState.startScroll
                        dragState.edge = when {
                            viewportTop < edgePx -> -1
                            viewportTop + rowPx > viewportHeight - edgePx -> 1
                            else -> 0
                        }
                    }
                    if (completed && dragState.target != dragState.startIndex) {
                        currentOnMove(item, dragState.target)
                    }
                    dragState.stop()
                }
            },
    ) {
        items.forEachIndexed { index, item ->
            key(item.id) {
                val dragging = dragState.id == item.id
                val shift = when {
                    dragging -> dragState.translation(scrollState.value)
                    dragState.id == null -> 0f
                    // 拖动中其余行让位，实时预览落点
                    dragState.startIndex < dragState.target &&
                        index in (dragState.startIndex + 1)..dragState.target -> -rowPx
                    dragState.startIndex > dragState.target &&
                        index in dragState.target until dragState.startIndex -> rowPx
                    else -> 0f
                }
                DictionaryRow(
                    item = item,
                    subtitle = subtitleFor(item),
                    dragging = dragging,
                    shift = shift,
                    onClick = { onEdit(item) },
                )
            }
        }
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.dict_empty_active),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            )
        }
        if (deletedItems.isNotEmpty()) {
            DeletedDictionarySection(deletedItems, onRestore, onPurge)
        }
        Spacer(Modifier.height(96.dp))
    }
}

/** 管理页底部的「已删除」折叠区：点「恢复」即可把软删项放回列表。 */
@Composable
private fun DeletedDictionarySection(
    items: List<DictionaryItem>,
    onRestore: (DictionaryItem) -> Unit,
    onPurge: (DictionaryItem) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "deletedChevron",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.deleted_section, items.size),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(200),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(160)),
            exit = shrinkVertically(
                animationSpec = tween(180),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(120)),
        ) {
            Column {
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                textDecoration = TextDecoration.LineThrough,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.note?.takeIf { it.isNotBlank() }?.let { note ->
                                Text(
                                    text = note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        TextButton(onClick = { onRestore(item) }) {
                            Text(stringResource(R.string.action_restore))
                        }
                        IconButton(onClick = { onPurge(item) }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.action_purge),
                                tint = colors.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun subtitleFor(item: DictionaryItem): String? {
    val parts = buildList {
        item.intensity?.let { add("${stringResource(R.string.dict_intensity)}：${it.label()}") }
        item.note?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun DictionaryRow(
    item: DictionaryItem,
    subtitle: String?,
    dragging: Boolean,
    shift: Float,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(18.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = shift
                if (dragging) {
                    shadowElevation = 12.dp.toPx()
                    shape = cardShape
                }
            }
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .pressScale(interactionSource, pressedScale = if (dragging) 1f else 0.98f)
            .clip(cardShape)
            .background(
                if (dragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            ) { onClick() }
            .padding(start = 16.dp, end = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 拖动手柄：按住即拖，无需长按
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.dict_drag_handle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
// ---- 新增 / 编辑弹窗（输入框 / 选项框与「新增伴侣」同一套外框样式 + 胶囊保存按钮） ----

@Composable
private fun DictionaryEditorDialog(
    title: String,

    withIntensity: Boolean,
    initialName: String,
    initialNote: String,
    initialIntensity: Intensity,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, String, Intensity) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var note by remember { mutableStateOf(initialNote) }
    var intensity by remember { mutableStateOf(initialIntensity) }

    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(18.dp))
                FormTextField(
                    label = stringResource(R.string.dict_name),
                    value = name,
                    onValueChange = { name = it },
                )
                Spacer(Modifier.height(8.dp))
                FormTextField(
                    label = stringResource(R.string.dict_note),
                    value = note,
                    onValueChange = { note = it },
                )
                if (withIntensity) {
                    Spacer(Modifier.height(8.dp))
                    FormSelectField(
                        label = stringResource(R.string.dict_intensity),
                        value = intensity.label(),
                        options = Intensity.entries.map { it to it.label() },
                        onSelect = { intensity = it },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canDelete) {
                        TextButton(onClick = onDelete) {
                            Text(
                                text = stringResource(R.string.action_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onSave(name, note, intensity) },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

