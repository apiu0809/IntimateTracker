package com.mysecret.intimatetracker.ui.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Tune
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.data.repo.DictionaryItem
import com.mysecret.intimatetracker.domain.withDeletedMarker
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.repo.FormField
import com.mysecret.intimatetracker.domain.model.Initiator
import com.mysecret.intimatetracker.domain.model.Protection
import com.mysecret.intimatetracker.ui.component.AppBottomSheet
import com.mysecret.intimatetracker.ui.component.FormSelectField
import com.mysecret.intimatetracker.ui.component.FormTextField
import com.mysecret.intimatetracker.ui.component.MultiChipGroup
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import com.mysecret.intimatetracker.ui.component.StarRating
import com.mysecret.intimatetracker.ui.component.Stepper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)

private val emojiGroups: List<Pair<String, List<String>>> = listOf(
    "基本" to listOf("🌟", "⭐", "🌧️", "🖐️", "💬", "🔥"),
    "行为" to listOf("🍆", "👙", "🖕", "🥒", "🍑", "👥", "🧬", "🔒", "💧", "💦", "👶", "⏰", "🌙", "☀️"),
    "地点" to listOf("🏠", "🚿", "🛁", "🛏️", "🌴", "🌊", "🌳", "🏛️", "🎬", "👀", "🚗", "🚋", "✈️", "🚢"),
    "情感" to listOf("❤️", "💕", "💔", "💖", "💤", "😴", "😍", "😈", "💩", "✨", "💫", "⚡"),
    "趣味" to listOf("🎉", "🎂", "🎁", "🍷", "🍓", "🍒", "🍌", "🍕", "☕", "🌹", "🌸", "🌈", "🍀", "❄️", "🐱", "🐶", "👑"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    onClose: () -> Unit,
    viewModel: AddRecordViewModel = hiltViewModel(),
) {
    val form = viewModel.form
    val visibility by viewModel.visibility.collectAsStateWithLifecycle()
    val show: (FormField) -> Boolean = { field -> visibility[field] ?: true }
    var showSimplified by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showPhotoLimit by remember { mutableStateOf(false) }
    val partners by viewModel.partners.collectAsStateWithLifecycle()
    val sexTypes by viewModel.sexTypes.collectAsStateWithLifecycle()
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val toys by viewModel.toys.collectAsStateWithLifecycle()

    val unsetLabel = stringResource(R.string.common_unset)
    var showDatePicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(if (viewModel.isEditing) R.string.record_edit else R.string.record_new),
        subtitle = dateFormat.format(Date(form.timestamp)),
        onSubtitleClick = { showDatePicker = true },
        onBack = onClose,
        closeIcon = true,
        actions = {
            if (viewModel.isEditing) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.record_detail_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = { viewModel.save(onClose) }) {
                Text(stringResource(R.string.action_save))
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (show(FormField.ICON)) item {
                FormCard(stringResource(R.string.record_icon), Icons.Outlined.Mood) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showEmojiPicker = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = form.icon ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "自定义",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            if (show(FormField.PHOTO)) item {
                FormCard(stringResource(R.string.record_photo), Icons.Outlined.Image) {
                    val context = LocalContext.current
                    val picker = rememberLauncherForActivityResult(
                        ActivityResultContracts.PickMultipleVisualMedia(9),
                    ) { uris -> if (uris.isNotEmpty()) viewModel.addUris(uris) }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(viewModel.existingPhotos) { photo ->
                            AsyncImage(
                                model = File(context.filesDir, photo.relPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).animateItem(),
                            )
                        }
                        items(viewModel.pendingUris) { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).animateItem(),
                            )
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .clickable {
                                        picker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.record_photo))
                            }
                        }
                    }
                }
            }
            if (show(FormField.PARTNER)) item {
                FormCard(stringResource(R.string.partner_title), Icons.Outlined.PeopleAlt) {
                    PartnerChips(
                        partners = partners.toChips(form.partnerIds),
                        selected = form.partnerIds,
                        onToggle = viewModel::togglePartner,
                    )
                }
            }

            if (show(FormField.INITIATOR)) item {
                FormCard(stringResource(R.string.record_initiator), Icons.Outlined.MeetingRoom) {
                    FormSelectField(
                        value = when (form.initiator) {
                            Initiator.ME -> stringResource(R.string.initiator_me)
                            Initiator.PARTNER -> stringResource(R.string.initiator_partner)
                            Initiator.BOTH -> stringResource(R.string.initiator_both)
                            null -> stringResource(R.string.common_unset)
                        },
                        options = listOf(
                            null to stringResource(R.string.common_unset),
                            Initiator.ME to stringResource(R.string.initiator_me),
                            Initiator.PARTNER to stringResource(R.string.initiator_partner),
                            Initiator.BOTH to stringResource(R.string.initiator_both),
                        ),
                        onSelect = { value -> viewModel.update { it.copy(initiator = value) } },
                    )
                }
            }

            if (show(FormField.DURATION)) item {
                FormCard(stringResource(R.string.record_duration), Icons.Outlined.Schedule) {
                    Column {
                        val presets = listOf(10, 30, 45, 60, 120)
                        MultiChipGroup(
                            items = presets.map { it.toString() to "$it" },
                            selected = form.durationMinutes?.let { setOf(it.toString()) } ?: emptySet(),
                            onToggle = { value -> viewModel.update { it.copy(durationMinutes = value.toInt()) } },
                        )
                        Spacer(Modifier.height(8.dp))
                        Stepper(
                            value = form.durationMinutes ?: 0,
                            onMinus = {
                                viewModel.update { it.copy(durationMinutes = ((it.durationMinutes ?: 0) - 5).coerceAtLeast(0)) }
                            },
                            onPlus = {
                                viewModel.update { it.copy(durationMinutes = ((it.durationMinutes ?: 0) + 5).coerceAtMost(600)) }
                            },
                            display = { value ->
                                if (form.durationMinutes == null) unsetLabel else "$value 分钟"
                            },
                            range = 0..600,
                        )
                    }
                }
            }

            if (show(FormField.SATISFACTION)) item {
                FormCard(stringResource(R.string.record_satisfaction), Icons.Outlined.StarBorder) {
                    StarRating(value = form.satisfaction, onSelect = { value ->
                        viewModel.setSatisfaction(value)
                    })
                }
            }

            if (show(FormField.SEX_TYPE)) item {
                FormCard(stringResource(R.string.record_type), Icons.Outlined.SwapHoriz) {
                    MultiChipGroup(
                        items = sexTypes.toChipItems(form.sexTypeIds),
                        selected = form.sexTypeIds,
                        onToggle = viewModel::toggleSexType,
                    )
                }
            }

            if (show(FormField.TOY)) item {
                FormCard(stringResource(R.string.dict_toy), Icons.Outlined.Category) {
                    MultiChipGroup(
                        items = toys.toChipItems(form.toyIds),
                        selected = form.toyIds,
                        onToggle = viewModel::toggleToy,
                    )
                }
            }
            if (show(FormField.LOCATION)) item {
                FormCard(stringResource(R.string.dict_location), Icons.Outlined.LocationOn) {
                    MultiChipGroup(
                        items = locations.toChipItems(form.locationIds),
                        selected = form.locationIds,
                        onToggle = viewModel::toggleLocation,
                    )
                }
            }

            if (show(FormField.POSITION)) item {
                FormCard(stringResource(R.string.dict_position), Icons.Outlined.Accessibility) {
                    MultiChipGroup(
                        items = positions.toChipItems(form.positionIds),
                        selected = form.positionIds,
                        onToggle = viewModel::togglePosition,
                    )
                }
            }

            if (show(FormField.PROTECTION)) item {
                FormCard(stringResource(R.string.record_protection), Icons.Outlined.Shield) {
                    FormSelectField(
                        value = when (form.protection) {
                            Protection.YES -> stringResource(R.string.protection_yes)
                            Protection.NO -> stringResource(R.string.protection_no)
                            Protection.UNKNOWN -> stringResource(R.string.protection_unknown)
                            null -> stringResource(R.string.common_unset)
                        },
                        options = listOf(
                            null to stringResource(R.string.common_unset),
                            Protection.YES to stringResource(R.string.protection_yes),
                            Protection.NO to stringResource(R.string.protection_no),
                            Protection.UNKNOWN to stringResource(R.string.protection_unknown),
                        ),
                        onSelect = { value -> viewModel.update { it.copy(protection = value) } },
                    )
                }
            }

            if (show(FormField.MY_ORGASM)) item {
                FormCard(stringResource(R.string.record_my_orgasm), Icons.Outlined.FavoriteBorder) {
                    Stepper(
                        value = form.myOrgasm,
                        onMinus = { viewModel.update { it.copy(myOrgasm = (it.myOrgasm - 1).coerceAtLeast(0)) } },
                        onPlus = { viewModel.update { it.copy(myOrgasm = (it.myOrgasm + 1).coerceAtMost(20)) } },
                        range = 0..20,
                    )
                }
            }

            if (show(FormField.PARTNER_ORGASM)) item {
                FormCard(stringResource(R.string.record_partner_orgasm), Icons.Outlined.FavoriteBorder) {
                    Stepper(
                        value = form.partnerOrgasm,
                        onMinus = { viewModel.update { it.copy(partnerOrgasm = (it.partnerOrgasm - 1).coerceAtLeast(0)) } },
                        onPlus = { viewModel.update { it.copy(partnerOrgasm = (it.partnerOrgasm + 1).coerceAtMost(20)) } },
                        range = 0..20,
                    )
                }
            }

            if (show(FormField.NOTES)) item {
                FormCard(stringResource(R.string.record_notes), Icons.Outlined.Menu) {
                    FormTextField(
                        value = form.notes,
                        onValueChange = { value -> viewModel.update { it.copy(notes = value) } },
                        placeholder = stringResource(R.string.record_notes_hint),
                        singleLine = false,
                        minLines = 3,
                    )
                }
            }

            item {
                FormCard("简化输入", Icons.Outlined.Tune) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSimplified = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "在记录表单中隐藏你用不到的字段",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showPhotoLimit) {
        AlertDialog(
            onDismissRequest = { showPhotoLimit = false },
            title = { Text("已达照片上限") },
            text = { Text("每条记录最多可包含 3 张照片。") },
            confirmButton = {
                TextButton(onClick = { showPhotoLimit = false }) { Text("确定") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.record_detail_delete)) },
            text = { Text(stringResource(R.string.record_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onClose)
                }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = form.timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        viewModel.update { it.copy(timestamp = millis) }
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }

    viewModel.saveError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveError,
            title = { Text(stringResource(R.string.record_save_failed)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSaveError) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
        )
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            onDismiss = { showEmojiPicker = false },
            onSelect = { emoji ->
                viewModel.setIcon(emoji)
                showEmojiPicker = false
            },
        )
    }

    if (showSimplified) {
        SimplifiedInputSheet(
            visibility = visibility,
            onToggle = viewModel::setFieldVisible,
            onDismiss = { showSimplified = false },
        )
    }
}

