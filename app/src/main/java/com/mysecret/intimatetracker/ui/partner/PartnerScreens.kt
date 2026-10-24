package com.mysecret.intimatetracker.ui.partner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecret.intimatetracker.R
import com.mysecret.intimatetracker.data.db.PartnerEntity
import com.mysecret.intimatetracker.domain.model.Gender
import com.mysecret.intimatetracker.domain.model.RelationshipType
import com.mysecret.intimatetracker.ui.component.AppFab
import com.mysecret.intimatetracker.ui.component.EmptyState
import com.mysecret.intimatetracker.ui.component.FormSelectField
import com.mysecret.intimatetracker.ui.component.FormTextField
import com.mysecret.intimatetracker.ui.component.ScreenScaffold
import com.mysecret.intimatetracker.ui.component.label
import com.mysecret.intimatetracker.ui.component.pressScale

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PartnerListScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedContentScope? = null,
    viewModel: PartnerListViewModel = hiltViewModel(),
) {
    val state by viewModel.partners.collectAsStateWithLifecycle()

    ScreenScaffold(title = stringResource(R.string.partner_title)) { modifier ->
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = state.active.isEmpty() && state.archived.isEmpty() && state.deleted.isEmpty(),
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier.fillMaxSize(),
                label = "partnerListContent",
            ) { empty ->
                if (empty) {
                    EmptyState(text = stringResource(R.string.partner_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 120.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.active, key = { it.id }) { partner ->
                            PartnerRow(
                                partner = partner,
                                onClick = { onOpen(partner.id) },
                                modifier = Modifier.animateItem(),
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                        if (state.archived.isNotEmpty()) {
                            item {
                                Text(
                                    text = "已归档（${state.archived.size}）",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.animateItem().padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                                )
                            }
                            items(state.archived, key = { it.id }) { partner ->
                                PartnerRow(
                                    partner = partner,
                                    onClick = { onOpen(partner.id) },
                                    archived = true,
                                    modifier = Modifier.animateItem(),
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            }
                        }
                        if (state.deleted.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.deleted_section, state.deleted.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.animateItem().padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                                )
                            }
                            items(state.deleted, key = { it.id }) { partner ->
                                DeletedPartnerRow(
                                    partner = partner,
                                    onRestore = { viewModel.restore(partner.id) },
                                    onPurge = { viewModel.askPurge(partner) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
            AppFab(
                contentDescription = stringResource(R.string.partner_add),
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 100.dp),
            )
        }
    }

    viewModel.pendingPurge?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPurge,
            title = { Text(stringResource(R.string.action_purge)) },
            text = { Text(stringResource(R.string.purge_confirm_partner, target.name)) },
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
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.partnerAvatarModifier(
    partnerId: String,
    animatedVisibilityScope: AnimatedContentScope,
): Modifier = Modifier.sharedElement(
    sharedContentState = rememberSharedContentState(key = "partner-avatar-$partnerId"),
    animatedVisibilityScope = animatedVisibilityScope,
    renderInOverlayDuringTransition = true,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PartnerRow(
    partner: PartnerEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    archived: Boolean = false,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedContentScope? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val avatarModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            partnerAvatarModifier(partner.id, animatedVisibilityScope)
        }
    } else {
        Modifier
    }
    val shape = RoundedCornerShape(16.dp)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().pressScale(interactionSource),
        shape = shape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .alpha(if (archived) 0.55f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PartnerAvatar(partner.avatarPath, partner.name, 44.dp, modifier = avatarModifier)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = partner.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (archived) {
                        "已归档 · ${partner.relationshipType.label()}"
                    } else {
                        partner.relationshipType.label()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DeletedPartnerRow(
    partner: PartnerEntity,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .alpha(0.55f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PartnerAvatar(partner.avatarPath, partner.name, 44.dp)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = partner.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = partner.relationshipType.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.action_restore))
            }
            IconButton(onClick = onPurge) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_purge),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PartnerEditScreen(
    partnerId: String?,
    onDone: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedContentScope? = null,
    viewModel: PartnerEditViewModel = hiltViewModel(),
) {
    if (!viewModel.loaded) return

    ScreenScaffold(
        title = stringResource(if (partnerId == null) R.string.partner_add else R.string.partner_edit),
        onBack = onDone,
        actions = {
            if (partnerId != null) {
                IconButton(onClick = { viewModel.delete(onDone) }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(
                onClick = { viewModel.save(onDone) },
                enabled = viewModel.name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            val picker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia(),
            ) { uri -> uri?.let(viewModel::setAvatar) }

            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box {
                    val avatarModifier = if (
                        partnerId != null &&
                        sharedTransitionScope != null &&
                        animatedVisibilityScope != null
                    ) {
                        with(sharedTransitionScope) {
                            partnerAvatarModifier(partnerId, animatedVisibilityScope)
                        }
                    } else {
                        Modifier
                    }
                    PartnerAvatar(
                        avatarPath = viewModel.avatarPath,
                        name = viewModel.name.ifBlank { "?" },
                        size = 96.dp,
                        modifier = avatarModifier,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.partner_avatar_change),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            FormTextField(
                label = stringResource(R.string.partner_name),
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
            )
            Spacer(Modifier.height(8.dp))

            FormSelectField(
                label = stringResource(R.string.partner_gender),
                value = viewModel.gender.label(),
                options = Gender.entries.map { it to it.label() },
                onSelect = viewModel::onGenderChange,
            )
            Spacer(Modifier.height(8.dp))

            FormSelectField(
                label = stringResource(R.string.partner_relationship),
                value = viewModel.relationship.label(),
                options = RelationshipType.entries.map { it to it.label() },
                onSelect = viewModel::onRelationshipChange,
            )
            Spacer(Modifier.height(8.dp))

            FormTextField(
                label = stringResource(R.string.partner_notes),
                value = viewModel.notes,
                onValueChange = viewModel::onNotesChange,
                singleLine = false,
                minLines = 3,
            )

            if (partnerId != null) {
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.partner_archive),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "不会再出现在新的选择中，所有记录都会保留。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = viewModel.isArchived,
                        onCheckedChange = { viewModel.toggleArchive(onDone) },
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    viewModel.duplicate?.let { duplicate ->
        val deleted = duplicate.deletedAt != null
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.duplicate_title)) },
            text = {
                Text(
                    stringResource(
                        when {
                            viewModel.isNew && deleted -> R.string.duplicate_partner_message_deleted
                            viewModel.isNew -> R.string.duplicate_partner_message
                            else -> R.string.duplicate_partner_message_edit
                        },
                        duplicate.name,
                    ),
                )
            },
            confirmButton = {
                when {
                    viewModel.isNew && deleted -> TextButton(
                        onClick = { viewModel.restoreDuplicate(onDone) },
                    ) {
                        Text(stringResource(R.string.duplicate_restore_existing))
                    }

                    viewModel.isNew -> TextButton(onClick = viewModel::saveAnyway) {
                        Text(stringResource(R.string.duplicate_create_anyway))
                    }

                    else -> TextButton(onClick = viewModel::saveAnyway) {
                        Text(stringResource(R.string.duplicate_save_anyway))
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::dismissDuplicate) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    if (viewModel.isNew) {
                        TextButton(onClick = viewModel::saveAnyway) {
                            Text(stringResource(R.string.duplicate_create_anyway))
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun PartnerAvatar(avatarPath: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarPath != null) {
            AsyncImage(
                model = File(context.filesDir, avatarPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.take(1).ifBlank { "?" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}