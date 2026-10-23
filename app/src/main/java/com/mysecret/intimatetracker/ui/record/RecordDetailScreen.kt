package com.mysecret.intimatetracker.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.compose.AsyncImage
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.db.RecordEntity
import com.mysecret.intimatetracker.domain.model.Initiator
import com.mysecret.intimatetracker.domain.model.Protection
import com.mysecret.intimatetracker.ui.component.EmptyState
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val titleFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: RecordDetailViewModel = hiltViewModel(),
) {
    val detail = viewModel.detail

    // 从编辑页返回时详情页会重新 RESUMED，此时重新读取，避免显示编辑前的旧内容
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.load()
    }

    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeletePhoto by remember { mutableStateOf<com.mysecret.intimatetracker.data.db.PhotoEntity?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = detail?.record?.let {
                            titleFormatter.format(
                                Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate(),
                            )
                        } ?: "",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    detail?.record?.let { record ->
                        IconButton(onClick = { onEdit(record.id) }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.record_detail_edit),
                            )
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.record_detail_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        val current = detail
        if (current == null) {
            EmptyState(text = stringResource(R.string.history_empty), modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 顺序与新增/编辑记录表单保持一致：
                // 照片 → 伴侣 → 主动方 → 时长 → 满意度 → 类型 → 情趣用品 → 地点 → 姿势 → 防护 → 高潮 → 备注
                if (current.photos.isNotEmpty()) {
                    item { PhotoCard(Icons.Outlined.Image, current.photos, onDelete = { photo -> confirmDeletePhoto = photo }, onClick = { index -> viewerIndex = index }) }
                }
                current.partners.takeIf { it.isNotEmpty() }?.let { values ->
                    item { ChipCard(Icons.Outlined.PeopleAlt, stringResource(R.string.partner_title), values) }
                }
                current.record.initiator?.let { initiator ->
                    item {
                        ValueCard(
                            icon = Icons.Outlined.MeetingRoom,
                            label = stringResource(R.string.record_initiator),
                            value = when (initiator) {
                                Initiator.ME -> stringResource(R.string.initiator_me)
                                Initiator.PARTNER -> stringResource(R.string.initiator_partner)
                                Initiator.BOTH -> stringResource(R.string.initiator_both)
                            },
                        )
                    }
                }
                current.record.durationMinutes?.let { minutes ->
                    item { ValueCard(Icons.Outlined.Schedule, stringResource(R.string.record_duration), "$minutes 分钟") }
                }
                current.record.satisfaction?.let { stars ->
                    item { StarsCard(Icons.Outlined.StarBorder, stars) }
                }
                current.types.takeIf { it.isNotEmpty() }?.let { values ->
                    item { ChipCard(Icons.Outlined.SwapHoriz, stringResource(R.string.record_type), values) }
                }
                current.toys.takeIf { it.isNotEmpty() }?.let { values ->
                    item { ChipCard(Icons.Outlined.Category, stringResource(R.string.dict_toy), values) }
                }
                current.locations.takeIf { it.isNotEmpty() }?.let { values ->
                    item { ChipCard(Icons.Outlined.LocationOn, stringResource(R.string.dict_location), values) }
                }
                current.positions.takeIf { it.isNotEmpty() }?.let { values ->
                    item { ChipCard(Icons.Outlined.Accessibility, stringResource(R.string.dict_position), values) }
                }
                current.record.protectionUsed?.let { protection ->
                    item {
                        ValueCard(
                            icon = Icons.Outlined.Shield,
                            label = stringResource(R.string.record_protection),
                            value = when (protection) {
                                Protection.YES -> stringResource(R.string.protection_yes)
                                Protection.NO -> stringResource(R.string.protection_no)
                                Protection.UNKNOWN -> stringResource(R.string.protection_unknown)
                            },
                        )
                    }
                }
                if (current.record.myOrgasmCount > 0) {
                    item {
                        ValueCard(
                            icon = Icons.Outlined.FavoriteBorder,
                            label = stringResource(R.string.record_my_orgasm),
                            value = current.record.myOrgasmCount.toString(),
                        )
                    }
                }
                if (current.record.partnerOrgasmCount > 0) {
                    item {
                        ValueCard(
                            icon = Icons.Outlined.FavoriteBorder,
                            label = stringResource(R.string.record_partner_orgasm),
                            value = current.record.partnerOrgasmCount.toString(),
                        )
                    }
                }
                current.record.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    item { ValueCard(Icons.Outlined.Menu, stringResource(R.string.record_notes), notes) }
                }
            }
        }
    }

    confirmDeletePhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { confirmDeletePhoto = null },
            title = { Text(stringResource(R.string.record_detail_delete)) },
            text = { Text("确定删除这张照片？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePhoto(photo)
                    confirmDeletePhoto = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePhoto = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    viewerIndex?.let { index ->
        val photos = detail?.photos.orEmpty()
        if (photos.isNotEmpty()) {
            com.mysecret.intimatetracker.ui.component.PhotoViewer(
                photos = photos.map {
                    com.mysecret.intimatetracker.ui.component.ViewerPhoto(
                        id = it.id,
                        relPath = it.relPath,
                        recordId = it.recordId,
                        timestamp = it.createdAt,
                    )
                },
                startIndex = index,
                showRecordButton = false,
                onOpenRecord = {},
                onDismiss = { viewerIndex = null },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.record_detail_delete)) },
            text = { Text(stringResource(R.string.record_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ValueCard(icon: ImageVector, label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ChipCard(icon: ImageVector, label: String, values: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.take(3).forEach { value ->
                    AssistChip(onClick = {}, label = { Text(value) })
                }
            }
        }
    }
}

@Composable
private fun StarsCard(icon: ImageVector, stars: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.record_satisfaction),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                repeat(5) { index ->
                    Icon(
                        imageVector = if (index < stars) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = if (index < stars) {
                            com.mysecret.intimatetracker.ui.theme.StarColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(
    icon: ImageVector,
    photos: List<com.mysecret.intimatetracker.data.db.PhotoEntity>,
    onDelete: (com.mysecret.intimatetracker.data.db.PhotoEntity) -> Unit,
    onClick: (Int) -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.record_photo),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                    androidx.compose.foundation.layout.Box(Modifier.size(96.dp)) {
                        AsyncImage(
                            model = File(context.filesDir, photo.relPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).clickable { onClick(index) },
                        )
                        androidx.compose.material3.IconButton(
                            onClick = { onDelete(photo) },
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.record_detail_delete),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private fun RecordEntity.dateOrNull(): LocalDate? =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