@Composable
private fun FormCard(title: String, icon: ImageVector? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun EmojiPickerDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_icon)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                emojiGroups.forEach { (group, emojis) ->
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(emojis) { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier
                                .clickable { onSelect(emoji) }
                                .padding(8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private data class PartnerChip(val id: String, val name: String, val avatarPath: String?)

/** 编辑记录时额外保留「已删 / 已归档但本记录已选中」的伙伴（历史真相），新建时只列当前可用。 */
private fun List<PartnerEntity>.toChips(selected: Set<String>): List<PartnerChip> =
    filter { it.id in selected || (it.deletedAt == null && !it.isArchived) }
        .map { PartnerChip(it.id, it.name.withDeletedMarker(it.deletedAt != null), it.avatarPath) }

/** 同上：已删的字典项只要被本记录选中就继续显示，并标「（已删除）」。 */
private fun List<DictionaryItem>.toChipItems(selected: Set<String>): List<Pair<String, String>> =
    filter { it.id in selected || !it.deleted }
        .map { it.id to it.name.withDeletedMarker(it.deleted) }

@Composable
private fun PartnerChips(
    partners: List<PartnerChip>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    val context = LocalContext.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        partners.forEach { chip ->
            val isSelected = chip.id in selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onToggle(chip.id) }
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val avatar = chip.avatarPath
                    if (avatar != null) {
                        AsyncImage(
                            model = File(context.filesDir, avatar),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = chip.name.take(1),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = chip.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun SimplifiedInputSheet(
    visibility: Map<FormField, Boolean>,
    onToggle: (FormField, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val fields = listOf(
        FormField.ICON to "记录图示",
        FormField.PHOTO to "照片",
        FormField.PARTNER to "伴侣",
        FormField.INITIATOR to "主动方",
        FormField.DURATION to "时长",
        FormField.SATISFACTION to "满意度",
        FormField.SEX_TYPE to "类型",
        FormField.TOY to "情趣用品",
        FormField.LOCATION to "地点",
        FormField.POSITION to "姿势",
        FormField.PROTECTION to "防护措施",
        FormField.MY_ORGASM to "你的高潮",
        FormField.PARTNER_ORGASM to "伴侣的高潮",
        FormField.NOTES to "备注",
    )
    AppBottomSheet(
        title = "简化输入",
        onDismiss = onDismiss,
        actionLabel = "关闭",
        onAction = onDismiss,
    ) {
        Text(
            text = "在记录表单中隐藏你用不到的字段",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "至少要保留 2 个字段显示。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(fields) { (field, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = visibility[field] ?: true,
                        onCheckedChange = { onToggle(field, it) },
                    )
                }
            }
        }
    }
}
